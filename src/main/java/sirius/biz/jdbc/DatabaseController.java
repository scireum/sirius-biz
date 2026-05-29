/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.biz.jobs.Jobs;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.db.jdbc.schema.Schema;
import sirius.db.jdbc.schema.SchemaUpdateAction;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Monoflop;
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
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Provides the management GUI for database related activities.
 */
@Register
public class DatabaseController extends BasicController {

    /**
     * Contains the default limit to prevent accidents when querying large tables
     */
    private static final int DEFAULT_LIMIT = 1000;

    private static final String PARAM_DATABASE = "database";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_EXPORT_QUERY = "exportQuery";
    private static final String PARAM_EXPORT_DATABASE = "exportDatabase";
    private static final Set<String> DDL_KEYWORDS = Set.of("alter", "drop", "create", "truncate", "rename");
    private static final Set<String> MODIFYING_KEYWORDS =
            Set.of("update", "insert", "delete", "merge", "replace", "upsert");
    private static final Set<String> READ_ONLY_KEYWORDS = Set.of("select", "show", "describe", "desc", "explain");

    @Part
    private Schema schema;

    @Part
    private Databases databases;

    @Part
    private Jobs jobs;

    @Part
    private DatabaseDisplayUtils databaseDisplayUtils;

    @ConfigValue("mixing.jdbc.mixing.database")
    private String defaultDatabase;

    @ConfigValue("jdbc.selectableDatabases")
    private List<String> selectableDatabases;

    /**
     * Renders the UI to execute SQL queries.
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql")
    @DefaultRoute
    public void sql(WebContext webContext) {
        // Only display selectable databases which are properly configured...
        List<String> availableDatabases =
                selectableDatabases.stream().filter(name -> databases.getDatabases().contains(name)).toList();
        webContext.respondWith().template("/templates/biz/model/sql.html.pasta", availableDatabases, defaultDatabase);
    }

    /**
     * Executes the given SQL query.
     *
     * @param webContext the current request
     * @param output     the JSON response
     * @throws SQLException in case of a database error
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql/api/execute")
    @InternalService
    public void executeQuery(WebContext webContext, JSONStructuredOutput output) throws SQLException {
        Watch watch = Watch.start();

        try {
            String database = webContext.get(PARAM_DATABASE).asString(defaultDatabase);
            Database db = determineDatabase(database);
            String sqlStatement = requireSingleStatement(webContext.get(PARAM_QUERY).asString());
            SQLQuery query = db.createQuery(sqlStatement).markAsLongRunning();

            OMA.LOG.INFO("Executing SQL (via /system/sql, authored by %s):%n%n%s",
                         UserContext.getCurrentUser().getUserName(),
                         sqlStatement);

            StatementType statementType = determineStatementType(sqlStatement);
            switch (statementType) {
                case DDL -> {
                    // To prevent accidental damage, we try to filter DDL queries (modifying the database structure) and
                    // only permit them against our system database.
                    if (!Strings.areEqual(database, defaultDatabase)) {
                        throw Exceptions.createHandled()
                                        .withSystemErrorMessage(
                                                "Cannot execute a DDL statement against this database. This can be only done for '%s'",
                                                defaultDatabase)
                                        .handle();
                    }

                    output.property("rowModified", query.executeUpdate());
                }
                case MODIFYING -> output.property("rowModified", query.executeUpdate());
                case READ_ONLY -> {
                    int effectiveLimit = webContext.get("limit").asInt(DEFAULT_LIMIT);
                    output.property("effectiveLimit", effectiveLimit);
                    Monoflop rowPrinted = Monoflop.create();
                    query.iterateAll(row -> outputRow(output, rowPrinted, row), new Limit(0, effectiveLimit));
                    if (rowPrinted.successiveCall()) {
                        output.endArray();
                    }
                }
                default -> throw Exceptions.createHandled()
                                           .withDirectMessage(
                                                   "Unsupported SQL statement. Please submit exactly one read, modifying or DDL statement.")
                                           .handle();
            }
            output.property("duration", watch.duration());
        } catch (SQLException exception) {
            // In case of an invalid query, we do not want to log this into the syslog but
            // rather just directly output the message to the user....
            throw Exceptions.createHandled().error(exception).withDirectMessage(exception.getMessage()).handle();
        }
    }

    /**
     * Exports the given SQL query.
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql/export")
    public void exportQuery(WebContext webContext) {
        if (!webContext.isPostRequest()) {
            throw Exceptions.createHandled().withSystemErrorMessage("Unsafe or missing POST detected!").handle();
        }

        String database = webContext.get(PARAM_EXPORT_DATABASE).asString(defaultDatabase);
        determineDatabase(database);
        String sqlStatement = requireSingleStatement(webContext.get(PARAM_EXPORT_QUERY).asString());
        rejectNonReadOnlyExport(sqlStatement);

        ExportQueryResultJobFactory jobFactory =
                jobs.findFactory(ExportQueryResultJobFactory.FACTORY_NAME, ExportQueryResultJobFactory.class);
        String processId = jobFactory.startInBackground(createJobParameterSupplier(database, sqlStatement));
        webContext.respondWith().redirectToGet("/ps/" + processId);
    }

    /**
     * Transforms the parameters from the names used here to the ones expected by {@link ExportQueryResultJobFactory}.
     *
     * @param database     the selected database
     * @param sqlStatement the query to execute
     * @return a parameter supplier as expected by the job factory
     */
    private Function<String, Value> createJobParameterSupplier(String database, String sqlStatement) {
        return parameterName -> switch (parameterName) {
            case PARAM_DATABASE -> Value.of(database);
            case PARAM_QUERY -> Value.of(sqlStatement);
            default -> Value.EMPTY;
        };
    }

