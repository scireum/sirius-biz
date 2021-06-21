/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.web.BasePageHelper;
import sirius.biz.web.BizController;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.Formatter;
import sirius.web.controller.AutocompleteHelper;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.LoginRequired;
import sirius.web.security.Permission;

import java.io.Serializable;

/**
 * Provides the database independent controller for code lists.
 * <p>
 * Some specific behaviour which depends on the underlying database has to be implemented by a concrete subclass.
 *
 * @param <I> the type of database IDs used by the concrete implementation
 * @param <L> the effective entity type used to represent code lists
 * @param <E> the effective entity type used to represent code list entries
 */
public abstract class CodeListController<I extends Serializable, L extends BaseEntity<I> & CodeList, E extends BaseEntity<I> & CodeListEntry<I, L>>
        extends BizController {

    /**
     * Contains the name of the permission required to manage code lists.
     */
    public static final String PERMISSION_MANAGE_CODELISTS = "permission-manage-code-lists";

    private static final String PARAM_CODE = "code";

    @Part
    private CodeLists<I, L, E> codeLists;

    /**
     * Provides a list of all code lists.
     *
     * @param ctx the current request
     */
    @DefaultRoute
    @LoginRequired
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-lists")
    public void codeLists(WebContext ctx) {
        BasePageHelper<L, ?, ?, ?> pageHelper = getListsAsPage();
        pageHelper.withContext(ctx);
        applyCodeListSearchFields(pageHelper);
        ctx.respondWith().template("/templates/biz/codelists/code-lists.html.pasta", pageHelper.asPage());
    }

    protected abstract BasePageHelper<L, ?, ?, ?> getListsAsPage();

    /**
     * Provides an editor for a code list.
     *
     * @param ctx        the current request
     * @param codeListId the id of the code list
     */
    @LoginRequired
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
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/details")
    public void codeListDetails(WebContext ctx, String codeListId) {
        codeListHandler(ctx, codeListId, true);
    }

    private void codeListHandler(WebContext ctx, String codeListId, boolean forceDetails) {
        L codeList = findForTenant(codeLists.getListType(), codeListId);

        if (codeList.isNew() || forceDetails) {
            boolean requestHandled =
                    prepareSave(ctx).withAfterCreateURI("/code-list/${id}/details").saveEntity(codeList);
            if (!requestHandled) {
                ctx.respondWith().template("/templates/biz/codelists/code-list-details.html.pasta", codeList);
            }
        } else {
            renderCodeList(ctx, codeList);
        }
    }

    private void renderCodeList(WebContext ctx, L codeList) {
        BasePageHelper<E, ?, ?, ?> pageHelper = getEntriesAsPage(codeList);
        pageHelper.withContext(ctx);
        applyCodeListEntrySearchFields(pageHelper);
        ctx.respondWith()
           .template("/templates/biz/codelists/code-list-entries.html.pasta", codeList, pageHelper.asPage());
    }

    protected abstract BasePageHelper<E, ?, ?, ?> getEntriesAsPage(L codeList);

    protected abstract void applyCodeListSearchFields(BasePageHelper<L, ?, ?, ?> pageHelper);

    protected abstract void applyCodeListEntrySearchFields(BasePageHelper<E, ?, ?, ?> pageHelper);

    /**
     * Provides an editor for a code list entry.
     *
     * @param webContext        the current request
     * @param codeListId the code list of the entry
     */
    @LoginRequired
    @Permission(PERMISSION_MANAGE_CODELISTS)
    @Routed("/code-list/:1/entry")
    public void codeListEntry(WebContext webContext, String codeListId) {
        L codeList = findForTenant(codeLists.getListType(), codeListId);
        assertNotNew(codeList);

        String code =
                webContext.get(CodeListEntry.CODE_LIST_ENTRY_DATA.inner(CodeListEntryData.CODE).toString()).asString();
        E codeListEntry = findOrCreateEntry(codeList, code);
        setOrVerify(codeListEntry, codeListEntry.getCodeList(), codeList);

        boolean requestHandled =
                prepareSave(webContext).withAfterSaveURI("/code-list/" + codeListId).saveEntity(codeListEntry);
        if (!requestHandled) {
            validate(codeListEntry);
            webContext.respondWith()
                      .template("/templates/biz/codelists/code-list-entry.html.pasta", codeList, codeListEntry);
        }
    }

    protected abstract E findOrCreateEntry(L codeList, String code);

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
        L cl = tryFindForTenant(codeLists.getListType(), codeListId).orElse(null);
        if (cl != null) {
            cl.getMapper().delete(cl);
            showDeletedMessage();
        }

        ctx.respondWith().redirectToGet("/code-lists");
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
        L cl = findForTenant(codeLists.getListType(), codeListId);
        assertNotNew(cl);

        E cle = find(codeLists.getEntryType(), entryId);
        if (cle != null && cle.getCodeList().is(cl)) {
            cle.getMapper().delete(cle);
            showDeletedMessage();
        }

        renderCodeList(ctx, cl);
    }

    /**
     * Autocompletion for codelists.
     *
     * @param webContext the current request
     */
    @LoginRequired
    @Routed("/code-lists/autocomplete")
    public void codeListsAutocomplete(final WebContext webContext) {
        AutocompleteHelper.handle(webContext, (query, result) -> {
            BasePageHelper<L, ?, ?, ?> pageHelper = getListsAsPage();
            pageHelper.withContext(webContext);

            pageHelper.asPage().getItems().forEach(codeList -> {
                CodeListData codeListData = codeList.getCodeListData();
                String label = Formatter.create("${code}[ (${name})]")
                                        .set(PARAM_CODE, codeListData.getCode())
                                        .set("name", codeListData.getName())
                                        .smartFormat();
                result.accept(AutocompleteHelper.suggest(codeListData.getCode())
                                                .withFieldLabel(label)
                                                .withCompletionDescription(codeListData.getDescription()));
            });
        });
    }
}
