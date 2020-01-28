/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.storage.layer2.Blob;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.biz.storage.layer2.BlobStorageSpace;
import sirius.biz.storage.layer3.EnumerateOnlyProvider;
import sirius.biz.storage.layer3.FileParameter;
import sirius.biz.storage.layer3.FileSearch;
import sirius.biz.storage.layer3.FindOnlyProvider;
import sirius.biz.storage.layer3.MutableVirtualFile;
import sirius.biz.storage.layer3.SingularVFSRoot;
import sirius.biz.storage.layer3.TmpRoot;
import sirius.biz.storage.layer3.VFSRoot;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.OutputStream;

/**
 * Provides a {@link VFSRoot} to trigger jobs via the built-in {@link VirtualFileSystem}.
 * <p>
 * This will provide a <b>jobs</b> folder (if {@link JobPresets} are enabled and the current user is allowed to
 * execute jobs). Within this directory, there is a sub-directory for each job which accepts a file. Each job folder
 * contains on folder per preset (as any other parameter than the file has to be specified elsewhere).
 * <p>
 * If a file is uploaded into a preset folder it is put into the {@link TmpRoot temporary space} and a job
 * execution is scheduled. Using the temporary space permits to upload different files with the same name at
 * the same time without any conflicts - this wouldn't be true when we'd use the <tt>work</tt> folder - which is
 * normally the preferred folder to upload the input data for jobs.
 */
@Register(classes = VFSRoot.class, framework = Jobs.FRAMEWORK_JOBS)
public class JobsRoot extends SingularVFSRoot {

    /**
     * Contains the name of the virtual folder which contains all "file jobs" as sub-folders.
     */
    public static final String PATH_JOBS = "jobs";

    @Part
    private JobPresets presets;

    @Part
    private BlobStorage blobStorage;

    @Part
    private Tenants<?, ?, ?> tenants;

    @Part
    private Jobs jobs;

    @Part
    private VirtualFileSystem virtualFileSystem;

    @Override
    protected String getName() {
        return PATH_JOBS;
    }

    @Nullable
    @Override
    protected String getDescription() {
        return NLS.get("JobsRoot.description");
    }

    @Override
    protected boolean isEnabled() {
        if (presets == null || jobs == null) {
            return false;
        }

        return UserContext.getCurrentUser().hasPermission(Jobs.PERMISSION_EXECUTE_JOBS);
    }

    @Override
    protected void populateRoot(MutableVirtualFile rootDirectory) {
        rootDirectory.withChildren(new EnumerateOnlyProvider(this::listFileJobs));
    }

    private void listFileJobs(VirtualFile jobsDirectory, FileSearch fileSearch) {
        jobs.getAvailableJobs(null)
            .filter(JobFactory::canStartInBackground)
            .filter(this::isFileJob)
            .forEach(fileJobFactory -> {
                MutableVirtualFile jobDirectory = MutableVirtualFile.checkedCreate(jobsDirectory, fileJobFactory.getName());
                jobDirectory.markAsExistingDirectory();
                jobDirectory.withChildren(new EnumerateOnlyProvider(this::listPresets));
                jobDirectory.attach(JobFactory.class, fileJobFactory);
                jobDirectory.withDescription(fileJobFactory.getDescription());
                fileSearch.processResult(jobDirectory);
            });
    }

    private boolean isFileJob(JobFactory factory) {
        return factory.getParameters().stream().filter(parameter -> parameter instanceof FileParameter).count() == 1;
    }

    private void listPresets(VirtualFile parent, FileSearch fileSearch) {
        JobFactory fileJobFactory = parent.as(JobFactory.class);
        presets.fetchPresets(fileJobFactory).forEach(preset -> {
            MutableVirtualFile presetDirectory =
                    MutableVirtualFile.checkedCreate(parent, preset.getJobConfigData().getLabel());
            presetDirectory.markAsExistingDirectory();
            presetDirectory.withChildren(new FindOnlyProvider(this::unwrapPreset));
            presetDirectory.attach(JobPreset.class, preset);
            fileSearch.processResult(presetDirectory);
        });
    }

    @Nonnull
    private VirtualFile unwrapPreset(VirtualFile parent, String name) {
        JobPreset preset = parent.as(JobPreset.class);
        MutableVirtualFile result = MutableVirtualFile.checkedCreate(parent, name);
        result.withOutputStreamSupplier(uploadFile -> uploadAndTrigger(preset, uploadFile.name()));

        return result;
    }

    private OutputStream uploadAndTrigger(JobPreset preset, String filename) {
        try {
            BlobStorageSpace temporaryStorageSpace = blobStorage.getSpace(TmpRoot.TMP_SPACE);
            Blob buffer = temporaryStorageSpace.createTemporaryBlob(UserContext.getCurrentUser().getTenantId());
            return buffer.createOutputStream(() -> {
                temporaryStorageSpace.markAsUsed(buffer);
                trigger(preset, buffer, filename);
            }, filename);
        } catch (Exception e) {
            throw Exceptions.handle(e);
        }
    }

    private void trigger(JobPreset preset, Blob buffer, String filename) {
        String parameterName = findFileParemter(preset);

        preset.getJobConfigData().getJobFactory().startInBackground(param -> {
            if (Strings.areEqual(param, parameterName)) {
                return Value.of(virtualFileSystem.makePath(TmpRoot.TMP_PATH, buffer.getBlobKey(), filename));
            } else {
                return Value.of(preset.getJobConfigData().getConfigMap().get(param));
            }
        });
    }

    private String findFileParemter(JobPreset preset) {
        return preset.getJobConfigData()
                     .getJobFactory()
                     .getParameters()
                     .stream()
                     .filter(p -> p instanceof FileParameter)
                     .map(Parameter::getName)
                     .findFirst()
                     .orElse(null);
    }
}
