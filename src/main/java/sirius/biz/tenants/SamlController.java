/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.google.common.collect.Streams;
import sirius.biz.saml.SamlHelper;
import sirius.biz.saml.SamlResponse;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Permits a login via SAML.
 *
 * @param <I> specifies the effective type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
@Register(classes = {Controller.class, SamlController.class}, framework = Tenants.FRAMEWORK_TENANTS)
public class SamlController<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends BizController {

    /**
     * Contains the URI prefix shared by all routes related to the SAML login process.
     */
    public static final String SAML_URI_PREFIX = "/saml";

    /**
     * Contains the request parameter which can be used to directly start the SAML login for a tenant.
     */
    public static final String SAML_TENANT_ID_PARAMETER = "samlTenantId";

    @Part
    private SamlHelper saml;

    @ConfigValue("security.roles")
    private List<String> roles;

    /**
     * Lists all possible SAML tenants or permits to create a login form for a custom SAML provider.
     *
     * @param webContext the current request
     */
    @Routed(SAML_URI_PREFIX)
    public void saml(WebContext webContext) {
        List<T> tenants = querySAMLTenants();
        webContext.respondWith().template("/templates/biz/tenants/saml.html.pasta", tenants);
    }

    /**
     * Directly starts the SAML login for the tenant given in {@link #SAML_TENANT_ID_PARAMETER}, if possible.
     *
     * @param webContext  the current request
     * @param originalUrl the URL to return to after the SAML login
     * @return <tt>true</tt> if the request has been handled, <tt>false</tt> otherwise
     */
    public boolean tryStartTenantSamlLogin(WebContext webContext, String originalUrl) {
        String tenantId = webContext.get(SAML_TENANT_ID_PARAMETER).asString();
        if (Strings.isEmpty(tenantId)) {
            return false;
        }

        T tenant = querySAMLTenants().stream()
                                     .filter(candidate -> Strings.areEqual(candidate.getIdAsString(), tenantId))
                                     .filter(this::isSamlLoginConfigured)
                                     .findFirst()
                                     .orElse(null);
        if (tenant == null) {
            return false;
        }

        webContext.respondWith()
                  .template("/templates/biz/tenants/saml.html.pasta", Collections.singletonList(tenant), originalUrl);
        return true;
    }

    private boolean isSamlLoginConfigured(T tenant) {
        TenantData tenantData = tenant.getTenantData();
        return Strings.isFilled(tenantData.getSamlIssuerUrl())
               && Strings.isFilled(tenantData.getSamlRequestIssuerName())
               && Strings.isFilled(tenantData.getSamlIssuerName())
               && Strings.isFilled(tenantData.getSamlFingerprint());
    }

    /**
     * Returns the actual entity class used to represent tenants.
     *
     * @return the entity class representing tenants
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getTenantClass() {
        return (Class<T>) tenants.getTenantClass();
    }

    /**
     * Processes a SAML response and tries to create or update a user which is then logged in.
     *
     * @param webContext the SAML response as request
     */
    @Routed(value = SAML_URI_PREFIX + "/login", skipCsrfValidation = true)
    public void samlLogin(WebContext webContext) {
        if (!webContext.isPostRequest()) {
            webContext.respondWith().redirectToGet(SAML_URI_PREFIX);
            return;
        }

        SamlResponse response = saml.parseSamlResponse(webContext);

        if (Strings.isEmpty(response.getNameId())) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("SAML Error: No or empty name ID was given.")
                            .handle();
        }

        TenantUserManager<?, ?, ?> manager =
                (TenantUserManager<?, ?, ?>) UserContext.getCurrentScope().getUserManager();
        UserInfo user = manager.findUserByName(webContext, response.getNameId());

        if (user == null) {
            user = tryCreateUser(webContext, response);
        } else {
            verifyUser(response, user);
        }
        manager.onExternalLogin(webContext, user);

