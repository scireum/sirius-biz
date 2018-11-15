package sirius.biz.params;

import sirius.biz.jobs.params.Parameter;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.kernel.di.std.Part;

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

    public SQLEntityParameter(String name, String title) {
        super(name, title);
    }

    public abstract String getAutocompleteUri();

    @Override
    public String getTemplateName() {
        return "/templates/params/entity-autocomplete.html.pasta";
    }
}