    protected Database determineDatabase(String database) {
        if (!selectableDatabases.contains(database) || !databases.getDatabases().contains(database)) {
            throw Exceptions.createHandled().withSystemErrorMessage("Unknown database: %s", database).handle();
        }
        return databases.get(database);
    }

    private void rejectNonReadOnlyExport(String sqlStatement) {
        StatementType statementType = determineStatementType(sqlStatement);
        if (statementType == StatementType.READ_ONLY) {
            return;
        }

        switch (statementType) {
            case DDL ->
                    throw Exceptions.createHandled().withDirectMessage("A DDL statement cannot be exported.").handle();
            case MODIFYING -> throw Exceptions.createHandled()
                                              .withDirectMessage("A modifying statement cannot be exported.")
                                              .handle();
            default -> throw Exceptions.createHandled()
                                       .withDirectMessage("Only read-only statements can be exported.")
                                       .handle();
        }
    }

    private String requireSingleStatement(String query) {
        List<String> statements = splitStatements(query);
        if (statements.isEmpty()) {
            throw Exceptions.createHandled().withDirectMessage("No SQL statement provided.").handle();
        }
        if (statements.size() > 1) {
            throw Exceptions.createHandled().withDirectMessage("Only one SQL statement at a time is allowed.").handle();
        }

        return statements.getFirst();
    }

    private StatementType determineStatementType(String statement) {
        String firstKeyword = extractFirstKeyword(statement);
        if (firstKeyword.isEmpty()) {
            return StatementType.OTHER;
        }

        if (DDL_KEYWORDS.contains(firstKeyword)) {
            return StatementType.DDL;
        }

        if (MODIFYING_KEYWORDS.contains(firstKeyword)) {
            return StatementType.MODIFYING;
        }

        if (READ_ONLY_KEYWORDS.contains(firstKeyword)) {
            return StatementType.READ_ONLY;
        }

        return StatementType.OTHER;
    }

    private String extractFirstKeyword(String statement) {
        if (Strings.isEmpty(statement)) {
            return "";
        }

        String trimmedStatement = statement.trim();
        int index = 0;
        while (index < trimmedStatement.length() && Character.isLetter(trimmedStatement.charAt(index))) {
            index++;
        }

        if (index == 0) {
            return "";
        }

        return trimmedStatement.substring(0, index).toLowerCase();
    }

    /**
     * Splits a raw SQL query string into individual statements separated by semicolons.
     * <p>
     * Correctly handles:
     * <ul>
     *     <li>Single-quoted, double-quoted, and backtick-quoted strings (including escaped quotes)</li>
     *     <li>Line comments ({@code --})</li>
     *     <li>Block comments ({@code /* ... *&#47;})</li>
     * </ul>
     * Blank statements (e.g. trailing semicolons) are ignored.
     *
     * @param query the raw query string to split
     * @return a list of individual SQL statement strings, trimmed of surrounding whitespace
     */
    private List<String> splitStatements(String query) {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        int index = 0;
        while (index < query.length()) {
            char currentChar = query.charAt(index);

            if (currentChar == '\'' || currentChar == '"' || currentChar == '`') {
                index = consumeQuotedString(query, currentStatement, currentChar, index);
            } else if (query.startsWith("--", index)) {
                index = skipLineComment(query, index + 2);
                currentStatement.append(' ');
            } else {
                if (query.startsWith("/*", index)) {
                    index = skipBlockComment(query, index + 2);
                    currentStatement.append(' ');
                } else if (currentChar == ';') {
                    appendIfNotBlank(statements, currentStatement);
                    currentStatement.setLength(0);
                    index++;
                } else {
                    currentStatement.append(currentChar);
                    index++;
                }
            }
        }

        appendIfNotBlank(statements, currentStatement);

        return statements;
    }

