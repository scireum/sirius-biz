package sirius.biz.jobs.params;

import sirius.kernel.commons.Tuple;

import java.util.List;

/**
 * Provides a single select parameter from a list of key-value pairs.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class SelectParameter<V, P extends SelectParameter<V, P>> extends ParameterBuilder<V, P> {

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

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/selectString.html.pasta";
    }

    @Override
    public String getLegacyTemplateName() {
        return "/templates/biz/jobs/params/legacy/selectString.html.pasta";
    }
}
