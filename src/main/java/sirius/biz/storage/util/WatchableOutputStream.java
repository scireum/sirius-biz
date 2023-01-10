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
import java.util.function.Consumer;

/**
 * Wraps an {@link OutputStream} and provides a {@link Future completion future}.
 * <p>
 * The future is fullfilled once the stream is closed.
 */
public class WatchableOutputStream extends OutputStream {

    private final OutputStream delegate;
    private Runnable successHandler;
    private Consumer<Throwable> failureHandler;
    private volatile boolean closeHandled = false;

    /**
     * Creates a new stream which wraps and delegates all calls to the given one.
     *
     * @param delegate the stream to wrap
     */
    public WatchableOutputStream(@Nonnull OutputStream delegate) {
        Objects.requireNonNull(delegate, "null was passed into a WatchableOutputStream");
        this.delegate = delegate;
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

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    /**
     * Sets the completion handler to this stream which is executed only if closing the stream is successful.
     *
     * @param successHandler the handler to be executed once the stream is successfully closed
     * @return <tt>this</tt> for fluent method chaining
     */
    public WatchableOutputStream onSuccess(@Nonnull final Runnable successHandler) {
        if (this.successHandler != null) {
            throw new UnsupportedOperationException("Only one success handler can be specified");
        }

        this.successHandler = successHandler;
        return this;
    }

    /**
     * Sets the completion handler to this stream which is executed only if closing the stream fails.
     * <p>
     * The original exception will be thrown, if this handler doesn't throw its own exception.
     *
     * @param failureHandler the handler to be executed once closing the stream failed
     * @return <tt>this</tt> for fluent method chaining
     */
    public WatchableOutputStream onFailure(@Nonnull final Consumer<Throwable> failureHandler) {
        if (this.failureHandler != null) {
            throw new UnsupportedOperationException("Only one failure handler can be specified");
        }

        this.failureHandler = failureHandler;
        return this;
    }

    /**
     * Installs a completion handler to this stream which is executed when the stream is closed.
     * <p>
     * In case of a failure, the original exception will be thrown, if this handler doesn't throw its own exception.
     *
     * @param completionHandler the handler to be executed once the stream is closed
     * @return <tt>this</tt> for fluent method chaining
     */
    public WatchableOutputStream onCompletion(@Nonnull final Runnable completionHandler) {
        if (this.successHandler != null || this.failureHandler != null) {
            throw new UnsupportedOperationException("Only one completion handler can be specified");
        }

        this.successHandler = completionHandler;
        this.failureHandler = ignored -> completionHandler.run();
        return this;
    }
}
