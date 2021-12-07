/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Strings;
import sirius.web.security.ScopeInfo;
import sirius.web.security.UserContext;

/**
 * Can be embedded into an {@link Event} to record some interesting parts of the current {@link UserContext}.
 */
public class UserData extends Composite {

    /**
     * Stores the ID of the current user.
     */
    public static final Mapping USER_ID = Mapping.named("userId");
    @NullAllowed
    private String userId;

    /**
     * Stores the ID of the current tenant.
     */
    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    @NullAllowed
    private String tenantId;

    /**
     * Stores the ID of the current scope.
     */
    public static final Mapping SCOPE_ID = Mapping.named("scopeId");
    @NullAllowed
    private String scopeId;

    @BeforeSave
    protected void fill() {
        UserContext ctx = CallContext.getCurrent().get(UserContext.class);
        if (ctx.getScope() != ScopeInfo.DEFAULT_SCOPE && Strings.isEmpty(scopeId)) {
            scopeId = ctx.getScope().getScopeId();
        }
        if (ctx.isUserPresent() && ctx.getUser().isLoggedIn() && Strings.isEmpty(userId)) {
            userId = ctx.getUser().getUserId();
            tenantId = ctx.getUser().getTenantId();
        }
    }

    /**
     * Specifies the user id to record.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the current user (as indicated by the {@link UserContext}).
     *
     * @param userId the user id to store
     */
    public void setCustomUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Specifies the tenant id to record.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the current tenant (as indicated by the {@link UserContext}).
     *
     * @param tenantId the tenant id to store
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Specifies the scope id to record.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the current scope (as indicated by the {@link UserContext}).
     *
     * @param scopeId the scope id to store
     */
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
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
