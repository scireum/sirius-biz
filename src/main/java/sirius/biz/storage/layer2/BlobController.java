/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpHeaderNames;
import sirius.biz.storage.layer3.VirtualFileSystemController;
import sirius.biz.storage.util.StorageUtils;
import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Routed;
import sirius.web.http.InputStreamHandler;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.io.IOException;

/**
 * Provides some helper routes for managing and uploading {@link Blob blobs}.
 * <p>
 * Note that most of the management UI is handled via the VFS (layer 3) in the
 * {@link VirtualFileSystemController}.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class BlobController extends BizController {

    private static final String KEY_FILENAME = "filename";
    private static final String KEY_FILE = "qqfile";
    private static final String KEY_FILE_ID = "fileId";
    private static final String KEY_PREVIEW_URL = "previewUrl";
    private static final String KEY_IMAGE_URL = "imageUrl";
    private static final String KEY_DOWNLOAD_URL = "downloadUrl";
    private static final String KEY_SIZE = "size";
    private static final String KEY_FORMATTED_SIZE = "formattedSize";
    private static final String KEY_PATH = "path";

    @Part
    private BlobStorage blobStorage;

    /**
     * Uploads a file into a temporary {@link Blob}, to be later persisted via a {@link BlobHardRef}.
     * <p>
     * As long as the blob remains unreferenced, it will be marked as temporary and will be eventually
     * deleted. However, if an entity references the blob via a {@link BlobHardRef}, it will be marked
     * as permanent.
     *
     * @param webContext the request to handle
     * @param output     the response to the AJAX call
     * @param spaceName  the {@link BlobStorageSpace} to store the object in
     * @param upload     the content of the upload
     */
    @Routed(value = "/dasd/upload-file/:1", preDispatchable = true)
    @InternalService
    @LoginRequired
    public void uploadFile(final WebContext webContext,
                           JSONStructuredOutput output,
                           String spaceName,
                           InputStreamHandler upload) {
        Blob blob = blobStorage.getSpace(spaceName).createTemporaryBlob();
        try {
            try (upload) {
                webContext.markAsLongCall();
                //TODO SIRI-96 remove legacy qqfile once library is updated...
                String name = webContext.get(KEY_FILENAME).asString(webContext.get(KEY_FILE).asString());
                blob.updateContent(name, upload, Long.parseLong(webContext.getHeader(HttpHeaderNames.CONTENT_LENGTH)));

                output.property(KEY_FILE_ID, blob.getBlobKey());

                // TODO SIRI-96 remove once the blobHardRefField has been refactored
                output.property(KEY_PREVIEW_URL, blob.url().asDownload().buildURL().orElse(""));
                output.property(KEY_IMAGE_URL,
                                blob.url()
                                    .withVariant(webContext.get("variant").asString("raw"))
                                    .buildURL()
                                    .orElse(""));

                output.property(KEY_DOWNLOAD_URL, blob.url().asDownload().buildURL().orElse(""));
                output.property(KEY_FILENAME, blob.getFilename());
                output.property(KEY_SIZE, blob.getSize());
                output.property(KEY_FORMATTED_SIZE, NLS.formatSize(blob.getSize()));
            }
        } catch (IOException exception) {
            blob.delete();
            throw Exceptions.createHandled().error(exception).handle();
        } catch (Exception exception) {
            blob.delete();
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(exception)
                            .withSystemErrorMessage("Failed to upload a file into space '%s': %s (%s)", spaceName)
                            .handle();
        }
    }

    /**
     * Finds the {@link Blob} for a given path and returns info about it.
     *
     * @param webContext the request to handle
     * @param output     the response to the AJAX call
     * @param spaceName  the {@link BlobStorageSpace} to find the object in
     */
    @Routed("/dasd/blob-info-for-path/:1")
    @InternalService
    @LoginRequired
    public void blobInfoForPath(final WebContext webContext, JSONStructuredOutput output, String spaceName) {
        String path = webContext.getParameter(KEY_PATH);

        BlobStorageSpace space = blobStorage.getSpace(spaceName);

        if (!space.isBrowsable()) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("The space '%s' is not accessible", spaceName)
                            .handle();
        }

        Blob blob = space.findByPath(path)
                         .orElseThrow(() -> Exceptions.createHandled()
                                                      .withSystemErrorMessage(
                                                              "Cannot find blob for path '%s' in space '%s'",
                                                              path,
                                                              spaceName)
                                                      .handle());

        output.property(KEY_FILE_ID, blob.getBlobKey());
        output.property(KEY_FILENAME, blob.getFilename());
        output.property(KEY_SIZE, blob.getSize());
        output.property(KEY_FORMATTED_SIZE, NLS.formatSize(blob.getSize()));
        output.property(KEY_DOWNLOAD_URL, blob.url().asDownload().buildURL().orElse(""));
    }
}
