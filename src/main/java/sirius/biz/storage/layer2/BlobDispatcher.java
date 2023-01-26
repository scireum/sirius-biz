/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Values;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.http.Response;
import sirius.web.http.WebContext;
import sirius.web.http.WebDispatcher;

import javax.annotation.Nullable;

/**
 * Responsible for delivering {@link Blob blobs} managed by the {@link BlobStorage Layer 2} of the storage framework.
 * <p>
 * This will mainly deliver blobs via URLs generated by {@link URLBuilder}. As this is a central activity, doesn't need
 * any user authentication (URLs are pre-signed) and its responsibility can be checked easily (all requests URIs start
 * with a common {@link BlobDispatcher#URI_PREFIX}), this has a pretty low priority. Note that
 * {@link URLBuilder#enableLargeFileDetection()} can be used so that URLs pointing to large files always start with
 * the prefix <tt>/dasd/xxl</tt>. This can be used by upstream reverse-proxies to detect files which aren't worth or
 * healthy to cache.
 * <p>
 * Note that this dispatcher itself doesn't do more than decoding and verifying the URL. All the heavy lifting is
 * either done by {@link sirius.biz.storage.layer1.ObjectStorageSpace#deliver(Response, String, boolean)} for physical URLs
 * or {@link BlobStorageSpace#deliver(String, String, Response, Runnable)} for virtual URLs.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class BlobDispatcher implements WebDispatcher {

    /**
     * Contains the base prefix for all URIs handled by this dispatcher.
     * <p>
     * As this dispatcher is most probably active in every system we do not want to "block" a common name
     * like "storage" or the like. However, we have to pick a name after all. Therefore, we went with a rather short
     * acronym from the mainframe area (DASD stood/stands/will always stand for Direct Attached Storage Device) and
     * is nowadays simply called a hard disk. Yes, it doesn't match the purpose of the URI but its short, not a common
     * term and <b>fun</b>.
     */
    public static final String URI_PREFIX = "/dasd";

    /**
     * Contains the {@link #URI_PREFIX} with a trailing slash.
     */
    protected static final String URI_PREFIX_TRAILED = URI_PREFIX + "/";

    /**
     * Contains the prefix length ("/dasd/") to cut from an incoming URI
     */
    private static final int URI_PREFIX_LENGTH = URI_PREFIX_TRAILED.length();

    /**
     * Contains a marker which can be placed in a URI to signal that the underlying file might be very large.
     * <p>
     * The dispatcher itself simply ignores this marker, but upstream reverse-proxies like NGINX or Varnish
     * can use this to optimize their cache utilization (e.g. by fully ignoring or piping this request/response).
     */
    public static final String LARGE_FILE_MARKER = "xxl/";

    /**
     * Contains the prefix length ("xxl" + "/") to cut from an incoming URI
     */
    private static final int LARGE_FILE_MARKER_LENGTH = LARGE_FILE_MARKER.length();

    /**
     * Marks the request as "physical" access (direct layer 1 access).
     */
    public static final String FLAG_PHYSICAL = "p";

    /**
     * Marks the request as "download".
     * <p>
     * This will invoke {@link Response#download(String)} before starting delivery.
     */
    public static final String FLAG_DOWNLOAD = "d";

    /**
     * Marks the request as "virtual" (layer 2) access.
     */
    public static final String FLAG_VIRTUAL = "v";

    /**
     * Marks the request as cacheable.
     * <p>
     * Otherwise, all HTTPS cache settings would be turned off.
     */
    public static final String FLAG_CACHEABLE = "c";

    /**
     * Detects the flag combination for a direct physical delivery (no download).
     * <p>
     * This will expect a URI like: <tt>/dasd/p/SPACE/ACCESS_TOKEN/BLOB_KEY/PHYSICAL_KEY.FILE_EXTENSION</tt>
     */
    private static final String PHYSICAL_DELIVERY = FLAG_PHYSICAL;

    /**
     * Detects the flag combination for a physical download.
     * <p>
     * This will expect a URI like: <tt>/dasd/pd/SPACE/ACCESS_TOKEN/BLOB_KEY/PHYSICAL_KEY/FILENAME.FILE_EXTENSION</tt>
     */
    private static final String PHYSICAL_DOWNLOAD = FLAG_PHYSICAL + FLAG_DOWNLOAD;

    /**
     * Detects the flag combination for a virtual delivery which has no HTTP cache support.
     * <p>
     * This will expect a URI like: <tt>/dasd/v/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY.FILE_EXTENSION</tt>
     */
    private static final String VIRTUAL_DELIVERY = FLAG_VIRTUAL;

    /**
     * Detects the flag combination for a virtual download which has no HTTP cache support.
     * <p>
     * This will expect a URI like: <tt>/dasd/vd/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY/FILENAME.FILE_EXTENSION</tt>
     */
    private static final String VIRTUAL_DOWNLOAD = FLAG_VIRTUAL + FLAG_DOWNLOAD;

    /**
     * Detects the flag combination for a cacheable virtual delivery.
     * <p>
     * This will expect a URI like: <tt>/dasd/cv/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY.FILE_EXTENSION</tt>
     */
    private static final String VIRTUAL_CACHEABLE_DELIVERY = FLAG_CACHEABLE + FLAG_VIRTUAL;

    /**
     * Detects the flag combination for a cacheable virtual download.
     * <p>
     * This will expect a URI like: <tt>/dasd/cvd/SPACE/ACCESS_TOKEN/VARIANT/BLOB_KEY/FILENAME.FILE_EXTENSION</tt>
     */
    private static final String VIRTUAL_CACHEABLE_DOWNLOAD = FLAG_CACHEABLE + FLAG_VIRTUAL + FLAG_DOWNLOAD;

    private static final String PARAM_HOOK = "hook";
    private static final String PARAM_PAYLOAD = "payload";

    @Part
    private BlobStorage blobStorage;

    @Part
    private StorageUtils utils;

    @Part
    private GlobalContext globalContext;

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public DispatchDecision dispatch(WebContext request) throws Exception {
        String uri = request.getRequestedURI();
        if (!uri.startsWith(URI_PREFIX_TRAILED)) {
            return DispatchDecision.CONTINUE;
        }

        // Cut off "/dasd/"...
        uri = uri.substring(URI_PREFIX_LENGTH);

        // Cut off "xxl/" if present...
        boolean largeFileExpected = false;
        if (uri.startsWith(LARGE_FILE_MARKER)) {
            uri = uri.substring(LARGE_FILE_MARKER_LENGTH);
            largeFileExpected = true;
        }

        installCompletionHook(uri, request);

        Values uriParts = Values.of(uri.split("/"));
        String type = uriParts.at(0).asString();
        if (Strings.areEqual(type, PHYSICAL_DELIVERY) && uriParts.length() == 5) {
            String filename = stripAdditionalText(uriParts.at(4).asString());
            physicalDelivery(request,
                             uriParts.at(1).asString(),
                             uriParts.at(2).asString(),
                             uriParts.at(4).asString(),
                             Files.getFilenameWithoutExtension(filename),
                             largeFileExpected,
                             filename);

            return DispatchDecision.DONE;
        }

        if (Strings.areEqual(type, PHYSICAL_DOWNLOAD) && uriParts.length() == 6) {
            physicalDownload(request,
                             uriParts.at(1).asString(),
                             uriParts.at(2).asString(),
                             uriParts.at(3).asString(),
                             uriParts.at(4).asString(),
                             largeFileExpected,
                             stripAdditionalText(uriParts.at(5).asString()));

            return DispatchDecision.DONE;
        }

        if (Strings.areEqual(type, VIRTUAL_DELIVERY) && uriParts.length() == 5) {
            String filename = stripAdditionalText(uriParts.at(4).asString());
            virtualDelivery(request,
                            uriParts.at(1).asString(),
                            uriParts.at(2).asString(),
                            uriParts.at(3).asString(),
                            Files.getFilenameWithoutExtension(filename),
                            filename,
                            false,
                            false);
            return DispatchDecision.DONE;
        }

        if (Strings.areEqual(type, VIRTUAL_DOWNLOAD) && uriParts.length() == 6) {
            virtualDelivery(request,
                            uriParts.at(1).asString(),
                            uriParts.at(2).asString(),
                            uriParts.at(3).asString(),
                            uriParts.at(4).asString(),
                            stripAdditionalText(uriParts.at(5).asString()),
                            true,
                            false);
            return DispatchDecision.DONE;
        }

        if (Strings.areEqual(type, VIRTUAL_CACHEABLE_DELIVERY) && uriParts.length() == 5) {
            String filename = stripAdditionalText(uriParts.at(4).asString());
            virtualDelivery(request,
                            uriParts.at(1).asString(),
                            uriParts.at(2).asString(),
                            uriParts.at(3).asString(),
                            Files.getFilenameWithoutExtension(filename),
                            filename,
                            false,
                            true);
            return DispatchDecision.DONE;
        }

        if (Strings.areEqual(type, VIRTUAL_CACHEABLE_DOWNLOAD) && uriParts.length() == 6) {
            virtualDelivery(request,
                            uriParts.at(1).asString(),
                            uriParts.at(2).asString(),
                            uriParts.at(3).asString(),
                            uriParts.at(4).asString(),
                            stripAdditionalText(uriParts.at(5).asString()),
                            true,
                            true);
            return DispatchDecision.DONE;
        }

        return DispatchDecision.CONTINUE;
    }

    private void installCompletionHook(String uri, WebContext request) {
        String hook = request.get(PARAM_HOOK).asString();
        if (Strings.isEmpty(hook)) {
            return;
        }

        String payload = request.get(PARAM_PAYLOAD).asString();
        request.getCompletionPromise().onSuccess(code -> {
            if (code == HttpResponseStatus.OK.code()) {
                executeHook(uri, hook, payload);
            }
        });
    }

    private void executeHook(String uri, String hook, String payload) {
        try {
            BlobDispatcherHook dispatcherHook = globalContext.getPart(hook, BlobDispatcherHook.class);
            if (dispatcherHook != null) {
                dispatcherHook.hook(payload);
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(StorageUtils.LOG)
                      .withSystemErrorMessage(
                              "An error occurred when executing hook '%s' with payload '%s' for URI: '%s'",
                              hook,
                              payload,
                              uri)
                      .handle();
        }
    }

    /**
     * Strips off a SEO text to retrieve the effective filename.
     * <p>
     * Such an "enhanced" filename is generated when {@link URLBuilder#withAddonText(String)} was used.
     *
     * @param input the full filename with an optional SEO text as prefix
     * @return the filename with the additional text stripped of
     */
    private String stripAdditionalText(String input) {
        Tuple<String, String> additionalTextAndKey = Strings.splitAtLast(input, "--");
        if (additionalTextAndKey.getSecond() == null) {
            return additionalTextAndKey.getFirst();
        } else {
            return additionalTextAndKey.getSecond();
        }
    }

    /**
     * Prepares a {@link Response} and delegates the call to the layer 1.
     *
     * @param request           the request to handle
     * @param space             the space which is accessed
     * @param accessToken       the security token to verify
     * @param blobKey           the blob key to be delivered
     * @param physicalKey       the physical object key used to determine which object should be delivered
     * @param largeFileExpected signals that a very large file is expected
     * @param filename          the filename which is used to set up a proper <tt>Content-Type</tt>
     */
    private void physicalDelivery(WebContext request,
                                  String space,
                                  String accessToken,
                                  String blobKey,
                                  String physicalKey,
                                  boolean largeFileExpected,
                                  String filename) {
        if (!utils.verifyHash(physicalKey, accessToken)) {
            request.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        Response response = request.respondWith().infinitelyCached().named(filename);
        blobStorage.getSpace(space).deliverPhysical(blobKey, physicalKey, response, largeFileExpected);
    }

    /**
     * Prepares a {@link Response} as download and delegates the call to the layer 1.
     *
     * @param request           the request to handle
     * @param space             the space which is accessed
     * @param accessToken       the security token to verify
     * @param blobKey           the blob key of the blob to download
     * @param physicalKey       the physical object key used to determine which object should be delivered
     * @param largeFileExpected signals that a very large file is expected
     * @param filename          the filename which is used to set up a proper <tt>Content-Type</tt>
     */
    private void physicalDownload(WebContext request,
                                  String space,
                                  String accessToken,
                                  String blobKey,
                                  String physicalKey,
                                  boolean largeFileExpected,
                                  String filename) {
        if (!utils.verifyHash(physicalKey, accessToken)) {
            request.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        Response response = request.respondWith().infinitelyCached().download(filename);
        blobStorage.getSpace(space).deliverPhysical(blobKey, physicalKey, response, largeFileExpected);
    }

    /**
     * Prepares a {@link Response} and delegates the call to the layer 2.
     *
     * @param request     the request to handle
     * @param space       the space which is accessed
     * @param accessToken the security token to verify
     * @param variant     the variant to deliver - {@link URLBuilder#VARIANT_RAW} will be used to deliver the blob
     *                    itself
     * @param blobKey     the blob object key used to determine which {@link Blob} should be delivered
     * @param filename    the filename which is used to set up a proper <tt>Content-Type</tt>
     * @param download    determines if a download should be generated
     * @param cacheable   determines if HTTP caching should be supported (<tt>true</tt>) or suppressed (<tt>false</tt>)
     */
    @SuppressWarnings("squid:S00107")
    @Explain("As this is a super hot code path we use 8 parameters instead of a parameter object"
             + " as this makes the URL parsing quite obvious")
    private void virtualDelivery(WebContext request,
                                 String space,
                                 String accessToken,
                                 @Nullable String variant,
                                 String blobKey,
                                 String filename,
                                 boolean download,
                                 boolean cacheable) {
        if (!utils.verifyHash(Strings.isFilled(variant) ? blobKey + "-" + variant : blobKey, accessToken)) {
            request.respondWith().error(HttpResponseStatus.UNAUTHORIZED);
            return;
        }

        BlobStorageSpace storageSpace = blobStorage.getSpace(space);
        Response response = request.respondWith();
        if (cacheable) {
            response.cached();
        } else {
            response.notCached();
        }
        if (download) {
            if (Strings.areEqual(filename, blobKey)) {
                filename = storageSpace.resolveFilename(blobKey).orElse(filename);
            }
            response.download(filename);
        } else {
            response.named(filename);
        }

        storageSpace.deliver(blobKey,
                             variant != null ? variant : URLBuilder.VARIANT_RAW,
                             response,
                             request::markAsLongCall);
    }
}
