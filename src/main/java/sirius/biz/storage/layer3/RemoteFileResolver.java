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
import java.util.Set;
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
     * @param options               additional options for resolving the file
     * @return a tuple containing the resolved file and a flag which indicates if the file was fetched from the remote,
     * or <tt>null</tt> if the file could not be resolved
     * @throws IOException in case of an IO error
     */
    public abstract Tuple<VirtualFile, Boolean> resolve(VirtualFile parent,
                                                        URI uri,
                                                        FetchFromUrlMode mode,
                                                        Predicate<String> fileExtensionVerifier,
                                                        Set<Options> options) throws IOException;

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
     * @param parent  the parent directory
     * @param path    the path to resolve
     * @param host    the host to use (if {@link Options#INCLUDE_HOST_NAME} is set)
     * @param options additional options for resolving the file
     * @return the resolved file
     */
    protected VirtualFile resolveVirtualFile(VirtualFile parent, String path, String host, Set<Options> options) {
        if (options.contains(Options.ALSO_RESOLVE_WITHOUT_DOMAIN)) {
            VirtualFile file = parent.resolve(path);

            if (file.exists()) {
                return file;
            }
        }

        if (options.contains(Options.INCLUDE_HOST_NAME)) {
            return parent.resolve(cleanHostName(host) + "/" + path);
        }

        return parent.resolve(path);
    }

    private String cleanHostName(String host) {
        if (host.startsWith("www.")) {
            return host.substring(4);
        }

        return host;
    }

    /**
     * Represents the options which are used to resolve a file.
     */
    public enum Options {

        /**
         * Instructs the resolver to include the host name in the path.
         * <p>
         * Note that {@code www.} is always stripped from the host name.
         * <p>
         * {@code https://www.p1.example.com/foo/bar.jpg} would become {@code p1.example.com/foo/bar.jpg}.
         */
        INCLUDE_HOST_NAME,

        /**
         * Instructs the resolver to also resolve the file without the host name in the path.
         * <p>
         * This is useful if the file was previously stored without the domain and the host name was added later. For
         * example {@code https://example.com/foo/bar.jpg}: We first try to resolve an existing file under
         * {@code foo/bar.jpg}, and only if none exist, we would use {@code example.com/foo/bar.jpg}.
         */
        ALSO_RESOLVE_WITHOUT_DOMAIN
    }
}
