/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.extend.UserAgentCallback;
import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.BlobStorage;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.templates.pdf.handlers.PdfReplaceHandler;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Resolves blob:// URIs to resized images while maintaining the image ratios.
 * <p>
 * The format of the URI needs to match blob://space/variant/blobKey.
 */
@Register
public class BlobPdfReplaceHandler extends PdfReplaceHandler {

    @Part
    private BlobStorage storage;

    @Override
    public boolean accepts(String protocol) {
        return "blob".equals(protocol);
    }

    @Nullable
    @Override
    public FSImage resolveUri(String uri, UserAgentCallback userAgentCallback, int cssWidth, int cssHeight)
            throws Exception {
        String[] blobInfo = Strings.split(uri, "://").getSecond().split("/");

        if (blobInfo.length != 3) {
            throw new IllegalArgumentException("The URI is required to match the format 'blob://space/variant/blobKey'");
        }

        Optional<FileHandle> fileHandle = storage.getSpace(blobInfo[0]).download(blobInfo[2], blobInfo[1]);

        if (fileHandle.isPresent()) {
            FSImage image = resolveResource(userAgentCallback, fileHandle.get().getFile().toURI().toURL());

            return resizeImage(image, cssWidth, cssHeight);
        }

        return null;
    }
}
