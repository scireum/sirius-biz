/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.async.CallContext;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

/**
 * Can be embedded into an {@link Event} to record some interesting parts of the current {@link UserContext}.
 */
public class UserData extends Composite {

    /**
     * Stores the ID of the current user.
     */
    @Length(26)
    @NullAllowed
    private String userId;

    /**
     * Stores the ID of the current tenant.
     */
    @Length(26)
    @NullAllowed
    private String tenantId;

    /**
     * Stores the ID of the current scope.
     */
    @NullAllowed
    private String scopeId;

    @BeforeSave
    protected void fill() {
        UserContext ctx = CallContext.getCurrent().get(UserContext.class);
        if (ctx.getScope() != ScopeInfo.DEFAULT_SCOPE) {
            scopeId = ctx.getScope().getScopeId();
        }
        if (ctx.getUser().isLoggedIn()) {
            userId = ctx.getUser().getUserId();
            tenantId = ctx.getUser().getTenantId();
        }
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getScopeId() {
        return scopeId;
    }
}