        // Re-resolve and install the newly created or updated user in the session.
        // This also flushes all caches again, as we updated some internal data within
        // "onExternalLogin" above...
        UserContext userContext = UserContext.get();
        userContext.setCurrentUser(manager.findUserByName(webContext, response.getNameId()));

        webContext.respondWith().template("/templates/biz/tenants/saml-complete.html.pasta", response);
    }

    /**
     * Creates a new user account for the given trusted SAML response.
     *
     * @param webContext the current request
     * @param response   the trusted SAML response
     * @return the newly created user
     */
    private UserInfo tryCreateUser(WebContext webContext, SamlResponse response) {
        if (Strings.isEmpty(response.getIssuer())) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: No issuer in request!").handle();
        }

        T tenant = findTrustedTenant(response);

        try {
            U account = getUserClass().getDeclaredConstructor().newInstance();

            UserContext userContext = UserContext.get();
            userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()), () -> {
                account.getTenant().setValue(tenant);
                account.getUserAccountData().getLogin().setUsername(response.getNameId());
                account.getUserAccountData().getLogin().setCleartextPassword(UUID.randomUUID().toString());
                account.getUserAccountData().setExternalLoginRequired(true);
                updateAccount(response, account);
                account.getMapper().update(account);
            });

            TenantUserManager<?, ?, ?> manager =
                    (TenantUserManager<?, ?, ?>) UserContext.getCurrentScope().getUserManager();
            UserInfo user = manager.findUserByName(webContext, response.getNameId());
            if (user == null) {
                throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Failed to create a user").handle();
            }

            return user;
        } catch (Exception exception) {
            throw Exceptions.handle(BizController.LOG, exception);
        }
    }

    /**
     * Returns the actual entity class used to represent users.
     *
     * @return the entity class representing users
     */
    @SuppressWarnings("unchecked")
    protected Class<U> getUserClass() {
        return (Class<U>) tenants.getUserClass();
    }

    /**
     * Finds the matching SAML tenant and verifies that the signing fingerprint is trusted for it.
     *
     * @param response the SAML response to verify
     * @return the trusted tenant matching the response
     */
    T findTrustedTenant(SamlResponse response) {
        T tenant = findTenant(response);
        verifyFingerprint(tenant, response);
        return tenant;
    }

    /**
     * Finds the tenant whose configured SAML issuer matches the response issuer.
     *
     * @param response the SAML response to inspect
     * @return the tenant matching the response issuer
     */
    private T findTenant(SamlResponse response) {
        for (T tenant : querySAMLTenants()) {
            if (checkIssuer(tenant, response)) {
                return tenant;
            }
        }

        throw Exceptions.createHandled()
                        .withSystemErrorMessage("SAML Error: No matching tenant found for issuer: %s",
                                                response.getIssuer())
                        .handle();
    }

    @SuppressWarnings("unchecked")
    protected List<T> querySAMLTenants() {
        return (List<T>) (Object) mixing.getDescriptor(getTenantClass())
                                        .getMapper()
                                        .select(getTenantClass())
                                        .ne(Tenant.TENANT_DATA.inner(TenantData.SAML_ISSUER_NAME), null)
                                        .ne(Tenant.TENANT_DATA.inner(TenantData.SAML_FINGERPRINT), null)
                                        .orderAsc(Tenant.TENANT_DATA.inner(TenantData.NAME))
                                        .queryList();
    }

    /**
     * Verifies an existing user account against the given SAML response and updates its mapped account data.
     *
     * @param response the trusted SAML response
     * @param user     the existing user to verify and update
     */
    private void verifyUser(SamlResponse response, UserInfo user) {
        U account = user.getUserObject(getUserClass());
        T tenant = account.getTenant().forceFetchValue();

        if (!checkIssuer(tenant, response)) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Issuer mismatch!").handle();
        }
        verifyFingerprint(tenant, response);

        UserContext userContext = UserContext.get();
        userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()), () -> {
            U refreshedAccount = account.getMapper().refreshOrFail(account);
            updateAccount(response, refreshedAccount);
            refreshedAccount.getMapper().update(refreshedAccount);
        });
    }

    /**
     * Verifies that the response fingerprint is trusted for the given tenant.
     *
     * @param tenant   the tenant whose SAML configuration is used
     * @param response the response to verify
     */
    private void verifyFingerprint(T tenant, SamlResponse response) {
        if (!isTrustedFingerprint(tenant.getTenantData().getSamlFingerprint(), response)) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Fingerprint mismatch!").handle();
        }
    }

    /**
     * Determines if the SAML response fingerprint matches one of the configured trusted fingerprints.
     *
     * @param configuredFingerprints a comma-separated list of trusted fingerprints
     * @param response               the response to verify
     * @return <tt>true</tt> if the response fingerprint is trusted, <tt>false</tt> otherwise
     */
    static boolean isTrustedFingerprint(String configuredFingerprints, SamlResponse response) {
        if (Strings.isEmpty(configuredFingerprints)) {
            return false;
        }

        for (String fingerprint : configuredFingerprints.split(",")) {
            if (Strings.isFilled(fingerprint) && response.hasFingerprint(fingerprint)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if the response issuer matches one of the tenant's configured issuers.
     *
     * @param tenant   the tenant whose SAML configuration is used
     * @param response the response to verify
     * @return <tt>true</tt> if the issuer is trusted, <tt>false</tt> otherwise
     */
    private boolean checkIssuer(T tenant, SamlResponse response) {
        return isInList(tenant.getTenantData().getSamlIssuerName(), response.getIssuer());
    }

    /**
     * Determines if a value occurs in a comma-separated, case-insensitive list.
     *
     * @param values       the comma-separated list to inspect
     * @param valueToCheck the value to search for
     * @return <tt>true</tt> if the value is contained in the list, <tt>false</tt> otherwise
     */
    private boolean isInList(String values, String valueToCheck) {
        if (Strings.isEmpty(values)) {
            return false;
        }

        for (String value : values.split(",")) {
            if (Strings.isFilled(value) && value.equalsIgnoreCase(valueToCheck)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Updates account fields and permissions from the attributes contained in the SAML response.
     *
     * @param response the trusted SAML response
     * @param account  the account to update
     */
    private void updateAccount(SamlResponse response, U account) {
        account.getUserAccountData().getPermissions().getPermissions().clear();
        List<String> mutablePermissions = account.getUserAccountData().getPermissions().getPermissions().modify();
        Streams.concat(response.getAttribute(SamlResponse.ATTRIBUTE_GROUP).stream(),
                       response.getAttribute(SamlResponse.ATTRIBUTE_ROLE).stream())
               .filter(Strings::isFilled)
               .flatMap(value -> Arrays.stream(value.split(",")))
               .map(String::trim)
               .filter(Strings::isFilled)
               .filter(role -> roles.contains(role))
               .forEach(mutablePermissions::add);

        if (Strings.isFilled(response.getAttributeValue(SamlResponse.ATTRIBUTE_GIVEN_NAME))) {
            account.getUserAccountData()
                   .getPerson()
                   .setFirstname(response.getAttributeValue(SamlResponse.ATTRIBUTE_GIVEN_NAME));
        }
        if (Strings.isFilled(response.getAttributeValue(SamlResponse.ATTRIBUTE_SURNAME))) {
            account.getUserAccountData()
                   .getPerson()
                   .setLastname(response.getAttributeValue(SamlResponse.ATTRIBUTE_SURNAME));
        }
        if (Strings.isFilled(response.getAttributeValue(SamlResponse.ATTRIBUTE_EMAIL_ADDRESS))) {
            account.getUserAccountData().setEmail(response.getAttributeValue(SamlResponse.ATTRIBUTE_EMAIL_ADDRESS));
        }

        // If a generated password was previously set, force a random password so that the
        // "please change your password" warning goes away.
        if (Strings.isFilled(account.getUserAccountData().getLogin().getGeneratedPassword())) {
            account.getUserAccountData().getLogin().setCleartextPassword(UUID.randomUUID().toString());
        }
    }
}
