package sirius.biz.jobs.params;

import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;

import java.util.Optional;

/**
 * An abstract class to create easy auto complete selection fields for {@link SQLEntity entities}.
 *
 * @param <V> the type of the sql entity to select
 * @param <T> the type of the sql entity parameter
 */
public abstract class SQLEntityParameter<V extends SQLEntity, T extends SQLEntityParameter>
        extends Parameter<V, SQLEntityParameter<V, T>> {

    @Part
    protected static OMA oma;

    protected Class<V> type;

    public SQLEntityParameter(String name, String title, Class<V> type) {
        super(name, title);
        this.type = type;
    }

    public abstract String getAutocompleteUri();

    @Override
    public String getTemplateName() {
        return "/templates/jobs/params/entity-autocomplete.html.pasta";
    }

    @Override
    protected Optional<V> resolveFromString(Value input) {
        return oma.select(type).eq(SQLEntity.ID, input).one();
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return input.asString("-1");
    }
}