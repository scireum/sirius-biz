/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2;

import io.netty.handler.codec.http.HttpHeaderNames;
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
import sirius.web.services.JSONStructuredOutput;

import java.io.IOException;

/**
 * Provides some helper routes for managing and uploading {@link Blob blobs}.
 * <p>
 * Note that most of the management UI is handled via the VFS (layer 3) in the
 * {@link sirius.biz.storage.layer3.VFSController}.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class BlobController extends BizController {

    @Part
    private BlobStorage blobStorage;

    /**
     * Uploads a file into a temporary {@link Blob}, to be later persisted via a {@link BlobHardRef}.
     * <p>
     * As long as the blob remains unreferenced, it will be marked as temporary and will be eventually
     * deleted. However, if an entity references the blob via a {@link BlobHardRef}, it will be marked
     * as permanent.
     *
     * @param ctx       the request to handle
     * @param out       the response to the AJAX call
     * @param spaceName the {@link BlobStorageSpace} to store the object in
     * @param upload    the content of the upload
     */
    @Routed(value = "/dasd/upload-file/:1", preDispatchable = true, jsonCall = true)
    @LoginRequired
    public void uploadFile(final WebContext ctx,
                           JSONStructuredOutput out,
                           String spaceName,
                           InputStreamHandler upload) {
        Blob blob = blobStorage.getSpace(spaceName).createTemporaryBlob();
        try {
            try {
                ctx.markAsLongCall();
                //TODO SIRI-96 remove legacy qqfile once library is updated...
                String name = ctx.get("filename").asString(ctx.get("qqfile").asString());
                blob.updateContent(name, upload, Long.parseLong(ctx.getHeader(HttpHeaderNames.CONTENT_LENGTH)));

                out.property("fileId", blob.getBlobKey());

                // TODO SIRI-96 remove once the blobHardRefField has been refactored
                out.property("previewUrl", blob.url().asDownload().buildURL().orElse(""));

                out.property("downloadUrl", blob.url().asDownload().buildURL().orElse(""));
                out.property("filename", blob.getFilename());
                out.property("size", blob.getSize());
                out.property("formattedSize", NLS.formatSize(blob.getSize()));
            } finally {
                upload.close();
            }
        } catch (IOException e) {
            blob.delete();
            throw Exceptions.createHandled().error(e).handle();
        } catch (Exception e) {
            blob.delete();
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(e)
                            .withSystemErrorMessage("Failed to upload a file into space '%s': %s (%s)", spaceName)
                            .handle();
        }
    }

    @Routed(value = "/dasd/blob-info-for-path/:1", jsonCall = true)
    @LoginRequired
    public void blobInfoForPath(final WebContext ctx, JSONStructuredOutput out, String spaceName) {
        String path = ctx.getParameter("path");

        Blob blob = blobStorage.getSpace(spaceName)
                               .findByPath(path)
                               .orElseThrow(() -> Exceptions.createHandled()
                                                            .withSystemErrorMessage(
                                                                    "Cannot find blob for path '%s' in space '%s'",
                                                                    path,
                                                                    spaceName)
                                                            .handle());

        out.property("fileId", blob.getBlobKey());
        out.property("filename", blob.getFilename());
        out.property("size", blob.getSize());
        out.property("formattedSize", NLS.formatSize(blob.getSize()));
        out.property("downloadUrl", blob.url().asDownload().buildURL().orElse(""));
    }
}
