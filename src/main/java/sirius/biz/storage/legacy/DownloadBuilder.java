/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a <b>builder</b> for download URLs for {@link StoredObject stored objects}.
 */
@Deprecated
public class DownloadBuilder {

    private final Storage storage;
    private final VirtualObject object;
    private final String objectKey;
    private final String bucket;
    private String fileExtension;
    private String filename;
    private String baseURL;
    private String version;
    private String physicalKey;
    private boolean eternallyValid;
    private String addonText;
    private String hook;
    private String payload;

    @Part
    private static VersionManager versionManager;

    /**
     * Creates a new builder with a direct reference to the storage and the object key.
     *
     * @param storage   the storage implementation
     * @param bucket    the bucket in which the object is placed
     * @param objectKey the object key to download
     */
    protected DownloadBuilder(Storage storage, String bucket, String objectKey) {
        this.storage = storage;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.object = null;
    }

    /**
     * Creates a new builder with a direct reference to the storage and the object.
     *
     * @param storage the storage implementation
     * @param object  the object key download
     */
    protected DownloadBuilder(Storage storage, VirtualObject object) {
        this.storage = storage;
        this.bucket = object.getBucket();
        this.objectKey = object.getObjectKey();
        this.object = object;
    }

    /**
     * Specifies the file extension to append to the URL.
     *
     * @param fileExtension the extension to append
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder withFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
        return this;
    }

    /**
     * Specifies the version of the file to use.
     * <p>
     * The syntax is a comma separated string of key-value pairs in the form of {@code key:value}. The available keys
     * are {@code size} for the requested maximum size, {@code min} for the minimum size and {@code imageFormat} for
     * the imageFormat. The value for the size key has to be in the format {@code <width>x<height>}.
     * <p>
     * If no external command line command is setup for the image conversion only "png" and "jpg" are supported as
     * formats. The Default version which is used is the jpg format.
     * <p>
     * Example: The version {@code size:500x500,min:100x100,imageFormat:png} would scale an image with 1000x800 pixels
     * to 500x400. An image with 1000x150 would be scaled to 500x75 and then extended with a white border to 500x100. An
     * image with 400x50 pixels would not be scaled but extended to 400x100. All files would be in png format.
     *
     * @param version the name of the version to use
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder withVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * Make the URL a downlod url using the given filename.
     *
     * @param filename the filename to send to the browser
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder asDownload(String filename) {
        this.filename = filename;
        if (Strings.isFilled(filename) && Strings.isEmpty(fileExtension)) {
            this.fileExtension = Files.getFileExtension(filename);
        }

        return this;
    }

    /**
     * Specifies the base URL to use.
     *
     * @param baseURL the base URL to use
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder withBaseURL(String baseURL) {
        this.baseURL = baseURL;
        return this;
    }

    /**
     * Makes this URL eternally valid (authenticated).
     *
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder eternallyValid() {
        this.eternallyValid = true;
        return this;
    }

    /**
     * Permits to add additional text to the URL which is ignored by the storage layer.
     * <p>
     * This might be used to add SEO texts for image URLs...
     *
     * @param text the text to include in the URL (will be escaped properly).
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder withAddonText(String text) {
        this.addonText = text;
        return this;
    }

    /**
     * Permits to add additional text to the URL which is ignored by the storage layer.
     * <p>
     * This might be used to add SEO texts for image URLs...
     *
     * @param text the text to include in the URL (will be escaped properly).
     * @return the builder itself for fluent method calls
     */
    public DownloadBuilder withHook(String hook, @Nullable String payload) {
        this.hook = hook;
        this.payload = payload;
        return this;
    }

    /**
     * Builds the effective URL according to the parameters specified wrapped in an {@link Optional}.
     * <p>
     * {@link Optional#empty()} is returned when the file cannot be found in the storage engine.
     *
     * @return the url generated by this builder wrapped in an {@link Optional} or {@link Optional#empty()}
     * if no file was found in the storage engine
     */
    public Optional<String> buildURL() {
        if (!determinePhysicalKey()) {
            return Optional.empty();
        }

        return Optional.ofNullable(storage.createURL(this));
    }

    private boolean determinePhysicalKey() {
        if (tryDirectFetch()) {
            return true;
        }

        Tuple<VirtualObject, Map<String, String>> physicalObjects = versionManager.fetchPhysicalObjects(this);

        if (physicalObjects == null) {
            return false;
        }

        fillFromObject(physicalObjects.getFirst());

        applyVersion(physicalObjects);

        return true;
    }

    private void fillFromObject(VirtualObject object) {
        this.physicalKey = object.getPhysicalKey();
        if (Strings.isEmpty(this.fileExtension)) {
            this.fileExtension = Files.getFileExtension(object.getPath());
        }
    }

    private boolean tryDirectFetch() {
        if (object != null && Strings.isEmpty(version)) {
            fillFromObject(object);
            return true;
        }

        return false;
    }

    private void applyVersion(Tuple<VirtualObject, Map<String, String>> physicalObjects) {
        if (Strings.isEmpty(version)) {
            return;
        }

        String versionKey = versionManager.fetchVersion(physicalObjects, version);
        if (Strings.isFilled(versionKey)) {
            this.physicalKey = versionKey;
        }
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getFilename() {
        return filename;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public String getPhysicalKey() {
        return physicalKey;
    }

    public String getBucket() {
        return bucket;
    }

    public boolean isEternallyValid() {
        return eternallyValid;
    }

    public String getAddonText() {
        return addonText;
    }
}
