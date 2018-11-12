package sirius.biz.storage.versions;

import sirius.biz.storage.Storage;
import sirius.biz.storage.StoredObjectRef;
import sirius.biz.tenants.SQLTenantAware;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Trim;
import sirius.kernel.di.std.Register;

import java.time.LocalDateTime;

/**
 * Entity holding meta information about a versioned file.
 */
@Register(framework = Storage.FRAMEWORK_STORAGE)
public class VersionedFile extends SQLTenantAware {

    /**
     * Path of the versioned file.
     * <p>
     * This string is used to identify the file which is versioned.
     */
    public static final Mapping UNIQUE_IDENTIFIER = Mapping.named("uniqueIdentifier");
    @Length(255)
    @Trim
    private String uniqueIdentifier;

    /**
     * The comment explaining what was changed with this change.
     */
    public static final Mapping COMMENT = Mapping.named("comment");
    @NullAllowed
    @Lob
    @Trim
    private String comment;

    /**
     * The timestamp marking the version of the file.
     */
    public static final Mapping TIMESTAMP = Mapping.named("timestamp");
    private LocalDateTime timestamp;

    /**
     * The file holding the versioned code.
     */
    public static final Mapping STORED_FILE = Mapping.named("storedFile");
    @NullAllowed
    private final StoredObjectRef storedFile = new StoredObjectRef(VersionedFiles.VERSIONED_FILES, false);

    /**
     * Contains meta information about this versioned file.
     * <p>
     * We can store for e.g. if this file was automatically created or if this is the default version of a template.
     */
    public static final Mapping ADDITIONAL_INFORMATION = Mapping.named("additionalInformation");
    @Length(255)
    @NullAllowed
    @Trim
    private String additionalInformation;

    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    public void setUniqueIdentifier(String uniqueIdentifier) {
        this.uniqueIdentifier = uniqueIdentifier;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public StoredObjectRef getStoredFile() {
        return storedFile;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }
}
