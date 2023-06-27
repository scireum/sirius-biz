/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;

import java.io.IOException;
import java.net.URI;
import java.util.function.Predicate;

/**
 * Resolves files by using the path and query parameters of the given URI.
 * <p>
 * If the path ends with a valid file extension, we use it as our virtual path. Otherwise, we check every query string
 * parameter and use the first one that has a valid file extension.
 */
@Register
public class PathAndQueryStringFileResolver extends RemoteFileResolver {

    @Override
    public Tuple<VirtualFile, Boolean> resolve(VirtualFile parent,
                                               URI uri,
                                               FetchFromUrlMode mode,
                                               Predicate<String> fileExtensionVerifier) throws IOException {
        String path = parsePathFromUri(uri, fileExtensionVerifier);

        if (Strings.isFilled(path)) {
            VirtualFile file = resolveVirtualFile(parent, path);
            return Tuple.create(file, file.performLoadFromUri(uri, mode));
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 200;
    }
}
