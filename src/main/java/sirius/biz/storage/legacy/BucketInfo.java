/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.biz.tenants.Tenant;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;
import sirius.web.security.UserContext;

/**
 * Represents metadata available for a bucket.
 * <p>
 * Most of this is taken from <tt>storage.buckets.[bucketName]</tt> from the system config.
 *
 * @deprecated use the new BlobStorageSpace API
 */
@Deprecated
public class BucketInfo {

    private final String name;
    private final String description;
    private final String permission;
    private final boolean canCreate;
    private final boolean canEdit;
    private final boolean canDelete;
    private final boolean alwaysUseLikeSearch;
    private final boolean showPublicURLs;
    private final int deleteFilesAfterDays;
    private final PhysicalStorageEngine engine;
    private final boolean logAsDeprecated;

    @Part
    private static OMA oma;

    @Part
    private static GlobalContext context;

    /**
     * Creates a new bucket info based on the given config section.
     *
     * @param extension the config section holding the bucket infos
     */
    protected BucketInfo(Extension extension) {
        this.name = extension.getId();
        this.description = NLS.getIfExists("Storage.bucket." + this.name, null).orElse("");
        this.permission = extension.get("permission").asString();
        this.canCreate = extension.get("canCreate").asBoolean();
        this.canEdit = extension.get("canEdit").asBoolean();
        this.canDelete = extension.get("canDelete").asBoolean();
        this.alwaysUseLikeSearch = extension.get("alwaysUseLikeSearch").asBoolean();
        this.showPublicURLs = extension.get("showPublicURLs").asBoolean();
        this.deleteFilesAfterDays = extension.get("deleteFilesAfterDays").asInt(0);
        this.engine = context.findPart(extension.get("engine").asString(), PhysicalStorageEngine.class);
        this.logAsDeprecated = extension.get("logAsDeprecated").asBoolean();
    }

    /**
     * Counts the total number of objects in this bucket.
     *
     * @return the total number of objects in this bucket
     */
    public long getNumberOfObjects() {
        return oma.select(VirtualObject.class)
                  .eq(VirtualObject.TENANT, UserContext.getCurrentUser().as(Tenant.class))
                  .eq(VirtualObject.BUCKET, name)
                  .count();
    }

    /**
     * Returns the (technical) name of the bucket.
     *
     * @return the name of the bucket
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the description of the bucket.
     *
     * @return the description of the bucket
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the permission required to access the contents of this bucket.
     *
     * @return the permission required to access this bucket or <tt>null</tt> if no special rights are needed
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Determines if a user can create new objects in this bucket.
     *
     * @return <tt>true</tt> if a user can create net objects, fale<tt>false</tt> otherwise
     */
    public boolean isCanCreate() {
        return canCreate;
    }

    /**
     * Determines if a user can edit objects within this bucket.
     *
     * @return <tt>true</tt> if a user can modify an object within this bucket, <tt>false</tt> otherwise
     */
    public boolean isCanEdit() {
        return canEdit;
    }

    /**
     * Determines if a user can delete objects within this bucket.
     *
     * @return <tt>true</tt> if a user can delete an object within this bucket, <tt>false</tt> otherwise
     */
    public boolean isCanDelete() {
        return canDelete;
    }

    /**
     * Determines if searching in a bucket always uses a like on search.
     *
     * @return <tt>true</tt> if a like on search is to be used, <tt>false</tt> otherwise
     */
    public boolean isAlwaysUseLikeSearch() {
        return alwaysUseLikeSearch;
    }

    /**
     * Determines if publicly accessible URLs should be displayed in the backend.
     *
     * @return <tt>true</tt> if public URLs should be shown, <tt>false</tt> otherwise
     */
    public boolean isShowPublicURLs() {
        return showPublicURLs;
    }

    /**
     * Determines the number of days after which objects are automatically deleted.
     * <p>
     * This is used for "self organizing" buckets, which automatically drain old data.
     *
     * @return the number of days after which objects are deleted or <tt>0</tt> to disable auto-deletion
     */
    public int getDeleteFilesAfterDays() {
        return deleteFilesAfterDays;
    }

    /**
     * Determines the {@link PhysicalStorageEngine} used to store objects within this bucket.
     *
     * @return the storage engine used by this bucket
     */
    public PhysicalStorageEngine getEngine() {
        return engine;
    }

    /**
     * Determines if uses of the bucket should be logged into the deprecated log if it's log level is FINE.
     *
     * @return <tt>true</tt> if uses should be logged, <tt>false</tt> otherwise
     */
    public boolean shouldLogAsDeprecated() {
        return logAsDeprecated;
    }
}
