/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.kernel.di.std.Priorized;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Describes a hash or key derivation function which is used to compute a hash for a
 * password which can be safely stored.
 * <p>
 * As cryptographic requirements change, and also to be able to support legacy accounts,
 * multiple functions can be present at once. If a function turns out to be less secure
 * than desired, it can be marked as {@link #isOutdated()}.
 * <p>
 * During login, each function will be checked. If the matching function is outdated,
 * the password (which is available as cleartext during login) is stored using the
 * first hash function (sorted by {@link #getPriority()}) which is not outdated.
 * <p>
 * This permits a transparent migration without affecting the users (forcing new password etc.).
 */
public interface PasswordHashFunction extends Priorized {

    /**
     * Computes the "hash" or cryptographic derivation of the given password which can be stored in a database.
     *
     * @param username contains the username for which the password hash is being computed as some legacy
     *                 implementations use it for their computation. Note that this is left empty when computing a new
     *                 hash, as it is considered very bad practice to do so.
     * @param salt     the salt used to protect the password
     * @param password the password itself
     * @return the computed hash or <tt>null</tt> to indicate that the input data is invalid
     */
    @Nullable
    String computeHash(@Nullable String username, @Nullable String salt, @Nonnull String password);

    /**
     * Determines if this function is considered insecure.
     * <p>
     * If a password is verified using an outdated function, it is automatically re-hashed
     * using a stronger function during the login process.
     *
     * @return <tt>true</tt> if the function is considered weak, <tt>false</tt> otherwise
     */
    boolean isOutdated();
}
