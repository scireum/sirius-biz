package sirius.biz.jobs.params;

import java.util.List;
import java.util.Map;

/**
 * Provides a multi select parameter from a list of key-value pairs.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class MultiSelectParameter<V, P extends MultiSelectParameter<V, P>>
        extends ParameterBuilder<List<V>, P> {

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
     * Returns a list of all selectable values provided by the parameter in relation to the
     * given {@linkplain Map context}.
     *
     * @param context the context to read the selection state from
     * @return a list of {@link MultiSelectValue entries} which are available for this parameter in relation to the
     * given context
     */
    public abstract List<MultiSelectValue> getValues(Map<String, String> context);

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/selectMultiString.html.pasta";
    }

    /**
     * Describes a selectable option for this parameter including whether it is currently selected.
     *
     * @param name     the name of the option
     * @param label    the displayable name of the option
     * @param selected <tt>true</tt> if this option is currently selected, <tt>false</tt> otherwise
     */
    public record MultiSelectValue(String name, String label, boolean selected) {
    }
}
