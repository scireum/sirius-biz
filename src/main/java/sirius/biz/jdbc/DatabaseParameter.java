/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.biz.jobs.params.SelectParameter;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.Databases;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * Permits to select a {@link Database} out of the whitelisted (<tt>jdbc.selectableDatabases</tt>) databases known to
 * the system.
 */
public class DatabaseParameter extends SelectParameter<Database, DatabaseParameter> {

    @Part
    private static Databases databases;

    @ConfigValue("jdbc.selectableDatabases")
    private static List<String> selectableDatabases;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be
     *              {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    public DatabaseParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a default which will be simply named "database".
     */
    public DatabaseParameter() {
        super("database", "Database");
    }

    @Override
    public List<Tuple<String, String>> getValues() {
        return selectableDatabases.stream().map(database -> Tuple.create(database, database)).toList();
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (Strings.isEmpty(input) || !databases.hasDatabase(input.asString())) {
            return null;
        }
        return input.asString();
    }

    @Override
    protected Optional<Database> resolveFromString(@Nonnull Value input) {
        if (!databases.hasDatabase(input.asString())) {
            return Optional.empty();
        }
        return Optional.of(databases.get(input.asString()));
    }
}
