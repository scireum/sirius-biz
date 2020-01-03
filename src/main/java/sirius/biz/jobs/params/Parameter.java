/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.Optional;

/**
 * Defines a parameter which can be rendered in the UI and verify and extract a value from a context.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class Parameter<V, P extends Parameter<V, P>> {

    /**
     * Provides a tri-state value for the visibility of a parameter.
     */
    private enum Visibility {NORMAL, ONLY_WITH_VALUE, HIDDEN}

    private static final String HIDDEN_TEMPLATE_NAME = "/templates/biz/jobs/params/hidden.html.pasta";

    protected String name;
    protected String label;
    protected String description;
    protected boolean required;
    protected Visibility visibility = Visibility.NORMAL;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected Parameter(String name, String label) {
        this.name = name;
        this.label = label;
    }

    @SuppressWarnings("unchecked")
    protected P self() {
        return (P) this;
    }

    /**
     * Specifies the label for the parameter.
     *
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @return the parameter itself for fluent method calls
     */
    public P withLabel(String label) {
        this.label = label;
        return self();
    }

    /**
     * Specifies a short description for the parameter.
     *
     * @param description the description for the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @return the parameter itself for fluent method calls
     */
    public P withDescription(String description) {
        this.description = description;
        return self();
    }

    /**
     * Marks the parameter as required.
     *
     * @return the parameter itself for fluent method calls
     */
    public P markRequired() {
        this.required = true;
        return self();
    }

    /**
     * Marks this parameter as hidden.
     *
     * @return the parameter itself for fluent method calls
     */
    public P hidden() {
        this.visibility = Visibility.HIDDEN;
        return self();
    }

    /**
     * Marks this parameter as hidden if no value is present.
     *
     * @return the parameter itself for fluent method calls
     */
    public P hiddenIfEmpty() {
        this.visibility = Visibility.ONLY_WITH_VALUE;
        return self();
    }

    /**
     * Determines if the parameter is currently visible.
     *
     * @param context the context containing all parameter values
     * @return <tt>true</tt> if the parameter is visible, <tt>false</tt> otherwsie
     */
    public boolean isVisible(Map<String, String> context) {
        if (this.visibility == Visibility.HIDDEN) {
            return false;
        }

        if (this.visibility == Visibility.NORMAL) {
            return true;
        }

        return get(context).isPresent();
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    public abstract String getTemplateName();

    /**
     * Returns the name of the template used to render the parameter in the UI.
     * <p>
     * Similar to {@link #getTemplateName()}, but this method considers the visibility
     * of the parameter and delivers an alternative template in case the parameter should be hidden.
     *
     * @param context the context containing all parameter values
     * @return the name or path of the template used to render the parameter
     */
    public String getEffectiveTemplateName(Map<String, String> context) {
        if (!isVisible(context)) {
            return HIDDEN_TEMPLATE_NAME;
        }
        return getTemplateName();
    }

    /**
     * Verifies the value given for this parameter
     *
     * @param input the input wrapped as <tt>Value</tt>
     * @return a serialized string version of the given input which can later be resolved using
     * {@link #resolveFromString(Value)}
     */
    public String checkAndTransform(Value input) {
        try {
            return checkAndTransformValue(input);
        } catch (IllegalArgumentException e) {
            throw Exceptions.createHandled()
                            .withNLSKey("Parameter.invalidValue")
                            .set("name", getLabel())
                            .set("message", e.getMessage())
                            .handle();
        }
    }

    /**
     * Checks and transforms the given value.
     *
     * @param input the input wrapped as <tt>Value</tt>
     *              * @return a serialized string version of the given input which can later be resolved using
     *              * {@link #resolveFromString(Value)}
     * @return the value represented as string
     * @throws IllegalArgumentException in case of invalid data
     */
    protected abstract String checkAndTransformValue(Value input);

    /**
     * Resolves the previously created string representation into the actual parameter value.
     * <p>
     * The string value will be created by {@link #checkAndTransformValue(Value)}.
     *
     * @param input the string value wrapped as <tt>Value</tt>
     * @return the resolved value wrapped as optional or an empty optional if the value couldn't be resolved
     */
    protected abstract Optional<V> resolveFromString(Value input);

    /**
     * Reads and resolves the value for this parameter from the given context.
     *
     * @param context the context to read the parameter value from
     * @return the resolved value wrapped as optional or an empty optional if there is no value available
     */
    public Optional<V> get(Map<String, String> context) {
        return resolveFromString(Value.of(context.get(getName())));
    }

    /**
     * Reads and resolves the value for this parameter from the given context.
     * <p>
     * Fails if no value could be resolved from the given context.
     *
     * @param context the context to read the parameter value from
     * @return the resolved value
     * @throws sirius.kernel.health.HandledException if no value for this parameter is available in the given context
     */
    public V require(Map<String, String> context) {
        return get(context).orElseThrow(() -> Exceptions.createHandled()
                                                        .withNLSKey("Parameter.required")
                                                        .set("name", getLabel())
                                                        .handle());
    }

    /**
     * Returns the name of the parameter.
     *
     * @return the name of the parameter
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the label of the parameter
     *
     * @return the {@link NLS#smartGet(String) auto translated} label of the parameter
     */
    public String getLabel() {
        return NLS.smartGet(label);
    }

    /**
     * Returns the description of the parameter
     *
     * @return the {@link NLS#smartGet(String) auto translated} description of the parameter
     */
    public String getDescription() {
        return NLS.smartGet(description);
    }

    /**
     * Determines if this parameter is required.
     *
     * @return <tt>ture</tt> if a value has to be present for this parameter, <tt>false</tt> otherwise
     */
    public boolean isRequired() {
        return required;
    }
}
