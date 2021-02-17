/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.kernel.Sirius;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Provides various helpers for the storage framework.
 * <p>
 * This provides access to the configuration for all layers and some authentication utilities.
 */
@Register(classes = StorageUtils.class)
public class StorageUtils {

    /**
     * Names the framework which must be enabled to activate the storage feature.
     */
    public static final String FRAMEWORK_STORAGE = "biz.storage";

    /**
     * Represents the central logger for the whole storage framework.
     */
    public static final Log LOG = Log.get("storage");

    /**
     * Pattern for cleaning up consecutive slashes and removing backslashes.
     */
    public static final Pattern SANITIZE_SLASHES = Pattern.compile("[/\\\\]+");

    /**
     * Lists the layers which are placed in the config as <tt>storage.layer1.spaces</tt> etc. Each of
     * these layers provide a list of {@link Extension extensions} - one per storage space.
     */
    public enum ConfigScope {LAYER1, LAYER2, LAYER3}

    @ConfigValue("storage.sharedSecret")
    @Nullable
    private String sharedSecret;
    private String safeSharedSecret;

    /**
     * Returns all configured extensions / storage spaces for the given scope.
     *
     * @param scope the scope to query
     * @return the list of extensions available for this scope
     */
    public Collection<Extension> getStorageSpaces(ConfigScope scope) {
        return Sirius.getSettings().getExtensions("storage." + scope.name().toLowerCase() + ".spaces");
    }

    /**
     * Verifies the authentication hash for the given key.
     *
     * @param key  the key to verify
     * @param hash the hash to verify
     * @return <tt>true</tt> if the hash verifies the given object key, <tt>false</tt> otherwise
     */
    public boolean verifyHash(String key, String hash) {
        // Check for a hash for today...
        if (Strings.areEqual(hash, computeHash(key, 0))) {
            return true;
        }

        // Check for an eternally valid hash...
        if (Strings.areEqual(hash, computeEternallyValidHash(key))) {
            return true;
        }

        // Check for hashes up to two days of age...
        for (int i = 1; i < 3; i++) {
            if (Strings.areEqual(hash, computeHash(key, -i)) || Strings.areEqual(hash, computeHash(key, i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Computes an authentication hash for the given storage key and the offset in days (from the current).
     *
     * @param key        the key to authenticate
     * @param offsetDays the offset from the current day
     * @return a hash valid for the given day and key
     */
    public String computeHash(String key, int offsetDays) {
        return Hasher.md5().hash(key + getTimestampOfDay(offsetDays) + getSharedSecret()).toHexString();
    }

    /**
     * Computes an authentication hash which is eternally valid.
     *
     * @param key the key to authenticate
     * @return a hash valid forever
     */
    public String computeEternallyValidHash(String key) {
        return Hasher.md5().hash(key + getSharedSecret()).toHexString();
    }

    /**
     * Generates a timestamp for the day plus the provided day offset.
     *
     * @param day the offset from the current day
     * @return the effective timestamp (number of days since 01.01.1970) in days
     */
    private String getTimestampOfDay(int day) {
        Instant midnight = LocalDate.now().plusDays(day).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return String.valueOf(midnight.toEpochMilli());
    }

    /**
     * Determines the shared secret to use.
     *
     * @return the shared secret to use. Which is either taken from <tt>storage.sharedSecret</tt> in the system config
     * or a random value if the system is not configured properly
     */
    private String getSharedSecret() {
        if (safeSharedSecret == null) {
            if (Strings.isFilled(sharedSecret)) {
                safeSharedSecret = sharedSecret;
            } else {
                LOG.WARN("Please specify a secure and random value for 'storage.sharedSecret' in the 'instance.conf'!");
                safeSharedSecret = String.valueOf(System.currentTimeMillis());
            }
        }

        return safeSharedSecret;
    }

    /**
     * Sanitizes the given path.
     * <p>
     * This will replace backslashes with forward slashes, and remove successive slashes. Trailing slashes are removed
     * from directory paths, and absolute paths are made relative by removing leading slashes.
     *
     * @param path the path to cleanup
     * @return the sanitized path without backslashes, successive slashes, and without leading and trailing slashes
     */
    @Nonnull
    public String sanitizePath(@Nullable String path) {
        path = Strings.trim(path);

        if (Strings.isEmpty(path)) {
            return "";
        }

        path = SANITIZE_SLASHES.matcher(path).replaceAll("/");

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Creates an output stream which writes into a local buffer and invokes the given consumer once the stream is closed.
     * <p>
     * Note that the local buffer is automatically deleted once the consumer has completed.
     *
     * @param dataConsumer the consumer which processes the locally buffered file
     * @return the output stream which can be used to fill the buffer
     * @throws IOException in case of an IO error while creating the local buffer
     */
    public OutputStream createLocalBuffer(Consumer<File> dataConsumer) throws IOException {
        File bufferFile = File.createTempFile("local-file-buffer", null);
        WatchableOutputStream out = new WatchableOutputStream(new FileOutputStream(bufferFile));
        out.getCompletionFuture().onFailure(error -> {
            Files.delete(bufferFile);
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(error)
                            .withSystemErrorMessage("An error occured while writing to a temporary buffer: %s (%s)")
                            .handle();
        });
        out.getCompletionFuture().onSuccess(() -> {
            try {
                dataConsumer.accept(bufferFile);
            } finally {
                Files.delete(bufferFile);
            }
        });

        return out;
    }

    /**
     * Creates an output stream which writes into a local buffer and invokes the given consumer once the stream is closed.
     * <p>
     * Note that the local buffer is deleted once the consumer has completed.
     *
     * @param dataConsumer the consumer which processes the local buffer by reading from an input stream
     * @return the output stream which can be used to fill the buffer
     * @throws IOException in case of an IO error while creating the local buffer
     */
    public OutputStream createLocallyBufferedStream(Consumer<InputStream> dataConsumer) throws IOException {
        return createLocalBuffer(bufferFile -> {
            try (InputStream in = new FileInputStream(bufferFile)) {
                dataConsumer.accept(in);
            } catch (IOException e) {
                throw Exceptions.handle()
                                .to(StorageUtils.LOG)
                                .error(e)
                                .withSystemErrorMessage(
                                        "An error occured while reading from a temporary buffer: %s (%s)")
                                .handle();
            }
        });
    }
}
