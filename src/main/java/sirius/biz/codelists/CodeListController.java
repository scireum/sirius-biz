/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.biz.web.PageHelper;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

import java.util.Optional;



@Framework("code-lists")
@Register(classes = Controller.class)
public class CodeListController extends BizController {

    private static final String PERMISSION_MANAGE_CODELISTS = "permission-manage-code-lists";

    /**
     * Provides a list of all code lists.
     *
     * @param ctx the current request
     */
    @DefaultRoute
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-lists")
    public void codeLists(WebContext ctx) {
        PageHelper<CodeList> ph =
                PageHelper.withQuery(oma.select(CodeList.class).orderAsc(CodeList.CODE));
        ph.withContext(ctx);
        ph.withSearchFields(CodeList.CODE, CodeList.NAME, CodeList.DESCRIPTION);
        ctx.respondWith().template("view/codelists/code-lists.html", ph.asPage());
    }

    /**
     * Provides an editor for a code list.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1")
    public void codeList(WebContext ctx, String codeListId) {
        codeListHandler(ctx, codeListId, false);
    }

    private void codeListHandler(WebContext ctx, String codeListId, boolean forceDetails) {
        CodeList cl = findForTenant(CodeList.class, codeListId);
        if (ctx.isPOST()) {
            try {
                boolean wasNew = cl.isNew();
                load(ctx, cl);
                oma.update(cl);
                showSavedMessage();
                if (wasNew) {
                    ctx.respondWith().redirectTemporarily(WebContext.getContextPrefix() + "/code-list/" + cl.getId());
                    return;
                }
            } catch (Throwable e) {
                UserContext.handle(e);
            }
        }
        if (cl.isNew() || forceDetails) {
            ctx.respondWith().template("view/codelists/code-list-details.html", cl);
        } else {
            renderCodeList(ctx, cl);
        }
    }

    /**
     * Provides an editor for a code list.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/details")
    public void codeListDetails(WebContext ctx, String codeListId) {
        codeListHandler(ctx, codeListId, true);
    }

    private void renderCodeList(WebContext ctx, CodeList cl) {
        PageHelper<CodeListEntry> ph = PageHelper.withQuery(oma.select(CodeListEntry.class)
                                                               .eq(CodeListEntry.CODE_LIST, cl)
                                                               .orderAsc(CodeListEntry.CODE_LIST));
        ph.withContext(ctx);
        ph.withSearchFields(CodeListEntry.CODE,
                            CodeListEntry.VALUE,
                            CodeListEntry.ADDITIONAL_VALUE,
                            CodeListEntry.DESCRIPTION);
        ctx.respondWith().template("view/codelists/code-list-entries.html", cl, ph.asPage());
    }

    /**
     * Provides an editor for a code list entry.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/entry")
    public void codeListEntry(WebContext ctx, String codeListId) {
        CodeList cl = findForTenant(CodeList.class, codeListId);
        assertNotNew(cl);
        if (ctx.isPOST()) {
            if (ctx.get("code").isFilled()) {
                String code = ctx.get("code").asString();
                CodeListEntry cle = oma.select(CodeListEntry.class)
                                       .eq(CodeListEntry.CODE_LIST, cl)
                                       .eq(CodeListEntry.CODE, code)
                                       .queryFirst();
                if (cle == null) {
                    cle = new CodeListEntry();
                    cle.getCodeList().setValue(cl);
                    cle.setCode(code);
                }
                cle.setPriority(ctx.get("priority").asInt(Priorized.DEFAULT_PRIORITY));
                cle.setValue(ctx.get("value").isEmptyString() ? null : ctx.get("value").asString());
                cle.setAdditionalValue(ctx.get("additionalValue").isEmptyString() ?
                                       null :
                                       ctx.get("additionalValue").asString());
                cle.setDescription(ctx.get("description").isEmptyString() ? null : ctx.get("description").asString());
                oma.update(cle);
                showSavedMessage();
            }
        }
        renderCodeList(ctx, cl);
    }

    /**
     * Deletes a code list.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/delete")
    public void deleteCodeList(WebContext ctx, String codeListId) {
        Optional<CodeList> cl = tryFindForTenant(CodeList.class, codeListId);
        if (cl.isPresent()) {
            oma.delete(cl.get());
            showDeletedMessage();
        }
        codeLists(ctx);
    }

    /**
     * Deletes a code list entry.
     *
     * @param ctx the current request
     */
    @LoginRequired
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/delete-entry/:2")
    public void deleteCodeListEntry(WebContext ctx, String codeListId, String entryId) {
        CodeList cl = findForTenant(CodeList.class, codeListId);
        assertNotNew(cl);
        Optional<CodeListEntry> cle = oma.find(CodeListEntry.class, entryId);
        if (cle.isPresent()) {
            if (cle.get().getCodeList().is(cl)) {
                oma.delete(cle.get());
                showDeletedMessage();
            }
        }
        renderCodeList(ctx, cl);
    }
}
