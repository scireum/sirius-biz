/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import com.google.common.collect.Streams;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.SAMLHelper;
import sirius.web.security.SAMLResponse;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Permits a login via SAML.
 *
 * @param <I> specifies the effective type of database IDs used by the concrete implementation
 * @param <T> specifies the effective entity type used to represent Tenants
 * @param <U> specifies the effective entity type used to represent UserAccounts
 */
@Register(framework = Tenants.FRAMEWORK_TENANTS)
public class SAMLController<I extends Serializable, T extends BaseEntity<I> & Tenant<I>, U extends BaseEntity<I> & UserAccount<I, T>>
        extends BizController {

    /**
     * Contains the URI prefix shared by all routes related to the SAML login process.
     */
    public static final String SAML_URI_PREFIX = "/saml";

    @Part
    private SAMLHelper saml;

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
    @Routed(SAML_URI_PREFIX + "/login")
    public void samlLogin(WebContext webContext) {
        if (!webContext.isUnsafePOST()) {
            webContext.respondWith().redirectToGet(SAML_URI_PREFIX);
            return;
        }

        SAMLResponse response = saml.parseSAMLResponse(webContext);

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

    private UserInfo tryCreateUser(WebContext webContext, SAMLResponse response) {
        if (Strings.isEmpty(response.getIssuer())) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: No issuer in request!").handle();
        }

        T tenant = findTenant(response);
        checkFingerprint(tenant, response);

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

    private T findTenant(SAMLResponse response) {
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

    private void verifyUser(SAMLResponse response, UserInfo user) {
        U account = user.getUserObject(getUserClass());
        T tenant = account.getTenant().forceFetchValue();

        if (!checkIssuer(tenant, response)) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Issuer mismatch!").handle();
        }
        if (!checkFingerprint(tenant, response)) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Fingerprint mismatch!").handle();
        }

        UserContext userContext = UserContext.get();
        userContext.runAs(userContext.getUserManager().findUserByUserId(account.getUniqueName()), () -> {
            U refreshedAccount = account.getMapper().refreshOrFail(account);
            updateAccount(response, refreshedAccount);
            refreshedAccount.getMapper().update(refreshedAccount);
        });
    }

    private boolean checkFingerprint(T tenant, SAMLResponse response) {
        return isInList(tenant.getTenantData().getSamlFingerprint(), response.getFingerprint());
    }

    private boolean checkIssuer(T tenant, SAMLResponse response) {
        return isInList(tenant.getTenantData().getSamlIssuerName(), response.getIssuer());
    }

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

    private void updateAccount(SAMLResponse response, U account) {
        account.getUserAccountData().getPermissions().getPermissions().clear();
        List<String> mutablePermissions = account.getUserAccountData().getPermissions().getPermissions().modify();
        Streams.concat(response.getAttribute(SAMLResponse.ATTRIBUTE_GROUP).stream(),
                       response.getAttribute(SAMLResponse.ATTRIBUTE_ROLE).stream())
               .filter(Strings::isFilled)
               .flatMap(value -> Arrays.stream(value.split(",")))
               .map(String::trim)
               .filter(Strings::isFilled)
               .filter(role -> roles.contains(role))
               .forEach(mutablePermissions::add);

        if (Strings.isFilled(response.getAttributeValue(SAMLResponse.ATTRIBUTE_GIVEN_NAME))) {
            account.getUserAccountData()
                   .getPerson()
                   .setFirstname(response.getAttributeValue(SAMLResponse.ATTRIBUTE_GIVEN_NAME));
        }
        if (Strings.isFilled(response.getAttributeValue(SAMLResponse.ATTRIBUTE_SURNAME))) {
            account.getUserAccountData()
                   .getPerson()
                   .setLastname(response.getAttributeValue(SAMLResponse.ATTRIBUTE_SURNAME));
        }
        if (Strings.isFilled(response.getAttributeValue(SAMLResponse.ATTRIBUTE_EMAIL_ADDRESS))) {
            account.getUserAccountData().setEmail(response.getAttributeValue(SAMLResponse.ATTRIBUTE_EMAIL_ADDRESS));
        }

        // If a generated password was previously set, force a random password so that the
        // "please change your password" warning goes away.
        if (Strings.isFilled(account.getUserAccountData().getLogin().getGeneratedPassword())) {
            account.getUserAccountData().getLogin().setCleartextPassword(UUID.randomUUID().toString());
        }
    }
}
