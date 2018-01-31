/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.web.BizController;
import sirius.db.mixing.constraints.FieldOperator;
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
import java.util.List;
import java.util.UUID;

@Register(classes = Controller.class)
public class SAMLController extends BizController {

    @Part
    private SAMLHelper saml;

    @ConfigValue("product.wondergemRoot")
    private String wondergemRoot;

    @ConfigValue("security.roles")
    private List<String> roles;

    @Routed("/saml")
    public void saml(WebContext ctx) {
        List<Tenant> tenants = oma.select(Tenant.class)
                                  .where(FieldOperator.on(Tenant.SAML_ISSUER_URL).notEqual(null))
                                  .orderAsc(Tenant.NAME)
                                  .queryList();

        ctx.respondWith().template("/templates/tenants/saml.html.pasta", tenants);
    }

    @Routed("/saml/login")
    public void samlLogin(WebContext ctx) {
        if (!ctx.isPOST()) {
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
                                .where(FieldOperator.on(Tenant.SAML_ISSUER_NAME).notEqual(null))
                                .where(FieldOperator.on(Tenant.SAML_FINGERPRINT).notEqual(null))
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

    private boolean checkFingerprint(Tenant tenant, SAMLResponse response) {
        String fingerprints = tenant.getSamlFingerprint();
        if (Strings.isEmpty(fingerprints)) {
            return false;
        }

        for (String fingerprint : fingerprints.split(",")) {
            if (Strings.isFilled(fingerprint) && Strings.areEqual(response.getFingerprint(), fingerprint)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkIssuer(Tenant tenant, SAMLResponse response) {
        String samlIssuerNames = tenant.getSamlIssuerName();
        if (Strings.isEmpty(samlIssuerNames)) {
            return false;
        }

        for (String issuer : samlIssuerNames.split(",")) {
            if (Strings.isFilled(issuer) && Strings.areEqual(issuer, response.getIssuer())) {
                return true;
            }
        }

        return false;
    }
}
