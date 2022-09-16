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
import java.io.InputStream;
import java.util.Objects;

/**
 * Wraps an {@link InputStream} and provides a {@link Future completion future}.
 * <p>
 * The future is fullfilled once the stream is closed.
 */
public class WatchableInputStream extends InputStream {

    private final InputStream delegate;
    private final Future completionFuture;

    /**
     * Creates a new stream which wraps and delegates all calls to the given one.
     *
     * @param delegate the stream to wrap
     */
    public WatchableInputStream(@Nonnull InputStream delegate) {
        Objects.requireNonNull(delegate, "null was passed into a WatchableInputStream");
        this.delegate = delegate;
        this.completionFuture = new Future();
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();

            // Filter duplicate completions...
            if (!completionFuture.isCompleted()) {
                completionFuture.success();
            }
        } catch (IOException e) {
            // Filter duplicate completions...
            if (!completionFuture.isCompleted()) {
                completionFuture.fail(e);
            }
            throw e;
        }
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
