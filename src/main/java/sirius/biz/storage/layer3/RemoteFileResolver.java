/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import io.netty.handler.codec.http.QueryStringDecoder;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Priorized;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * Provides a resolver which is used to resolve a URI into a {@linkplain VirtualFile virtual file}.
 * <p>
 * Subclasses are in charge of determining the path of the virtual file, fetching it if required, and ultimately
 * returning the virtual file itself.
 */
@AutoRegister
public abstract class RemoteFileResolver implements Priorized {

    /**
     * Tries to resolve the given URI into a {@linkplain VirtualFile virtual file}.
     *
     * @param parent                the parent directory
     * @param uri                   the URI to resolve
     * @param mode                  the mode to use when fetching the file
     * @param fileExtensionVerifier the verifier used to check if the path or any query string parameter contains a
     *                              valid file name
     * @return a tuple containing the resolved file and a flag which indicates if the file was fetched from the remote,
     * or <tt>null</tt> if the file could not be resolved
     * @throws IOException in case of an IO error
     */
    public abstract Tuple<VirtualFile, Boolean> resolve(VirtualFile parent,
                                                        URI uri,
                                                        FetchFromUrlMode mode,
                                                        Predicate<String> fileExtensionVerifier) throws IOException;

    /**
     * Determines if this resolver requires a request to the remote system to determine the virtual path.
     * <p>
     * Note that this only refers to the resolution of the path, not the actual fetching of the file.
     *
     * @return <tt>true</tt> if a request is required, <tt>false</tt> otherwise
     */
    public boolean requiresRequestForPathResolve() {
        return false;
    }

    /**
     * Tries to parse the effective filename from the given URI.
     * <p>
     * This will first check the path for a valid file extension. If this isn't applicable, it will check every
     * parameter of the query string for a valid file extension.
     * <p>
     * Note that this will not perform a HEAD request or the like, to fetch the <tt>Content-Disposition</tt> header.
     *
     * @param uri                   the URI to check
     * @param fileExtensionVerifier the verifier used to check if the path or any query string parameter contains a
     *                              valid file name
     * @return the extracted filename (with path) or <tt>null</tt> if none could be extracted
     */
    @Nullable
    protected String parsePathFromUri(URI uri, Predicate<String> fileExtensionVerifier) {
        if (fileExtensionVerifier.test(Files.getFileExtension(uri.getPath()))) {
            return uri.getPath();
        }

        // If the URL has a querystring, we check every parameter and determine if there is one with a valid filename
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri.toString(), StandardCharsets.UTF_8);
        return queryStringDecoder.parameters()
                                 .values()
                                 .stream()
                                 .flatMap(List::stream)
                                 .filter(path -> fileExtensionVerifier.test(Files.getFileExtension(path)))
                                 .findFirst()
                                 .orElse(null);
    }

    /**
     * Resolves the given path against the given parent.
     *
     * @param parent the parent directory
     * @param path   the path to resolve
     * @return the resolved file
     */
    protected VirtualFile resolveVirtualFile(VirtualFile parent, String path) {
        return parent.resolve(path);
    }
}
