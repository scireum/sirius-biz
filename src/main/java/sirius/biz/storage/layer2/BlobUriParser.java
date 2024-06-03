/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Parses blob URIs as created by {@link URLBuilder}.
 */
public class BlobUriParser {

    /**
     * Contains the prefix length ("/dasd/") to cut from an incoming URI
     */
    private static final int URI_PREFIX_LENGTH = BlobDispatcher.URI_PREFIX_TRAILED.length();

    /**
     * Contains the prefix length ("xxl" + "/") to cut from an incoming URI
     */
    private static final int LARGE_FILE_MARKER_LENGTH = BlobDispatcher.LARGE_FILE_MARKER.length();

    private static final int TYPE = 0;
    private static final int STORAGE_SPACE = 1;
    private static final int ACCESS_TOKEN = 2;

    private static final int PHYSICAL_URI_BLOB_KEY = 3;
    private static final int VIRTUAL_URI_VARIANT = 3;
    private static final int SHORT_URL_FILENAME = 4;

    private static final int PHYSICAL_LONG_URI_PHYSICAL_KEY = 4;
    private static final int VIRTUAL_LONG_URI_BLOB_KEY = 4;
    private static final int LONG_URL_FILENAME = 5;

    private BlobUriParser() {
    }

    /**
     * Parses the given URI into a {@link BlobUri}.
     *
     * @param uri the URI to parse
     * @return the parsed URI as {@link BlobUri}
     */
    public static Optional<BlobUri> parseBlobUri(String uri) {
        if (!uri.startsWith(BlobDispatcher.URI_PREFIX_TRAILED)) {
            return Optional.empty();
        }

        BlobUri result = new BlobUri();

        // Cut off "/dasd/"...
        uri = uri.substring(URI_PREFIX_LENGTH);

        // Cut off "xxl/" if present...
        if (uri.startsWith(BlobDispatcher.LARGE_FILE_MARKER)) {
            uri = uri.substring(LARGE_FILE_MARKER_LENGTH);
            result.withLargeFileExpected(true);
        }

        Values uriParts = Values.of(uri.split("/"));

        if (uriParts.length() < 3) {
            return Optional.empty();
        }

        String type = uriParts.at(TYPE).asString();

        result.withCacheable(type.contains(BlobDispatcher.FLAG_CACHEABLE))
              .withDownload(type.contains(BlobDispatcher.FLAG_DOWNLOAD))
              .withStorageSpace(uriParts.at(STORAGE_SPACE).asString())
              .withAccessToken(uriParts.at(ACCESS_TOKEN).asString());

        if (type.contains(BlobDispatcher.FLAG_PHYSICAL)) {
            return parsePhysicalUri(result, uriParts);
        }

        if (type.contains(BlobDispatcher.FLAG_VIRTUAL)) {
            return parseVirtualUri(result, uriParts);
        }

        return Optional.empty();
    }

    @Nonnull
    private static Optional<BlobUri> parsePhysicalUri(BlobUri result, Values uriParts) {
        // Handles /dasd/p/SPACE/ACCESS_TOKEN/BLOB_KEY/seo--PHYSICAL_KEY.FILE_EXTENSION
        if (uriParts.length() == 5) {
            String filename = stripAdditionalText(uriParts.at(SHORT_URL_FILENAME).asString());
            String physicalKey = Files.getFilenameWithoutExtension(filename);

            return Optional.of(result.withPhysical(true)
                                     .withBlobKey(uriParts.at(PHYSICAL_URI_BLOB_KEY).asString())
                                     .withPhysicalKey(physicalKey)
                                     .withFilename(filename));
        }

        // Handles /dasd/p[d]/SPACE/ACCESS_TOKEN/BLOB_KEY/PHYSICAL_KEY/seo--FILENAME.FILE_EXTENSION
        if (uriParts.length() == 6) {
            String filename = stripAdditionalText(uriParts.at(LONG_URL_FILENAME).asString());

            return Optional.of(result.withPhysical(true)
                                     .withBlobKey(uriParts.at(PHYSICAL_URI_BLOB_KEY).asString())
                                     .withPhysicalKey(uriParts.at(PHYSICAL_LONG_URI_PHYSICAL_KEY).asString())
                                     .withFilename(filename));
        }

        return Optional.empty();
    }

    @Nonnull
    private static Optional<BlobUri> parseVirtualUri(BlobUri result, Values uriParts) {
        // Handles /dasd/[c]v/SPACE/ACCESS_TOKEN/VARIANT/seo--BLOB_KEY.FILE_EXTENSION
        if (uriParts.length() == 5) {
            String filename = stripAdditionalText(uriParts.at(SHORT_URL_FILENAME).asString());
            String blobKey = Files.getFilenameWithoutExtension(filename);

            return Optional.of(result.withBlobKey(blobKey)
                                     .withVariant(uriParts.at(VIRTUAL_URI_VARIANT).asString())
                                     .withFilename(filename));
        }

        // Handles /dasd/[c]v[d]/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY/seo--FILENAME.FILE_EXTENSION
        if (uriParts.length() == 6) {
            String filename = stripAdditionalText(uriParts.at(LONG_URL_FILENAME).asString());

            return Optional.of(result.withBlobKey(uriParts.at(VIRTUAL_LONG_URI_BLOB_KEY).asString())
                                     .withVariant(uriParts.at(VIRTUAL_URI_VARIANT).asString())
                                     .withFilename(filename));
        }

        return Optional.empty();
    }

    /**
     * Strips off a SEO text to retrieve the effective filename.
     * <p>
     * Such an "enhanced" filename is generated when {@link URLBuilder#withAddonText(String)} was used.
     *
     * @param input the full filename with an optional SEO text as prefix
     * @return the filename with the additional text stripped of
     */
    private static String stripAdditionalText(String input) {
        Tuple<String, String> additionalTextAndKey = Strings.splitAtLast(input, "--");
        if (additionalTextAndKey.getSecond() == null) {
            return additionalTextAndKey.getFirst();
        } else {
            return additionalTextAndKey.getSecond();
        }
    }
}
