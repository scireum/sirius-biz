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
import sirius.biz.storage.layer2.URLBuilder;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.templates.pdf.handlers.PdfReplaceHandler;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Resolves blob:// URIs to resized images while maintaining the image ratios.
 * <p>
 * The format of the URI needs to match blob://space/blobKey or blob://space/blobKey/variant.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
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

        if (blobInfo.length != 2 && blobInfo.length != 3) {
            throw new IllegalArgumentException(
                    "The URI is required to match the format 'blob://space/blobKey/variant' or 'blob://space/blobKey'");
        }

        String spaceKey = blobInfo[0];
        String blobKey = blobInfo[1];
        String variant = URLBuilder.VARIANT_RAW;

        if (blobInfo.length == 3) {
            variant = blobInfo[2];
        }

        Optional<FileHandle> optionalFileHandle = storage.getSpace(spaceKey).download(blobKey, variant);

        if (optionalFileHandle.isPresent()) {
            try (FileHandle fileHandle = optionalFileHandle.get()) {
                FSImage image = resolveResource(userAgentCallback, fileHandle.getFile().toURI().toURL());

                return resizeImage(image, cssWidth, cssHeight);
            }
        }

        return null;
    }
}
