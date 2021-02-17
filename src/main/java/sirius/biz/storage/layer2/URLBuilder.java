/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.biz.storage.layer2.variants.ConversionEngine;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents a <b>builder</b> to generate delivery URLs for {@link Blob blobs} via the {@link BlobDispatcher}.
 */
public class URLBuilder {

    /**
     * Contains the variant name which delivers the actual contents of the blob without any conversion.
     */
    public static final String VARIANT_RAW = "raw";

    /**
     * Matches all non-url characters in the given <tt>addonText</tt> which will be replaced by "-" in
     * {@link #appendAddonText(StringBuilder)}.
     */
    private static final Pattern NON_URL_CHARACTERS = Pattern.compile("[^a-zA-Z0-9_.]");

    protected BlobStorageSpace space;
    protected Blob blob;
    protected String blobKey;
    protected String variant = VARIANT_RAW;
    protected String filename;
    protected String baseURL;
    protected String addonText;
    protected boolean eternallyValid;
    protected boolean reusable;
    protected boolean forceDownload;
    protected boolean suppressCache;
    protected String hook;
    protected String payload;

    @Part
    private static StorageUtils utils;

    @Part
    private static ConversionEngine conversionEngine;

    /**
     * Creates a new builder with a direct reference to the space and the blob key.
     *
     * @param space   the storage space which manages the blob
     * @param blobKey the blob key to download
     */
    public URLBuilder(BlobStorageSpace space, String blobKey) {
        this.space = space;
        this.blobKey = blobKey;
        this.blob = null;
    }

    /**
     * Creates a new builder with a direct reference to the blob.
     *
     * @param space the storage space which manages the blob
     * @param blob  the blob download
     */
    public URLBuilder(BlobStorageSpace space, Blob blob) {
        this.space = space;
        this.blobKey = blob.getBlobKey();
        this.blob = blob;
    }

    /**
     * Specifies the version of the file to use.
     * <p>
     * If a value (other than {@link #VARIANT_RAW}) is given, the selected variant is delivered. If the variant
     * does not exist yet, an appropriate URL is generated which will then generate and deliver the variant.
     * <p>
     * The type of variants and how these are created is specified in the system configuration and managed by the
     * {@link ConversionEngine}.
     *
     * @param variant the name of the variant to deliver
     * @return the builder itself for fluent method calls
     * @see ConversionEngine
     */
    public URLBuilder withVariant(String variant) {
        this.variant = variant;
        return this;
    }

    /**
     * Make the URL a downlod url using the given filename.
     *
     * @param filename the filename to send to the browser
     * @return the builder itself for fluent method calls
     */
    public URLBuilder asDownload(String filename) {
        this.filename = filename;
        this.forceDownload = true;

        return this;
    }

    /**
     * Make the URL a downlod url using the filename of the blob.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder asDownload() {
        this.forceDownload = true;

        return this;
    }

    /**
     * Specifies the base URL to use.
     *
     * @param baseURL the base URL to use
     * @return the builder itself for fluent method calls
     */
    public URLBuilder withBaseURL(String baseURL) {
        this.baseURL = baseURL;
        return this;
    }

    /**
     * Makes this URL eternally valid (authenticated).
     * <p>
     * Note that this implies that a {@link #reusable() reusable} URL is generated.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder eternallyValid() {
        this.eternallyValid = true;
        return this;
    }

    /**
     * Makes this URL reusable.
     * <p>
     * Such URLs use an virtual access path which are not as cachable as physical ones (which are infinitely cached).
     * On the other hand these URLs remain constant for the same blob where as physical URLs change once the underlying
     * blob is updated.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder reusable() {
        this.reusable = true;
        return this;
    }

    /**
     * Disables HTTP caching entirely.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder suppressCaching() {
        this.suppressCache = true;
        return this;
    }

    /**
     * Permits to add additional text to the URL which is ignored by the {@link BlobDispatcher}.
     * <p>
     * This can be used to add SEO texts for image URLs...
     *
     * @param text the text to include in the URL (will be escaped properly).
     * @return the builder itself for fluent method calls
     */
    public URLBuilder withAddonText(String text) {
        this.addonText = text;
        return this;
    }

    /**
     * Permits to trigger a {@link BlobDispatcherHook} once the blob was completely delivered.
     * <p>
     * This can be used to emit events etc.
     *
     * @param hook    the name of the hook to trigger
     * @param payload the paylog to send to the tirgger (e.g. a database id or the like)
     * @return the builder itself for fluent method calls
     */
    public URLBuilder withHook(String hook, @Nullable String payload) {
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
        if (Strings.isEmpty(blobKey) || (blob != null && Strings.isEmpty(blob.getPhysicalObjectKey()))) {
            return Optional.empty();
        }

        if (!eternallyValid && !suppressCache && !reusable && isPhysicalKeyReadilyAvailable()) {
            return Optional.ofNullable(createPhysicalDeliveryURL());
        } else {
            return Optional.ofNullable(createVirtualDeliveryURL());
        }
    }

