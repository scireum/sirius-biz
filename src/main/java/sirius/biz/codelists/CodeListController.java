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

import java.util.Optional;

/**
 * Provides an editor GUI for managing code lists.
 */
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
        PageHelper<CodeList> ph = PageHelper.withQuery(oma.select(CodeList.class).orderAsc(CodeList.CODE));
        ph.withContext(ctx);
        ph.withSearchFields(CodeList.CODE, CodeList.NAME, CodeList.DESCRIPTION);
        ctx.respondWith().template("templates/codelists/code-lists.html.pasta", ph.asPage());
    }

    /**
     * Provides an editor for a code list.
     *
     * @param ctx        the current request
     * @param codeListId the id of the code list
     */
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1")
    public void codeList(WebContext ctx, String codeListId) {
        codeListHandler(ctx, codeListId, false);
    }

    /**
     * Provides an editor for a code list.
     *
     * @param ctx        the current request
     * @param codeListId the id of the code list
     */
    @LoginRequired
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/details")
    public void codeListDetails(WebContext ctx, String codeListId) {
        codeListHandler(ctx, codeListId, true);
    }

    private void codeListHandler(WebContext ctx, String codeListId, boolean forceDetails) {
        CodeList cl = findForTenant(CodeList.class, codeListId);

        if (cl.isNew() || forceDetails) {
            boolean requestHandled =
                    prepareSave(ctx).withAfterCreateURI("/code-list/${id}/details").saveEntity(cl);
            if (!requestHandled) {
                ctx.respondWith().template("templates/codelists/code-list-details.html.pasta", cl);
            }
        } else {
            renderCodeList(ctx, cl);
        }
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
        ctx.respondWith().template("templates/codelists/code-list-entries.html.pasta", cl, ph.asPage());
    }

    /**
     * Provides an editor for a code list entry.
     *
     * @param ctx        the current request
     * @param codeListId the code list of the entry
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
     * @param ctx        the current request
     * @param codeListId the code list to delete
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
     * @param ctx        the current request
     * @param codeListId the code list of the entry
     * @param entryId    the entry to delete
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
