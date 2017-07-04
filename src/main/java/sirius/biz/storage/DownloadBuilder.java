/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;

import java.util.Map;

/**
 * Represents a <b>builder</b> for download URLs for {@link StoredObject stored objects}.
 */
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
     * Builds the effective URL according to the parameters specified.
     *
     * @return the url generated by this builder
     */
    public String buildURL() {
        determinePhysicalKey();

        return storage.createURL(this);
    }

    private void determinePhysicalKey() {
        if (tryDirectFetch()) {
            return;
        }

        Tuple<VirtualObject, Map<String, String>> physicalObjects = versionManager.fetchPhysicalObjects(this);
        fillFromObject(physicalObjects.getFirst());

        applyVersion(physicalObjects);
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
}
