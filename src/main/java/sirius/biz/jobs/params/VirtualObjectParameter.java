package sirius.biz.jobs.params;

import sirius.biz.storage.Storage;
import sirius.biz.storage.StoredObject;
import sirius.biz.storage.VirtualObject;
import sirius.biz.tenants.jdbc.SQLTenant;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Provides a base class to implement autocomplete parameters for {@link VirtualObject entities}.
 */
public class VirtualObjectParameter extends EntityParameter<VirtualObject, VirtualObjectParameter> {

    @Part
    private static Storage storage;

    private static final String DEFAULT_BUCKET = "work";

    /**
     * Provides a default file path to select for the parameter instance.
     * <p>
     * If no value for this parameter is selected, the default file path will be used.
     */
    private String defaultFilePath = "";

    /**
     * The bucket to find or store the selected {@link VirtualObject}.
     */
    private String bucketName = DEFAULT_BUCKET;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public VirtualObjectParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name the name of the parameter
     */
    public VirtualObjectParameter(String name) {
        super(name);
    }

    @Override
    public String getAutocompleteUri() {
        return "/storage/autocomplete/" + getBucketName();
    }

    @Override
    protected Class<VirtualObject> getType() {
        return VirtualObject.class;
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return findFile(input.asString()).map(StoredObject::getObjectKey).orElse("");
    }

    @Override
    protected Optional<VirtualObject> resolveFromString(Value input) {
        return findFile(input.asString());
    }

    /**
     * Finds a {@link VirtualObject} for the given object key.
     * <p>
     * If no {@link VirtualObject} was found but a defaultFilePath was given,
     * a second look up is executed for the default file path.
     *
     * @param key the wanted object key
     * @return {@link Optional<VirtualObject>} the found virtual object or {@link Optional#empty()}
     */
    private Optional<VirtualObject> findFile(String key) {
        Optional<StoredObject> file = storage.findByKey((SQLTenant)tenants.getRequiredTenant(), getBucketName(), key);

        if (!file.isPresent() && Strings.isFilled(defaultFilePath)) {
            file = storage.findByPath((SQLTenant)tenants.getRequiredTenant(), getBucketName(), defaultFilePath);
        }

        return file.map(storedObject -> {
            if (storedObject instanceof VirtualObject) {
                return (VirtualObject) storedObject;
            }

            return null;
        });
    }

    /**
     * Setter for the default file path.
     *
     * @param path the default file path of an virtual object
     * @return the instance itself for fluent method calls
     */
    public VirtualObjectParameter withDefaultName(String path) {
        this.defaultFilePath = path;
        return this;
    }

    /**
     * Getter of the default file path
     *
     * @return the set default file path
     */
    public String getDefaultFilePath() {
        return defaultFilePath;
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/virtual-object-autocomplete.html.pasta";
    }

    /**
     * Retrieves the bucketName name to be used.
     *
     * @return the bucketName name to be used
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * Setter for the bucket name to be used.
     *
     * @param bucketName the name of the bucket to be used for this parameter
     * @return the instance itself for fluent method calls
     */
    public VirtualObjectParameter withBucketName(String bucketName) {
        this.bucketName = bucketName;
        return this;
    }
}