    protected boolean isPhysicalKeyReadilyAvailable() {
        // If the raw file is requested and the blob object is available, we can easily determine the effective
        // physical key to serve.
        return Strings.areEqual(variant, URLBuilder.VARIANT_RAW) && blob != null;
    }

    private String createPhysicalDeliveryURL() {
        StringBuilder result = createBaseURL();

        String physicalKey = determinePhysicalKey();
        if (Strings.isEmpty(physicalKey)) {
            return createVirtualDeliveryURL();
        }

        result.append(BlobDispatcher.URI_PREFIX);
        result.append("/");
        result.append(BlobDispatcher.FLAG_PHYSICAL);
        if (forceDownload) {
            result.append(BlobDispatcher.FLAG_DOWNLOAD);
        }

        result.append("/");
        result.append(space.getName());
        result.append("/");
        result.append(computeAccessToken(physicalKey));
        result.append("/");
        result.append(blobKey);
        result.append("/");
        if (forceDownload) {
            result.append(physicalKey);
            result.append("/");
            appendAddonText(result);
            result.append(Strings.urlEncode(determineEffectiveFilename()));
        } else {
            appendAddonText(result);
            result.append(physicalKey);
            result.append(determineEffectiveFileExtension());
        }

        appendHook(result);

        return result.toString();
    }

    private String createVirtualDeliveryURL() {
        StringBuilder result = createBaseURL();
        result.append(BlobDispatcher.URI_PREFIX);
        result.append("/");
        if (!suppressCache) {
            result.append(BlobDispatcher.FLAG_CACHABLE);
        }
        result.append(BlobDispatcher.FLAG_VIRTUAL);
        if (forceDownload) {
            result.append(BlobDispatcher.FLAG_DOWNLOAD);
        }
        result.append("/");
        result.append(space.getName());
        result.append("/");
        result.append(computeAccessToken(blobKey + "-" + variant));
        result.append("/");
        result.append(variant);
        if (forceDownload) {
            result.append("/");
            result.append(blobKey);
            result.append("/");
            appendAddonText(result);
            result.append(Strings.urlEncode(determineEffectiveFilename()));
        } else {
            result.append("/");
            appendAddonText(result);
            result.append(blobKey);
            result.append(determineEffectiveFileExtension());
        }

        appendHook(result);

        return result.toString();
    }

    /**
     * Appends the given hook name and payload if present.
     * <p>
     * This will be picked up by the BlobDispatcher and will trigger the matching
     * BlobDispatcherHook...
     *
     * @param urlBuilder the builder to append the strings to
     */
    private void appendHook(StringBuilder urlBuilder) {
        if (Strings.isFilled(hook)) {
            urlBuilder.append("?hook=");
            urlBuilder.append(Strings.urlEncode(hook));
            if (Strings.isFilled(payload)) {
                urlBuilder.append("&payload=");
                urlBuilder.append(Strings.urlEncode(payload));
            }
        }
    }

    private StringBuilder createBaseURL() {
        StringBuilder result = new StringBuilder();
        if (Strings.isFilled(baseURL)) {
            result.append(baseURL);
        } else {
            ((BasicBlobStorageSpace<?, ?, ?>) space).getBaseURL().ifPresent(result::append);
        }

        return result;
    }

    private String determinePhysicalKey() {
        if (blob != null && Strings.areEqual(variant, URLBuilder.VARIANT_RAW)) {
            return blob.getPhysicalObjectKey();
        }

        Tuple<String, Boolean>
                result = ((BasicBlobStorageSpace<?, ?, ?>) space).resolvePhysicalKey(blobKey, variant, true);
        if (result != null) {
            return result.getFirst();
        } else {
            return null;
        }
    }

    private String computeAccessToken(String authToken) {
        if (eternallyValid) {
            return utils.computeEternallyValidHash(authToken);
        } else {
            return utils.computeHash(authToken, 0);
        }
    }

    private void appendAddonText(StringBuilder result) {
        if (Strings.isFilled(addonText)) {
            result.append(Strings.reduceCharacters(NON_URL_CHARACTERS.matcher(addonText).replaceAll("-")));
            result.append("--");
        }
    }

    private String determineEffectiveFilename() {
        if (Strings.isFilled(filename)) {
            return filename;
        }
        if (blob != null) {
            return blob.getFilename();
        }

        return space.resolveFilename(blobKey).orElse(blobKey);
    }

    private String determineEffectiveFileExtension() {
        String result = Value.of(variant)
                             .ignore(VARIANT_RAW)
                             .asOptionalString()
                             .map(conversionEngine::determineTargetFileExension)
                             .orElseGet(() -> Files.getFileExtension(determineEffectiveFilename()));

        if (Strings.isFilled(result)) {
            return "." + Strings.urlEncode(result);
        } else {
            return "";
        }
    }
}
