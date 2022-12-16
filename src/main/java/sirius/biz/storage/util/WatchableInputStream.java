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
import java.util.function.Consumer;

/**
 * Wraps an {@link InputStream} and provides a {@link Future completion future}.
 * <p>
 * The future is fullfilled once the stream is closed.
 */
public class WatchableInputStream extends InputStream {

    private final InputStream delegate;
    private Runnable successHandler;
    private Consumer<Throwable> failureHandler;
    private boolean closeHandled = false;

    /**
     * Creates a new stream which wraps and delegates all calls to the given one.
     *
     * @param delegate the stream to wrap
     */
    public WatchableInputStream(@Nonnull InputStream delegate) {
        Objects.requireNonNull(delegate, "null was passed into a WatchableInputStream");
        this.delegate = delegate;
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
        } catch (IOException e) {
            // Close might be invoked several times (e.g. by some ZIP implementations).
            // Therefore, we filter this to only execute the handler once.
            if (failureHandler != null && !closeHandled) {
                closeHandled = true;
                failureHandler.accept(e);
            }

            throw e;
        }

        // Filter duplicate executions (s.a.)...
        if (successHandler != null && !closeHandled) {
            closeHandled = true;
            successHandler.run();
        }
    }

    /**
     * Adds a completion handler to this stream which is executed only if closing the stream is successful.
     *
     * @param successHandler the handler to be executed once the stream is successfully closed
     * @return <tt>this</tt> for fluent method chaining
     */
    public WatchableInputStream onSuccess(@Nonnull final Runnable successHandler) {
        if (this.successHandler != null) {
            throw new UnsupportedOperationException("Only one success handler can be specified");
        }

        this.successHandler = successHandler;
        return this;
    }

    /**
     * Adds a completion handler to this stream which is executed only if closing the stream fails.
     * <p>
     * The original exception will be thrown, if this handler doesn't throw its own exception.
     *
     * @param failureHandler the handler to be executed once closing the stream failed
     * @return <tt>this</tt> for fluent method chaining
     */
    public WatchableInputStream onFailure(@Nonnull final Consumer<Throwable> failureHandler) {
        if (this.failureHandler != null) {
            throw new UnsupportedOperationException("Only one failure handler can be specified");
        }

        this.failureHandler = failureHandler;
        return this;
    }

    /**
     * Adds a completion handler to this stream which is executed when the stream is closed.
     * <p>
     * In case of a failure, the original exception will be thrown, if this handler doesn't throw its own exception.
     *
     * @param completionHandler the handler to be executed once the stream is closed
     * @return <tt>this</tt> for fluent method chaining
     */
    public WatchableInputStream onCompletion(@Nonnull final Runnable completionHandler) {
        if (this.successHandler != null || this.failureHandler != null) {
            throw new UnsupportedOperationException("Only one completion handler can be specified");
        }

        this.successHandler = completionHandler;
        this.failureHandler = ignored -> completionHandler.run();
        return this;
    }
}
