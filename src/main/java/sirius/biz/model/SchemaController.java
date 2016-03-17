/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.mixing.Schema;
import sirius.mixing.schema.SchemaUpdateAction;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.JSONStructuredOutput;

/**
 * Created by aha on 10.03.16.
 */
@Register(classes = Controller.class)
public class SchemaController extends BasicController {

    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @DefaultRoute
    @Routed("/system/schema")
    public void changes(WebContext ctx) {
        ctx.respondWith().template("view/model/schema.html");
    }

    @Part
    private Schema schema;

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
