/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.tenants.TenantUserManager;
import sirius.db.mixing.Schema;
import sirius.db.mixing.schema.SchemaUpdateAction;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.JSONStructuredOutput;

/**
 * Provides the management GUI for schema changes as indicated by the {@link Schema} of mixing.
 */
@Register(classes = Controller.class)
public class SchemaController extends BasicController {

    @Part
    private Schema schema;

    /**
     * Renders the schema list view.
     *
     * @param ctx the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @DefaultRoute
    @Routed("/system/schema")
    public void changes(WebContext ctx) {
        ctx.respondWith().template("templates/model/schema.html.pasta");
    }

    /**
     * Lists all required changes as JSON
     *
     * @param ctx the current request
     * @param out the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Routed(value = "/system/schema/api/list", jsonCall = true)
    public void changesList(WebContext ctx, JSONStructuredOutput out) {
        schema.computeRequiredSchemaChanges();
        out.beginArray("changes");
        for (SchemaUpdateAction a : schema.getSchemaUpdateActions()) {
            out.beginObject("change");
            out.property("id", a.getId());
            out.property("reason", a.getReason());
            out.property("sql", a.getSql());
            out.property("executed", a.isExecuted());
            out.property("failed", a.isFailed());
            out.property("error", Value.of(a.getError()).asString());
            out.property("datalossPossible", a.isDataLossPossible());
            out.endObject();
        }
        out.endArray();
    }

    /**
     * Executes the given schema change.
     *
     * @param ctx the current request
     * @param out the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Routed(value = "/system/schema/api/execute", jsonCall = true)
    public void execute(WebContext ctx, JSONStructuredOutput out) {
        SchemaUpdateAction result = schema.executeSchemaUpdateAction(ctx.get("id").asString());

        if (result != null) {
            out.property("errorMessage", Value.of(result.getError()).asString());
        } else {
            out.property("errorMessage", NLS.get("SchemaController.unknownChange"));
        }
    }
}
