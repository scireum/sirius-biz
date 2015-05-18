/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants;

import sirius.biz.model.LoginData;
import sirius.biz.model.PersonData;
import sirius.biz.web.BizController;
import sirius.biz.web.DefaultRoute;
import sirius.biz.web.PageHelper;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.nls.NLS;
import sirius.kernel.xml.StructuredOutput;
import sirius.web.controller.Controller;
import sirius.web.controller.Message;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.http.session.ServerSession;
import sirius.web.mails.MailService;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

import java.util.List;
import java.util.Optional;

/**
 * Created by aha on 07.05.15.
 */
@Register(classes = Controller.class)
public class UserAccountController extends BizController {

    public static final String PERMISSION_MANAGE_USER_ACCOUNTS = "permission-manage-user-accounts";

    @Routed("/user-accounts")
    @DefaultRoute
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void accounts(WebContext ctx) {
        PageHelper<UserAccount> ph = PageHelper.withQuery(oma.select(UserAccount.class)
                                                             .orderAsc(UserAccount.PERSON.inner(PersonData.LASTNAME))
                                                             .orderAsc(UserAccount.PERSON.inner(PersonData.FIRSTNAME)));
        ph.forCurrentTenant();
        ph.withContext(ctx);
        ph.withSearchFields(UserAccount.EMAIL,
                            UserAccount.LOGIN.inner(LoginData.USERNAME),
                            UserAccount.PERSON.inner(PersonData.FIRSTNAME),
                            UserAccount.PERSON.inner(PersonData.LASTNAME));

        ctx.respondWith().template("view/tenants/user-accounts.html", ph.asPage());
    }

    @Routed("/user-account/:1")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void account(WebContext ctx, String accountId) {
        UserAccount userAccount = findForTenant(UserAccount.class, accountId);
        if (ctx.isPOST()) {
            try {
                boolean wasNew = userAccount.isNew();
                if (wasNew) {
                    userAccount.getTenant().setValue(tenants.getRequiredTenant());
                }
                load(ctx,
                     userAccount,
                     UserAccount.EMAIL,
                     UserAccount.PERSON.inner(PersonData.SALUTATION),
                     UserAccount.PERSON.inner(PersonData.TITLE),
                     UserAccount.PERSON.inner(PersonData.FIRSTNAME),
                     UserAccount.PERSON.inner(PersonData.LASTNAME),
                     UserAccount.LOGIN.inner(LoginData.USERNAME),
                     UserAccount.LOGIN.inner(LoginData.ACCOUNT_LOCKED));
                oma.update(userAccount);
                showSavedMessage();
                if (wasNew) {
                    ctx.respondWith()
                       .redirectTemporarily(WebContext.getContextPrefix() + "/user-account/" + userAccount.getId());
                    return;
                }
            } catch (Throwable e) {
                UserContext.handle(e);
            }
        }
        ctx.respondWith().template("view/tenants/user-account-details.html", userAccount);
    }

    @Routed("/user-account/:1/password")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void password(final WebContext ctx, String id) {
        UserAccount userAccount = findForTenant(UserAccount.class, id);
        assertNotNew(userAccount);

        if (ctx.isPOST()) {
            try {
                String password = ctx.get("password").asString();
                String confirmation = ctx.get("confirmation").asString();
                if (Strings.isEmpty(password) || password.length() < userAccount.getMinPasswordLength()) {
                    UserContext.setFieldError("password", null);
                    throw Exceptions.createHandled()
                                    .withNLSKey("Model.password.minLengthError")
                                    .set("minChars", userAccount.getMinPasswordLength())
                                    .handle();
                }
                if (!Strings.areEqual(password, confirmation)) {
                    UserContext.setFieldError("confirmation", null);
                    throw Exceptions.createHandled().withNLSKey("Model.password.confirmationMismatch").handle();
                }
                userAccount.getLogin().setCleartextPassword(password);
                oma.update(userAccount);
                showSavedMessage();
                accounts(ctx);
                return;
            } catch (Throwable e) {
                UserContext.handle(e);
            }
        }
        ctx.respondWith().template("view/tenants/user-account-password.html", userAccount);
    }

