/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.storage.layer2.BlobDispatcher;
import sirius.biz.storage.layer2.BlobUri;
import sirius.biz.storage.layer2.BlobUriParser;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Resolves files of URIs as generated by {@link sirius.biz.storage.layer2.URLBuilder}.
 * <p>
 * This allows us to remove the (time limited) access token from the path and lets us apply our own directory structure.
 * Physical URIs will be resolved to <tt>/SPACE/BLOB_KEY-PHYSICAL_KEY.FILE_EXTENSION</tt> while virtual URIs will be
 * resolved to <tt>/SPACE/VARIANT/BLOB_KEY.FILE_EXTENSION</tt>.
 */
@Register
public class BlobUriFileResolver extends RemoteFileResolver {

    @Override
    public Tuple<VirtualFile, Boolean> resolve(VirtualFile parent,
                                               URI uri,
                                               FetchFromUrlMode mode,
                                               Predicate<String> fileExtensionVerifier,
                                               Set<Options> options) throws IOException {
        String path = uri.getPath();

        if (!fileExtensionVerifier.test(Files.getFileExtension(path))) {
            return null;
        }

        if (!path.startsWith(BlobDispatcher.URI_PREFIX_TRAILED)) {
            return null;
        }

        Optional<BlobUri> blobUri = BlobUriParser.parseBlobUri(path);

        if (blobUri.isPresent()) {
            String filePath = generateFilePath(blobUri.get());
            VirtualFile file = resolveVirtualFile(parent, filePath, uri.getHost(), options);
            return Tuple.create(file, file.performLoadFromUri(uri, mode));
        }

        return null;
    }

    private String generateFilePath(BlobUri blobUri) {
        String fileExtension = Files.getFileExtension(blobUri.getFilename());

        String directory;
        String filename;

        if (blobUri.isPhysical()) {
            directory = blobUri.getStorageSpace();
            filename = blobUri.getBlobKey() + "-" + blobUri.getPhysicalKey() + "." + fileExtension;
        } else {
            directory = blobUri.getStorageSpace() + "/" + blobUri.getVariant();
            filename = blobUri.getBlobKey() + "." + fileExtension;
        }

        return directory + "/" + filename;
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
