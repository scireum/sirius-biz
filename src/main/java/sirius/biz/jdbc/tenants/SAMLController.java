/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.tenants;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Lambdas;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.SAMLHelper;
import sirius.web.security.SAMLResponse;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Permis a login via SAML.
 */
@Register(classes = Controller.class, framework = Tenants.FRAMEWORK_TENANTS)
public class SAMLController extends BizController {

    @Part
    private SAMLHelper saml;

    @ConfigValue("product.wondergemRoot")
    private String wondergemRoot;

    @ConfigValue("security.roles")
    private List<String> roles;

    /**
     * Lists all possible SAML tenants or permits to create a login form for a custom SAML provider.
     *
     * @param ctx the current request
     */
    @Routed("/saml")
    public void saml(WebContext ctx) {
        List<Tenant> tenants = obtainTenantsForSaml(ctx);

        ctx.respondWith().template("/templates/tenants/saml.html.pasta", tenants);
    }

    private List<Tenant> obtainTenantsForSaml(WebContext ctx) {
        // If GET parameters are present, we create a "fake" tenant to provide a custom SAML target.
        // This can be used if several identity providers are available for a single tenant.
        // We can verify several tenants but we can only redirect to a single identity provider.
        // Therefore these parameters can be used to create a SAML request to a custom one.
        if (ctx.hasParameter("issuerName")) {
            Tenant fakeTenant = new Tenant();
            fakeTenant.setSamlRequestIssuerName(ctx.require("issuerName").asString());
            fakeTenant.setSamlIssuerUrl(ctx.require("issuerUrl").asString().replace("javascript:", ""));
            fakeTenant.setSamlIssuerIndex(ctx.get("issuerIndex").asString("0"));

            return Collections.singletonList(fakeTenant);
        }

        return oma.select(Tenant.class).ne(Tenant.SAML_ISSUER_URL, null).orderAsc(Tenant.NAME).queryList();
    }

    /**
     * Processes a SAML response and tries to create or update a user which is then logged in.
     *
     * @param ctx the SAML response as request
     */
    @Routed("/saml/login")
    public void samlLogin(WebContext ctx) {
        if (!ctx.isUnsafePOST()) {
            ctx.respondWith().redirectToGet("/saml");
            return;
        }

        SAMLResponse response = saml.parseSAMLResponse(ctx);

        if (Strings.isEmpty(response.getNameId())) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage("SAML Error: No or empty name ID was given.")
                            .handle();
        }

        TenantUserManager manager = (TenantUserManager) UserContext.getCurrentScope().getUserManager();
        UserInfo user = manager.findUserByName(ctx, response.getNameId());

        if (user == null) {
            user = tryCreateUser(ctx, response);
        } else {
            verifyUser(response, user);
        }

        UserContext userContext = UserContext.get();
        userContext.setCurrentUser(user);
        userContext.attachUserToSession();
        manager.recordLogin(user, true);

        ctx.respondWith().redirectToGet(ctx.get("goto").asString(wondergemRoot));
    }

    private UserInfo tryCreateUser(WebContext ctx, SAMLResponse response) {
        if (Strings.isEmpty(response.getIssuer())) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: No issuer in request!").handle();
        }

        Tenant tenant = findTenant(response);

        checkFingerprint(tenant, response);
        UserAccount account = new UserAccount();
        account.getTenant().setValue(tenant);
        account.getLogin().setUsername(response.getNameId());
        account.getLogin().setCleartextPassword(UUID.randomUUID().toString());
        account.setExternalLoginRequired(true);
        updateAccount(response, account);
        oma.update(account);

        TenantUserManager manager = (TenantUserManager) UserContext.getCurrentScope().getUserManager();
        UserInfo user = manager.findUserByName(ctx, response.getNameId());
        if (user == null) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Failed to create a user").handle();
        }

        return user;
    }

    private Tenant findTenant(SAMLResponse response) {
        for (Tenant tenant : oma.select(Tenant.class)
                                .fields(Tenant.ID, Tenant.SAML_ISSUER_NAME, Tenant.SAML_FINGERPRINT)
                                .ne(Tenant.SAML_ISSUER_NAME, null)
                                .ne(Tenant.SAML_FINGERPRINT, null)
                                .queryList()) {
            if (checkIssuer(tenant, response)) {
                return tenant;
            }
        }

        throw Exceptions.createHandled()
                        .withSystemErrorMessage("SAML Error: No matching tenant found for issuer: %s",
                                                response.getIssuer())
                        .handle();
    }

    private void verifyUser(SAMLResponse response, UserInfo user) {
        UserAccount account = user.getUserObject(UserAccount.class);
        Tenant tenant = account.getTenant().getValue();

        if (!checkIssuer(tenant, response)) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Issuer mismatch!").handle();
        }
        if (!checkFingerprint(tenant, response)) {
            throw Exceptions.createHandled().withSystemErrorMessage("SAML Error: Fingerprint mismatch!").handle();
        }

        account = oma.refreshOrFail(account);
        updateAccount(response, account);
        oma.update(account);
    }

    private boolean checkFingerprint(Tenant tenant, SAMLResponse response) {
        return isInList(tenant.getSamlFingerprint(), response.getFingerprint());
    }

    private boolean checkIssuer(Tenant tenant, SAMLResponse response) {
        return isInList(tenant.getSamlIssuerName(), response.getIssuer());
    }

    private boolean isInList(String values, String valueToCheck) {
        if (Strings.isEmpty(values)) {
            return false;
        }

        for (String value : values.split(",")) {
            if (Strings.isFilled(value) && Strings.areEqual(value, valueToCheck)) {
                return true;
            }
        }

        return false;
    }

    private void updateAccount(SAMLResponse response, UserAccount account) {
        account.getPermissions().getPermissions().clear();
        response.getAttribute(SAMLResponse.ATTRIBUTE_GROUP)
                .stream()
                .filter(Strings::isFilled)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(Strings::isFilled)
                .filter(role -> roles.contains(role))
                .collect(Lambdas.into(account.getPermissions().getPermissions()));

        if (Strings.isFilled(response.getAttributeValue(SAMLResponse.ATTRIBUTE_GIVEN_NAME))) {
            account.getPerson().setFirstname(response.getAttributeValue(SAMLResponse.ATTRIBUTE_GIVEN_NAME));
        }
        if (Strings.isFilled(response.getAttributeValue(SAMLResponse.ATTRIBUTE_SURNAME))) {
            account.getPerson().setLastname(response.getAttributeValue(SAMLResponse.ATTRIBUTE_SURNAME));
        }
        if (Strings.isFilled(response.getAttributeValue(SAMLResponse.ATTRIBUTE_EMAIL_ADDRESS))) {
            account.setEmail(response.getAttributeValue(SAMLResponse.ATTRIBUTE_EMAIL_ADDRESS));
        }
    }
}
