/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
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

import javax.annotation.Nullable;
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
        return Strings.areAllFilled(tenantData.getSamlIssuerUrl(),
                                    tenantData.getSamlRequestIssuerName(),
                                    tenantData.getSamlIssuerName(),
                                    tenantData.getSamlFingerprint());
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
     * Finds the tenant for which the given SAML response was issued and which trusts its signing fingerprint.
     * <p>
     * Several tenants may share the same SAML provider (e.g. multiple enterprise applications within the same
     * Entra ID directory) and therefore the same issuer. Each of these applications signs with its own certificate,
     * so the fingerprint is used to pick the matching tenant. If even the fingerprint is shared, the audience of the
     * response is compared against the {@link TenantData#getSamlRequestIssuerName() request issuer} of each tenant.
     *
     * @param response the SAML response to verify
     * @return the trusted tenant matching the response
     */
    T findTrustedTenant(SamlResponse response) {
        List<T> issuerMatches = findTenantsByIssuer(response);
        if (issuerMatches.isEmpty()) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("SAML Error: No matching tenant found for issuer: %s",
                                                    response.getIssuer())
                            .handle();
        }

        T trustedTenant = selectTrustedTenant(issuerMatches, response);
        if (trustedTenant == null) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Fingerprint mismatch!").handle();
        }

        logAudienceMismatch(trustedTenant, response);
        return trustedTenant;
    }

    /**
     * Finds all tenants whose configured SAML issuer matches the response issuer.
     *
     * @param response the SAML response to inspect
     * @return all tenants matching the response issuer, ordered by name
     */
    private List<T> findTenantsByIssuer(SamlResponse response) {
        return querySAMLTenants().stream().filter(tenant -> checkIssuer(tenant, response)).toList();
    }

    /**
     * Selects the tenant which trusts the signing fingerprint of the response from the given candidates.
     *
     * @param candidates the tenants whose issuer matches the response, ordered by name
     * @param response   the SAML response to verify
     * @return the tenant matching the response or <tt>null</tt> if no candidate trusts the signing fingerprint
     */
    private T selectTrustedTenant(List<T> candidates, SamlResponse response) {
        List<T> trustedTenants = candidates.stream()
                                           .filter(tenant -> isTrustedFingerprint(tenant.getTenantData()
                                                                                        .getSamlFingerprint(),
                                                                                  response))
                                           .toList();
        if (trustedTenants.isEmpty()) {
            return null;
        }
        if (trustedTenants.size() == 1) {
            return trustedTenants.getFirst();
        }

        // If several tenants also share the signing certificate, we prefer the one whose request issuer matches the
        // audience. If the audience is missing or doesn't match, we stick to the first tenant (by name), as this has
        // always been the behaviour for such setups...
        return trustedTenants.stream()
                             .filter(tenant -> checkAudience(tenant, response))
                             .findFirst()
                             .orElse(trustedTenants.getFirst());
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
            logTenantMismatch(user, tenant, response);
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Issuer mismatch!").handle();
        }
        if (!isTrustedFingerprint(tenant.getTenantData().getSamlFingerprint(), response)) {
            logTenantMismatch(user, tenant, response);
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Fingerprint mismatch!").handle();
        }
        logAudienceMismatch(tenant, response);

        UserContext userContext = UserContext.get();
        userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()), () -> {
            U refreshedAccount = account.getMapper().refreshOrFail(account);
            updateAccount(response, refreshedAccount);
            refreshedAccount.getMapper().update(refreshedAccount);
        });
    }

    /**
     * Logs a hint if an existing user is rejected, but the response would have been trusted by another tenant.
     * <p>
     * This either means that the user has been created within the wrong tenant (older versions picked the first
     * tenant with a matching issuer without verifying its fingerprint) or that the same user name is used by several
     * tenants (as user names are unique across all tenants, a user can only belong to one of them). Without this hint,
     * such a setup is hard to diagnose, as the identity provider reports a successful login.
     *
     * @param user     the existing user which is rejected
     * @param tenant   the tenant the user belongs to
     * @param response the rejected response
     */
    private void logTenantMismatch(UserInfo user, T tenant, SamlResponse response) {
        T expectedTenant = findOtherTrustedTenant(tenant, response);
        if (expectedTenant == null) {
            return;
        }

        LOG.WARN("SAML login of user %s was rejected: The user belongs to tenant %s (%s), but the response "
                 + "(issuer: %s, audience: %s) was issued for tenant %s (%s). "
                 + "The user was probably created within the wrong tenant or the same name is used by several "
                 + "tenants.",
                 user.getUserName(),
                 tenant.getTenantData().getName(),
                 tenant.getIdAsString(),
                 response.getIssuer(),
                 response.getAudience(),
                 expectedTenant.getTenantData().getName(),
                 expectedTenant.getIdAsString());
    }

    /**
     * Determines the tenant the response would have been selected for, if this isn't the given tenant.
     *
     * @param tenant   the tenant the user belongs to
     * @param response the response to inspect
     * @return the tenant which would have been selected for the response or <tt>null</tt> if no tenant trusts the
     * response or if it is the given tenant anyway
     */
    @Nullable
    T findOtherTrustedTenant(T tenant, SamlResponse response) {
        T expectedTenant = selectTrustedTenant(findTenantsByIssuer(response), response);
        if (expectedTenant == null || Strings.areEqual(expectedTenant.getIdAsString(), tenant.getIdAsString())) {
            return null;
        }

        return expectedTenant;
    }

    /**
     * Logs a warning if the response carries an audience which doesn't match the request issuer of the given tenant.
     * <p>
     * The audience isn't enforced (yet), therefore such a response is still accepted. However, this most probably
     * hints at a misconfiguration (e.g. a typo or a case mismatch in the request issuer), which would otherwise go
     * unnoticed, as the tenant is then selected via the fallback.
     *
     * @param tenant   the tenant which accepted the response
     * @param response the accepted response
     */
    private void logAudienceMismatch(T tenant, SamlResponse response) {
        if (Strings.isEmpty(response.getAudience()) || checkAudience(tenant, response)) {
            return;
        }

        LOG.WARN("SAML response for tenant %s (%s) has the audience '%s', which doesn't match the configured request "
                 + "issuer '%s'. Please check the SAML configuration.",
                 tenant.getTenantData().getName(),
                 tenant.getIdAsString(),
                 response.getAudience(),
                 tenant.getTenantData().getSamlRequestIssuerName());
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
     * Determines if the response audience matches the tenant's request issuer.
     * <p>
     * In contrast to {@link #checkIssuer(BaseEntity, SamlResponse)}, this comparison is case-sensitive, as the
     * audience is a URI (or plain identifier) which has to match exactly according to the SAML specification.
     *
     * @param tenant   the tenant whose SAML configuration is used
     * @param response the response to verify
     * @return <tt>true</tt> if the audience matches the request issuer, <tt>false</tt> otherwise
     */
    private boolean checkAudience(T tenant, SamlResponse response) {
        String audience = response.getAudience();
        return Strings.isFilled(audience) && Strings.areEqual(tenant.getTenantData().getSamlRequestIssuerName(),
                                                              audience);
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