    /**
     * Consumes a quoted string starting at the given index and appends it to the current statement builder.
     * <p>
     * Handles both backslash-escaped characters ({@code \'}, {@code \"}) and doubled-quote escaping
     * ({@code ''}, {@code ""}, ` `` `).
     *
     * @param query            the full query string
     * @param currentStatement the statement builder to append to
     * @param quoteChar        the opening (and closing) quote character ({@code '}, {@code "} or {@code `})
     * @param index            the index of the opening quote character
     * @return the index immediately after the closing quote character
     */
    private int consumeQuotedString(String query, StringBuilder currentStatement, char quoteChar, int index) {
        currentStatement.append(quoteChar);
        index++;

        while (index < query.length()) {
            char currentChar = query.charAt(index);
            char nextChar = index + 1 < query.length() ? query.charAt(index + 1) : '\0';
            boolean isEscapedCharacter = currentChar == '\\' && nextChar != '\0';
            boolean isEscapedQuote = currentChar == quoteChar && nextChar == quoteChar;

            currentStatement.append(currentChar);
            if (isEscapedCharacter || isEscapedQuote) {
                currentStatement.append(nextChar);
                index += 2;
            } else {
                index++;
                if (currentChar == quoteChar) {
                    return index;
                }
            }
        }

        return index;
    }

    /**
     * Skips an SQL line comment and all directly following line break characters.
     * <p>
     * Example: for {@code "-- comment\nSELECT 1"} with index {@code 2}, this returns the index of {@code SELECT}.
     *
     * @param query the query to inspect
     * @param index the offset after the opening {@code --}
     * @return the first index after the comment and following line breaks
     */
    private int skipLineComment(String query, int index) {
        while (index < query.length() && query.charAt(index) != '\n' && query.charAt(index) != '\r') {
            index++;
        }

        while (index < query.length() && (query.charAt(index) == '\n' || query.charAt(index) == '\r')) {
            index++;
        }

        return index;
    }

    /**
     * Skips an SQL block comment.
     * <p>
     * Example: for {@code "/* comment *&#47; SELECT 1"} with index {@code 2}, this returns the index of {@code SELECT}.
     *
     * @param query the query to inspect
     * @param index the offset after the opening {@code /*}
     * @return the first index after the closing {@code *&#47;} or the query length if the comment is unterminated
     */
    private int skipBlockComment(String query, int index) {
        int commentEnd = query.indexOf("*/", index);
        return commentEnd >= 0 ? commentEnd + 2 : query.length();
    }

    /**
     * Appends the current statement if it contains non-whitespace content.
     * <p>
     * Example: {@code " SELECT 1 "} is appended as {@code "SELECT 1"}, while {@code "   "} is ignored.
     *
     * @param statements       the collected statements
     * @param currentStatement the statement being built
     */
    private void appendIfNotBlank(List<String> statements, StringBuilder currentStatement) {
        String statement = currentStatement.toString().trim();
        if (Strings.isFilled(statement)) {
            statements.add(statement);
        }
    }

    private enum StatementType {
        READ_ONLY, MODIFYING, DDL, OTHER
    }

    private void outputRow(JSONStructuredOutput output, Monoflop rowPrinted, Row row) {
        if (rowPrinted.firstCall()) {
            output.beginArray("columns");
            for (Tuple<String, Object> field : row.getFieldsList()) {
                output.property("column", field.getFirst());
            }
            output.endArray();
            output.beginArray("rows");
        }
        output.beginArray("row");
        for (Tuple<String, Object> field : row.getFieldsList()) {
            output.property("column", databaseDisplayUtils.formatValueForDisplay(field.getSecond()));
        }
        output.endArray();
    }

    /**
     * Renders the schema list view.
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema")
    public void changes(WebContext webContext) {
        webContext.respondWith().template("/templates/biz/model/schema.html.pasta");
    }

    /**
     * Lists all required changes as JSON
     *
     * @param webContext the current request
     * @param output     the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema/api/list")
    @InternalService
    public void changesList(WebContext webContext, JSONStructuredOutput output) {
        schema.computeRequiredSchemaChanges();
        output.beginArray("changes");
        for (SchemaUpdateAction action : schema.getSchemaUpdateActions()) {
            output.beginObject("change");
            output.property("id", action.getId());
            output.property("reason", action.getReason());
            output.property("realm", action.getRealm());
            output.property("sql", String.join(";\n", action.getSql()) + ";");
            output.property("executed", action.isExecuted());
            output.property("failed", action.isFailed());
            output.property("error", Value.of(action.getError()).asString());
            output.property("dataLossPossible", action.isDataLossPossible());
            output.endObject();
        }
        output.endArray();
    }

    /**
     * Executes the given schema change.
     *
     * @param webContext the current request
     * @param output     the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema/api/execute")
    @InternalService
    public void execute(WebContext webContext, JSONStructuredOutput output) {
        SchemaUpdateAction result = schema.executeSchemaUpdateAction(webContext.get("id").asString());

        if (result != null) {
            output.property("errorMessage", Value.of(result.getError()).asString());
        } else {
            output.property("errorMessage", NLS.get("DatabaseController.unknownChange"));
        }
    }
}
