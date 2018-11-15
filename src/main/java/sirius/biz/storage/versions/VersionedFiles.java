package sirius.biz.storage.versions;

import sirius.biz.storage.Storage;
import sirius.biz.storage.StoredObject;
import sirius.biz.tenants.Tenant;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Small helper class to access and create {@link VersionedFile versioned files}.
 */
@Register(classes = VersionedFiles.class, framework = Storage.FRAMEWORK_STORAGE)
public class VersionedFiles {

    @Part
    private static OMA oma;

    @Part
    private static Storage storage;

    /**
     * Name of the used bucked.
     */
    public static final String VERSIONED_FILES = "versioned-files";

    @ConfigValue("storage.buckets.versioned-files.maxNumberOfVersions")
    private int maxNumberOfVersions = 0;

    /**
     * Retrieves all versions of a versioned file.
     *
     * @param tenant           the owning tenant
     * @param uniqueIdentifier the identifier of the versioned file
     * @return {@link List<VersionedFile>} list of versioned files (sorted: most current first)
     */
    public List<VersionedFile> getVersions(Tenant tenant, String uniqueIdentifier) {
        return oma.select(VersionedFile.class)
                  .eq(VersionedFile.TENANT, tenant)
                  .eq(VersionedFile.UNIQUE_IDENTIFIER, uniqueIdentifier)
                  .orderDesc(VersionedFile.TIMESTAMP)
                  .queryList();
    }

    /**
     * Checks if there are any versions for the given path.
     *
     * @param tenant           the owning tenant
     * @param uniqueIdentifier the identifier of the versioned file
     * @return boolean <tt>true</tt> if there are any versions, <tt>false</tt> otherwise
     */
    public boolean hasVersions(Tenant tenant, String uniqueIdentifier) {
        return oma.select(VersionedFile.class)
                  .eq(VersionedFile.TENANT, tenant)
                  .eq(VersionedFile.UNIQUE_IDENTIFIER, uniqueIdentifier)
                  .exists();
    }

    /**
     * Deletes all versions of a versioned file.
     *
     * @param tenant           the owning tenant
     * @param uniqueIdentifier the identifier of the versioned file
     */
    public void deleteVersions(Tenant tenant, String uniqueIdentifier) {
        oma.select(VersionedFile.class)
           .eq(VersionedFile.TENANT, tenant)
           .eq(VersionedFile.UNIQUE_IDENTIFIER, uniqueIdentifier)
           .delete();
    }

    /**
     * Retrieves the {@link VersionedFile} for the given id.
     *
     * @param tenant    the owning tenant
     * @param versionId the database id of the versioned file entity
     * @return the found {@link VersionedFile}
     */
    public VersionedFile getFile(Tenant tenant, String versionId) {
        return oma.select(VersionedFile.class)
                  .eq(VersionedFile.ID, versionId)
                  .eq(VersionedFile.TENANT, tenant)
                  .first()
                  .orElseThrow(() -> Exceptions.createHandled().withNLSKey("VersionedFiles.noVersion").handle());
    }

    /**
     * Gets the content of the {@link VersionedFile}.
     *
     * @param file the {@link VersionedFile} holding the content
     * @return {@link List<String>} each string holding one line of the file content
     */
    public List<String> getContent(VersionedFile file) {
        try (InputStream data = storage.getData(file.getStoredFile().getObject())) {
            return new BufferedReader(new InputStreamReader(data)).lines().collect(Collectors.toList());
        } catch (IOException e) {
            throw Exceptions.handle(Storage.LOG, e);
        }
    }

    /**
     * Creates a new {@link VersionedFile}.
     *
     * @param tenant           the owning tenant
     * @param uniqueIdentifier the identifier of the versioned file
     * @param content          the content of the current version
     * @param comment          the comment explaining the changes of the current version
     * @return {@link VersionedFile} the versioned file
     */
    public VersionedFile createVersion(Tenant tenant, String uniqueIdentifier, String content, String comment) {
        VersionedFile file = new VersionedFile();
        file.setComment(comment);
        file.setUniqueIdentifier(uniqueIdentifier);
        file.setTimestamp(LocalDateTime.now());
        file.getTenant().setValue(tenant);
        file.getStoredFile().setObject(generateNewFile(file, uniqueIdentifier, content));

        oma.update(file);

        deleteOldVersions(tenant, uniqueIdentifier);

        return file;
    }

    /**
     * Generates a new {@link StoredObject} holding the code to be versioned.
     * <p>
     * Will check if the path already exists to avoid overwriting existing versions.
     *
     * @param file             the {@link VersionedFile} associated with the versioned code
     * @param uniqueIdentifier the identifier of the versioned file
     * @param code             the code to be versioned
     * @return {@link StoredObject} the generated file
     */
    private StoredObject generateNewFile(VersionedFile file, String uniqueIdentifier, String code) {
        String fullPath = uniqueIdentifier + file.getTimestamp();

        if (storage.findByPath(file.getTenant().getValue(), VERSIONED_FILES, fullPath).isPresent()) {
            throw Exceptions.createHandled()
                            .withNLSKey("VersionedFiles.versionExistsConflict")
                            .set("date", NLS.toUserString(file.getTimestamp()))
                            .set("path", uniqueIdentifier)
                            .handle();
        }

        StoredObject object = storage.createTemporaryObject(file.getTenant().getValue(),
                                                            VERSIONED_FILES,
                                                            file.getStoredFile().getReference(),
                                                            fullPath);

        try (OutputStream out = storage.updateFile(object)) {
            out.write(code.getBytes());
        } catch (IOException e) {
            throw Exceptions.handle(Storage.LOG, e);
        }

        return object;
    }

    /**
     * Deletes old versions of a {@link VersionedFile} identified by an unique identifier.
     *
     * @param tenant           the owning tenant
     * @param uniqueIdentifier the unique identifier of a versioned file
     */
    public void deleteOldVersions(Tenant tenant, String uniqueIdentifier) {
        if (maxNumberOfVersions == 0) {
            return;
        }

        AtomicInteger filesToSkip = new AtomicInteger(maxNumberOfVersions);

        oma.select(VersionedFile.class)
           .eq(VersionedFile.TENANT, tenant)
           .eq(VersionedFile.UNIQUE_IDENTIFIER, uniqueIdentifier)
           .orderDesc(VersionedFile.TIMESTAMP)
           .iterateAll(file -> {
               if (filesToSkip.getAndDecrement() > 0) {
                   return;
               }

               if (file.getStoredFile().isFilled()) {
                   storage.delete(file.getStoredFile().getObject());
               }

               oma.delete(file);
           });
    }
}
