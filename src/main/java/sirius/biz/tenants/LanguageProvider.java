/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import java.util.Optional;

/**
 * Provides the language which will be set for the {@link sirius.web.security.UserInfo UserInfo} in the {@link TenantUserManager}.
 */
public interface LanguageProvider {

    /**
     * Calculates the language which should be set for the given {@link UserAccount} and/or {@link Tenant}.
     *
     * @param user   the {@link UserAccount} for which the language should be calculated
     * @param tenant the {@link Tenant} for which the language should be calculated
     * @return the language which should be set for the given {@link UserAccount} and/or {@link Tenant}
     */
    Optional<String> getLanguage(UserAccount<?, ?> user, Tenant<?> tenant);
}
