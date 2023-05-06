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
import java.text.Normalizer;
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
     * Defines the default url validity time in days.
     */
    public static final int DEFAULT_URL_VALIDITY_DAYS = 2;

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
     * Regular Expressions that matches any character that is not allowed in a file.
     * <p>
     * These chars are prohibited as they might be reserved by the file system as indicated here: https://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
     */
    public static final Pattern SANITIZE_ILLEGAL_FILE_CHARS = Pattern.compile("[?%*:|\"<>]");

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
     * @param key          the key to verify
     * @param hash         the hash to verify
     * @param validityDays the number of days the hash should be valid into the past
     * @return <tt>true</tt> if the hash verifies the given object key, <tt>false</tt> otherwise
     */
    public boolean verifyHash(String key, String hash, int validityDays) {
        // Check for a hash for today...
        if (Strings.areEqual(hash, computeHash(key, 0))) {
            return true;
        }

        // Check for an eternally valid hash...
        if (Strings.areEqual(hash, computeEternallyValidHash(key))) {
            return true;
        }

        // Check for hashes up to X days into the past...
        for (int i = 1; i <= validityDays; i++) {
            if (Strings.areEqual(hash, computeHash(key, -i))) {
                return true;
            }
        }
        // Check for hashes up to two days into the future...
        for (int i = 1; i <= DEFAULT_URL_VALIDITY_DAYS; i++) {
            if (Strings.areEqual(hash, computeHash(key, i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifies the authentication hash for the given key.
     *
     * @param key  the key to verify
     * @param hash the hash to verify
     * @return <tt>true</tt> if the hash verifies the given object key, <tt>false</tt> otherwise
     */
    public boolean verifyHash(String key, String hash) {
        return verifyHash(key, hash, DEFAULT_URL_VALIDITY_DAYS);
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
     * Normalizes the text to {@link Normalizer.Form#NFC combined unicode}, replaces backslashes with forward slashes,
     * and removes successive slashes. Trailing slashes are removed from directory paths,
     * and absolute paths are made relative by removing leading slashes.
     * Also replaces chars that may be illegal in file systems with {@code _}.
     *
     * @param path the path to cleanup
     * @return the sanitized path without illegal characters
     */
    @Nonnull
    public String sanitizePath(@Nullable String path) {
        path = Strings.trim(path);

        if (Strings.isEmpty(path)) {
            return "";
        }

        path = replaceIllegalFileChars(path, false);

        path = Normalizer.normalize(path, Normalizer.Form.NFC);

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Can be used to replace chars in a path or file name that might be reserved by the file system.
     * <p>
     * Also cleans up multiple slashes and replaces backslashes. These can either be kept as a single forward slash
     * or also be replaced by {@code _}, depending on the replaceSlashes parameter.
     *
     * @param path           the path or filename to clean up
     * @param replaceSlashes if slashes should be replaced or only cleaned up
     * @return the path or file name without illegal chars
     */
    public String replaceIllegalFileChars(String path, boolean replaceSlashes) {
        path = SANITIZE_SLASHES.matcher(path).replaceAll(replaceSlashes ? "_" : "/");
        return SANITIZE_ILLEGAL_FILE_CHARS.matcher(path).replaceAll("_");
    }

    /**
     * Validates whether the given file name is empty or contains chars that might be reserved by the file system.
     *
     * @param name the file name to check
     * @return <tt>true</tt> when the given name is empty or contains unwanted chars
     */
    public boolean containsIllegalFileChars(String name) {
        if (Strings.isEmpty(name)) {
            return true;
        }
        return SANITIZE_ILLEGAL_FILE_CHARS.split(name).length > 1 || SANITIZE_SLASHES.split(name).length > 1;
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
        out.onFailure(error -> {
            Files.delete(bufferFile);
            throw Exceptions.handle()
                            .to(StorageUtils.LOG)
                            .error(error)
                            .withSystemErrorMessage("An error occurred while writing to a temporary buffer: %s (%s)")
                            .handle();
        });
        out.onSuccess(() -> {
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
                                        "An error occurred while reading from a temporary buffer: %s (%s)")
                                .handle();
            }
        });
    }
}
