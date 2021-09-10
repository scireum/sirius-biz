/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.kernel.async.Future;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Wraps an {@link OutputStream} and provides a {@link Future completion future}.
 * <p>
 * The future is fullfilled once the stream is closed.
 */
public class WatchableOutputStream extends OutputStream {

    private final OutputStream delegate;
    private final Future completionFuture;

    /**
     * Creates a new stream which wraps and delegates all calls to the given one.
     *
     * @param delegate the stream to wrap
     */
    public WatchableOutputStream(@Nonnull OutputStream delegate) {
        Objects.requireNonNull(delegate, "null was passed into a WatchableOutputStream");
        this.delegate = delegate;
        this.completionFuture = new Future();
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();

            // Close might be invoked several times (e.g. by some ZIP implementations).
            // Therefore, we filter this to only fulfill the future once.
            if (!completionFuture.isCompleted()) {
                completionFuture.success();
            }
        } catch (IOException e) {
            // Filter duplicate completions (s.a.)...
            if (!completionFuture.isCompleted()) {
                completionFuture.fail(e);
            }
            throw e;
        }
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    /**
     * Provides the completion future which is fullfilled once the stream is closed.
     *
     * @return the completion future of this stream
     */
    public Future getCompletionFuture() {
        return completionFuture;
    }
}