    @Part
    private MailService mails;

    @Routed("/user-account/:1/generate-password")
    @LoginRequired
    @Permission(PERMISSION_MANAGE_USER_ACCOUNTS)
    public void generatePassword(final WebContext ctx, String id) {
        UserAccount userAccount = findForTenant(UserAccount.class, id);
        assertNotNew(userAccount);

        userAccount.getLogin().setGeneratedPassword(Strings.generatePassword());
        oma.update(userAccount);
        showSavedMessage();
        if (Strings.isFilled(userAccount.getEmail())) {
            mails.createEmail()
                 .useMailTemplate("user-account-password",
                                  Context.create()
                                         .set("password", userAccount.getLogin().getGeneratedPassword())
                                         .set("name", userAccount.getPerson().getAddressableName())
                                         .set("username", userAccount.getLogin().getUsername())
                                         .set("url", getBaseUrl()))
                 .to(userAccount.getEmail(), userAccount.getPerson().toString())
                 .send();
        }

        accounts(ctx);
    }

    @Routed("/forgotPassword")
    public void forgotPassword(final WebContext ctx) {
        try {
            List<UserAccount> accounts = oma.select(UserAccount.class)
                                      .eq(UserAccount.EMAIL, ctx.get("email").asString())
                                      .limit(2)
                                      .queryList();
            if (accounts.isEmpty()) {
                throw Exceptions.createHandled().withNLSKey("UserAccountController.noUserFoundForEmail").handle();
            } else if (accounts.size() > 1) {
                throw Exceptions.createHandled().withNLSKey("UserAccountController.tooManyUsersFoundForEmail").handle();
            } else {
                UserAccount account = accounts.get(0);
                if (account.getLogin().isAccountLocked()) {
                    throw Exceptions.createHandled().withNLSKey("LoginData.accountIsLocked").handle();
                }
                account.getLogin().setGeneratedPassword(Strings.generatePassword());
                oma.update(account);

                if (Strings.isFilled(account.getEmail())) {
                    mails.createEmail()
                         .useMailTemplate("user-account-password",
                                          Context.create()
                                                 .set("reason",
                                                      NLS.fmtr("UserAccountController.forgotPassword.reason")
                                                         .set("ip", ctx.getRemoteIP().toString())
                                                         .format())
                                                 .set("password", account.getLogin().getGeneratedPassword())
                                                 .set("name", account.getPerson().getAddressableName())
                                                 .set("username", account.getLogin().getUsername())
                                                 .set("url", getBaseUrl())).to(account.getEmail(), account.getPerson().toString())
                         .send();
                }
            }

            StructuredOutput out = ctx.respondWith().json();
            out.beginResult();
            out.property("success", true);
            out.endResult();
        } catch (HandledException t) {
            StructuredOutput out = ctx.respondWith().json();
            out.beginResult();
            out.property("error", t.getMessage());
            out.endResult();
        } catch (Throwable t) {
            StructuredOutput out = ctx.respondWith().json();
            out.beginResult();
            out.property("error", Exceptions.handle(t));
            out.endResult();
        }
    }

    @LoginRequired
    @Routed("/user-account/:1/delete")
    public void deleteAdmin(final WebContext ctx, String id) {
        Optional<UserAccount> account = tryFindForTenant(UserAccount.class, id);
        if (account.isPresent()) {
            oma.delete(account.get());
            showDeletedMessage();
        }
        accounts(ctx);
    }

    @Routed("/logout")
    public void logout(WebContext ctx) {
        ctx.clearSession();
        ctx.getServerSession(false).ifPresent(ServerSession::invalidate);
        ctx.respondWith().redirectTemporarily("/");
    }


}
