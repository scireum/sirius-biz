/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.Optional;

/**
 * Defines a parameter which uses the textfield as input field.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class TextParameter<V, P extends TextParameter<V, P>> extends ParameterBuilder<V, P> {

    protected String addonText;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected TextParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Specifies an addon text which is shown at the end of the input field.
     *
     * @param addonText the addon test to show, which will be {@link NLS#smartGet(String) auto translated}
     * @return the parameter itself for fluent method calls
     */
    public P withAddonText(String addonText) {
        this.addonText = addonText;
        return self();
    }

    /**
     * Returns the addon text shown at the end of an input text field.
     *
     * @return the {@link NLS#smartGet(String) auto translated} text to show at
     * the end of an input text field
     */
    public String getAddonText() {
        return NLS.smartGet(addonText);
    }

    @Override
    public Optional<?> updateValue(Map<String, String> parameterContext) {
        return updater.apply(parameterContext).map(NLS::toUserString);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/textfield.html.pasta";
    }
}
