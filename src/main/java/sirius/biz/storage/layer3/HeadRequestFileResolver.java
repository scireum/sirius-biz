/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Streams;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.Outcall;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Resolves files by performing a HEAD request to the given URI.
 * <p>
 * If the request is successful, the path is extracted from the <tt>Content-Disposition</tt> header. If none was
 * returned, but redirects were followed, we attempt to extract the path from the new URL. If the server doesn't support
 * HEAD requests, we directly perform a GET request and hope the <tt>Content-Disposition</tt> header is set.
 */
@Register
public class HeadRequestFileResolver extends RemoteFileResolver {

    @Override
    public boolean requiresRequestForPathResolve() {
        return true;
    }

    @Override
    public Tuple<VirtualFile, Boolean> resolve(VirtualFile parent,
                                               URI uri,
                                               FetchFromUrlMode mode,
                                               Predicate<String> fileExtensionVerifier,
                                               Set<Options> options) throws IOException {
        try {
            Outcall headRequest = new Outcall(uri);
            headRequest.markAsHeadRequest();
            headRequest.alwaysFollowRedirects();
            headRequest.modifyClient().connectTimeout(Duration.ofSeconds(10));

            CookieManager cookieManager = new CookieManager();
            headRequest.modifyClient().cookieHandler(cookieManager);

            String path = headRequest.parseFileNameFromContentDisposition()
                                     .filter(filename -> fileExtensionVerifier.test(Files.getFileExtension(filename)))
                                     .orElse(null);

            URI lastConnectedURI = headRequest.getResponse().request().uri();

            if (Strings.isEmpty(path) && !uri.getPath().equals(lastConnectedURI.getPath())) {
                // We don't have a path yet, but we followed redirects, so we check the new URI
                if (headRequest.getResponseCode() == HttpResponseStatus.NOT_FOUND.code() && lastConnectedURI.toString()
                                                                                                            .contains(
                                                                                                                    "Ã")) {
                    // We followed a redirect header in UTF-8 that was interpreted as ISO-8859-1, indicated by 'Ã' in the url
                    // as the starting byte of two byte characters in UTF-8 will always be interpreted as 'Ã' in ISO-8859-1
                    lastConnectedURI =
                            new URI(new String(lastConnectedURI.toString().getBytes(StandardCharsets.ISO_8859_1),
                                               StandardCharsets.UTF_8));
                }
                path = parsePathFromUri(lastConnectedURI, fileExtensionVerifier);
            }

            if (Strings.isFilled(path)) {
                VirtualFile file = resolveVirtualFile(parent, path, uri.getHost(), options);
                LocalDateTime lastModifiedHeader =
                        headRequest.getHeaderFieldDate(HttpHeaderNames.LAST_MODIFIED.toString()).orElse(null);
                if (lastModifiedHeader == null
                    || !file.exists()
                    || mode == FetchFromUrlMode.ALWAYS_FETCH
                    || file.lastModifiedDate().isBefore(lastModifiedHeader)) {
                    return Tuple.create(file, file.performLoadFromUri(lastConnectedURI, mode));
                } else {
                    return Tuple.create(file, false);
                }
            }

            if (!shouldRetryWithGet(headRequest.getResponse())) {
                return null;
            }
        } catch (HttpTimeoutException | URISyntaxException exception) {
            Exceptions.ignore(exception);
        }

        // We either ran into a timeout or the server doesn't support HEAD requests -> re-attempt with a GET
        return resolveViaGetRequest(parent, uri, mode, options);
    }

    private boolean shouldRetryWithGet(HttpResponse<?> response) {
        if (response.statusCode() == HttpResponseStatus.METHOD_NOT_ALLOWED.code() && allowsGet(response)) {
            // server disallows HEAD request and indicates GET is allowed
            return true;
        }

        // some servers will improperly respond with 503 or 501 if HEAD requests are not allowed
        // - we want to retry anyway
        return response.statusCode() == HttpResponseStatus.NOT_IMPLEMENTED.code()
               || response.statusCode() == HttpResponseStatus.SERVICE_UNAVAILABLE.code();
    }

    private boolean allowsGet(HttpResponse<?> response) {
        return response.headers()
                       .firstValue(HttpHeaderNames.ALLOW.toString())
                       .filter(header -> header.toUpperCase().contains(HttpMethod.GET.name()))
                       .isPresent();
    }

    private Tuple<VirtualFile, Boolean> resolveViaGetRequest(VirtualFile parent,
                                                             URI uri,
                                                             FetchFromUrlMode mode,
                                                             Set<Options> options) throws IOException {
        Outcall request = new Outcall(uri);
        request.alwaysFollowRedirects();
        String path = request.parseFileNameFromContentDisposition().orElse(null);
        if (Strings.isEmpty(path)) {
            // Drain any content, the server sent, as we have no way of processing it...
            Streams.exhaust(request.getResponse().body());
            return null;
        }

        VirtualFile file = resolveVirtualFile(parent, path, uri.getHost(), options);
        if (file.exists() && mode == FetchFromUrlMode.NON_EXISTENT) {
            // Drain any content, as the mode dictates not to update the file (which might require another upload,
            // so discarding the data is faster).
            Streams.exhaust(request.getResponse().body());
            return Tuple.create(file, false);
        }

        LocalDateTime lastModifiedHeader =
                request.getHeaderFieldDate(HttpHeaderNames.LAST_MODIFIED.toString()).orElse(null);
        if (lastModifiedHeader == null
            || !file.exists()
            || mode == FetchFromUrlMode.ALWAYS_FETCH
            || file.lastModifiedDate().isBefore(lastModifiedHeader)) {
            // Directly load the file from the response, we don't need another request.
            file.loadFromOutcall(request);
            return Tuple.create(file, true);
        } else {
            // Drain any content, as the mode dictates not to update the file (which might require another upload,
            // so discarding the data is faster).
            Streams.exhaust(request.getResponse().body());
            return Tuple.create(file, false);
        }
    }

    @Override
    public int getPriority() {
        return 300;
    }
}
