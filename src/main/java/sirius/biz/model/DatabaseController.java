/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.model;

import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.Schema;
import sirius.db.mixing.schema.SchemaUpdateAction;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.JSONStructuredOutput;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the management GUI for database related activities.
 */
@Register(classes = Controller.class)
public class DatabaseController extends BasicController {

    public static final int DEFAULT_LIMIT = 1000;
    @Part
    private Schema schema;

    @Part
    private Databases databases;

    @ConfigValue("mixing.database")
    private String defaultDatabase;

    /**
     * Renders the UI to execute SQL queries.
     *
     * @param ctx the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Routed("/system/sql")
    public void sql(WebContext ctx) {
        ctx.respondWith().template("templates/model/sql.html.pasta");
    }

    /**
     * Executes the given sql query.
     *
     * @param ctx the current request
     * @param out the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
    @Routed(value = "/system/sql/api/execute", jsonCall = true)
    public void executeQuery(WebContext ctx, JSONStructuredOutput out) throws SQLException {
        Watch w = Watch.start();
        String database = ctx.get("db").asString(defaultDatabase);
        Database db = databases.get(database);
        String query = ctx.get("query").asString();
        SQLQuery qry = db.createQuery(query);

        if (isDDSStatement(query)) {
            // To prevent accidential damage, we try to filter DDS queries (modifying the database structure) and
            // only permit them against our system database.
            if (!Strings.areEqual(database, defaultDatabase)) {
                throw Exceptions.createHandled()
                                .withSystemErrorMessage(
                                        "Cannot execute a DDS query against this database. This can be only done for '%s'",
                                        database)
                                .handle();
            }

            out.property("rowModified", qry.executeUpdate());
        }

        if (isModifyStatement(query)) {
            out.property("rowModified", qry.executeUpdate());
        } else {
            AtomicBoolean arrayStarted = new AtomicBoolean();
            qry.iterateAll(r -> outputRow(out, arrayStarted, r), new Limit(0, ctx.get("limit").asInt(DEFAULT_LIMIT)));
            if (arrayStarted.get()) {
                out.endArray();
            }
        }
        out.property("duration", w.duration());
    }

    private boolean isModifyStatement(String query) {
        String lowerCaseQuery = query.toLowerCase().trim();
        return lowerCaseQuery.startsWith("update") || lowerCaseQuery.startsWith("insert") || lowerCaseQuery.startsWith(
                "delete");
    }

    private boolean isDDSStatement(String qry) {
        String lowerCaseQuery = qry.toLowerCase().trim();
        return lowerCaseQuery.startsWith("alter") || lowerCaseQuery.startsWith("drop") || lowerCaseQuery.startsWith(
                "create");
    }

    private void outputRow(JSONStructuredOutput out, AtomicBoolean arrayStarted, Row row) {
        if (!arrayStarted.get()) {
            arrayStarted.set(true);
            out.beginArray("columns");
            for (Tuple<String, Object> col : row.getFieldsList()) {
                out.property("column", col.getFirst());
            }
            out.endArray();
            out.beginArray("rows");
        }
        out.beginArray("row");
        for (Tuple<String, Object> col : row.getFieldsList()) {
            out.property("column", col.getSecond());
        }
        out.endArray();
    }

    /**
     * Renders the schema list view.
     *
     * @param ctx the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_TENANT)
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
            out.property("errorMessage", NLS.get("DatabaseController.unknownChange"));
        }
    }
}
