package sirius.biz.jobs.params;

import sirius.kernel.commons.Tuple;

import java.util.List;

/**
 * Provides a multi select parameter from a list of key-value pairs.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class MultiSelectParameter<V, P extends MultiSelectParameter<V, P>> extends ParameterBuilder<List<V>, P> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be
     *              {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    protected MultiSelectParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Enumerates all values provided by the parameter.
     *
     * @return list of {@link Tuple entries} with the key as first and display value as second tuple items.
     */
    public abstract List<Tuple<String, String>> getValues();

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/selectMultiString.html.pasta";
    }
}
