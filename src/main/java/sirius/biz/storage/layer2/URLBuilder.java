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

    /**
     * Contains the default fallback URI used by {@link #buildImageURL()}.
     * <p>
     * Note that a custom URI can be provided via {@link #withFallbackUri(String)}
     * or {@link BlobHardRef#withFallbackUri(String)}.
     */
    public static final String IMAGE_FALLBACK_URI = "/assets/images/blob_image_fallback.png";

    protected BlobStorageSpace space;
    protected Blob blob;
    protected String blobKey;
    protected String variant = VARIANT_RAW;
    protected String cachedPhysicalKey;
    protected String filename;
    protected String baseURL;
    protected String addonText;
    protected boolean eternallyValid;
    protected boolean reusable;
    protected boolean delayResolve;
    protected boolean forceDownload;
    protected boolean suppressCache;
    protected String hook;
    protected String payload;
    protected String fallbackUri;
    protected boolean largeFile;

    @Part
    private static StorageUtils utils;

    @Part
    private static ConversionEngine conversionEngine;

    @ConfigValue("storage.layer2.largeFileLimit")
    private static long largeFileLimit;

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
     * Specifies a fallback URI to deliver if no blob or blob-key is available.
     *
     * @param fallbackUri the fallback URI to use
     * @return the builder itself for fluent method calls
     * @see #IMAGE_FALLBACK_URI
     * @see #safeBuildURL(String)
     * @see #buildImageURL()
     */
    public URLBuilder withFallbackUri(String fallbackUri) {
        this.fallbackUri = fallbackUri;
        return this;
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
     * Make the URL a download url using the given filename.
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
     * Make the URL a download url using the filename of the blob.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder asDownload() {
        this.forceDownload = true;

        return this;
    }

    /**
     * Enables a check which determines if the underlying file is considered "too large to be cached".
     * <p>
     * Downstream reverse proxies like Varnish are heavily impact by very large files (GBs). Therefore, this
     * check can be enabled. If we then detect that the underlying file is larger than {@link #largeFileLimit}
     * (specified in the config by <tt>storage.layer2.largeFileLimit</tt>), we add a special prefix to the
     * URL (<tt>/dasd/xxl</tt>). This prefix is cut away by the {@link BlobDispatcher} and doesn't change the
     * processing at all. However, a downstream reverse proxy can detect such links and by-pass caching etc.
     * entirely.
     * <p>
     * This has to be enabled manually, as such large downloads are infrequent and require a lookup for the
     * filesize.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder enableLargeFileDetection() {
        if (Strings.isFilled(blobKey) && blob == null) {
            space.findByBlobKey(blobKey).ifPresent(resolvedBlob -> this.blob = resolvedBlob);
        }
        if (blob != null && blob.getSize() > largeFileLimit) {
            this.largeFile = true;
        }

        return this;
    }

    /**
     * Specifies the base URL to use.
     * <p>
     * Note that the system tries to provide a proper base URL based on the <b>storage space</b> in which
     * the blob resides. Therefore, this method only needs to be invoked if an external / exotic base URL is to be used.
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
     * Such URLs use a virtual access path which are not as cacheable as physical ones (which are infinitely cached).
     * On the other hand these URLs remain constant for the same blob whereas physical URLs change once the underlying
     * blob is updated. Therefore, these URLs can be passed on to 3rd parties as they remain valid as long as the
     * referenced blob "lives".
     * <p>
     * Note that the authentication of this URL is still limited unless {@link #eternallyValid()} is invoked.
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder reusable() {
        this.reusable = true;
        return this;
    }

    /**
     * Instructs the system to use the virtual blob URL instead of resolving the physical URL.
     * <p>
     * This might be feasible, if the caller knows, that there is a great chance, that the URL being generated is
     * not actually invoked. We therefore can generate a virtual URL (which requires no additional lookup, but is
     * not as "cacheable" as a physical one).
     *
     * @return the builder itself for fluent method calls
     */
    public URLBuilder delayResolved() {
        this.delayResolve = true;
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
     * Permits adding additional text to the URL which is ignored by the {@link BlobDispatcher}.
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
     * @param payload the payload to send to the trigger (e.g. a database id or the like)
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
            if (Strings.isFilled(fallbackUri)) {
                return Optional.of(createBaseURL().append(fallbackUri).toString());
            }
            return Optional.empty();
        }

        if (eternallyValid) {
            // If a URL is eternally valid, there is no point of generating a physical URL, as this will be outdated
            // as soon as the underlying blob contents change and not "live as long as the blob itself"...
            return Optional.of(createVirtualDeliveryUrl());
        }
        if (reusable) {
            // If the caller requested a reusable URL (one to be output and sent to 3rd parties, we probably also
            // want it to be valid as long as the "blob lives" and not to become obsolete once the blob contents
            // change...
            return Optional.of(createVirtualDeliveryUrl());
        }
        if (suppressCache) {
            // Manual cache control is only supported in virtual calls, not physical...
            return Optional.of(createVirtualDeliveryUrl());
        }
        if (delayResolve && !isPhysicalKeyReadilyAvailable()) {
            // The caller specifically requested, that we do not forcefully compute the physical URL (which might
            // require a lookup, but to rather use the virtual URL...
            return Optional.of(createVirtualDeliveryUrl());
        }

        return Optional.of(createPhysicalDeliveryUrl());
    }

    /**
     * Builds the URL and permits to specify a custom fallback URI to deliver if no blob or blob-key is available.
     *
     * @param fallbackUri the fallback URI to use
     * @return the effective URL to use
     * @see #IMAGE_FALLBACK_URI
     * @see #withFallbackUri(String)
     * @see #buildImageURL()
     */
    public String safeBuildURL(String fallbackUri) {
        return buildURL().orElseGet(() -> createBaseURL().append(fallbackUri).toString());
    }

    /**
     * Builds the URL based on the given parameters or uses a generic fallback image if no blob or blob-key is
     * available.
     *
     * @return the effective URL to use
     * @see #IMAGE_FALLBACK_URI
     * @see #withFallbackUri(String)
     * @see #safeBuildURL(String)
     */
    public String buildImageURL() {
        return safeBuildURL(IMAGE_FALLBACK_URI);
    }


    /**
     * Obtains the fallback URI if present.
     *
     * @return the fallback URI or an empty optional if no fallback URI is present
     */
    public Optional<String> getFallbackUri() {
        return Optional.ofNullable(fallbackUri);
    }

    protected boolean isPhysicalKeyReadilyAvailable() {
        // If the raw file is requested and the blob object is available, we can easily determine the effective
        // physical key to serve.
        return Strings.areEqual(variant, VARIANT_RAW) && blob != null;
    }

    private String createPhysicalDeliveryUrl() {
        StringBuilder result = createBaseURL();

        String physicalKey = determinePhysicalKey();
        if (Strings.isEmpty(physicalKey)) {
            return createVirtualDeliveryUrl();
        }

        result.append(BlobDispatcher.URI_PREFIX);
        result.append("/");
        if (largeFile) {
            result.append(BlobDispatcher.LARGE_FILE_MARKER);
        }
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

    private String createVirtualDeliveryUrl() {
        StringBuilder result = createBaseURL();
        result.append(BlobDispatcher.URI_PREFIX);
        result.append("/");
        if (largeFile) {
            result.append(BlobDispatcher.LARGE_FILE_MARKER);
        }
        if (!suppressCache) {
            result.append(BlobDispatcher.FLAG_CACHEABLE);
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

        Tuple<String, Boolean> result =
                ((BasicBlobStorageSpace<?, ?, ?>) space).resolvePhysicalKey(blobKey, variant, true);
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
