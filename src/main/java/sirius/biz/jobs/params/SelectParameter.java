package sirius.biz.jobs.params;

import sirius.kernel.commons.Tuple;
import sirius.kernel.nls.NLS;

import java.util.List;

/**
 * Provides a single select parameter from a list of key-value pairs.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class SelectParameter<V, P extends SelectParameter<V, P>> extends ParameterBuilder<V, P> {

    protected boolean multipleOptions = false;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be
     *              {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    protected SelectParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Enumerates all values provided by the parameter.
     *
     * @return list of {@link Tuple entries} with the key as first and display value as second tuple items.
     */
    public abstract List<Tuple<String, String>> getValues();

    /**
     * Allows to select multiple values.
     *
     * @return the parameter itself for fluent method calls
     */
    public P withMultipleOptions() {
        this.multipleOptions = true;
        return self();
    }

    @Override
    public String getTemplateName() {
        if (multipleOptions) {
            return "/templates/biz/jobs/params/selectMultiString.html.pasta";
        } else {
            return "/templates/biz/jobs/params/selectSingleString.html.pasta";
        }
    }
}
