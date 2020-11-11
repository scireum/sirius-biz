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
public abstract class ParameterBuilder<V, P extends ParameterBuilder<V, P>> {

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
    protected Parameter.LogVisibility logVisibility = Parameter.LogVisibility.NORMAL;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected ParameterBuilder(String name, String label) {
        this.name = name;
        this.label = label;
    }

    @SuppressWarnings("unchecked")
    protected P self() {
        return (P) this;
    }

    public Parameter<V> build() {
        return new Parameter<>(self());
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
     * Marks this parameter as visible.
     *
     * @return the parameter itself for fluent method calls
     */
    public P visible() {
        this.visibility = Visibility.NORMAL;
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
     * Marks this parameter that it should only be logged in the system log.
     *
     * @return the parameter itself for fluent method calls
     */
    public P logInSystem() {
        this.logVisibility = Parameter.LogVisibility.SYSTEM;
        return self();
    }

    /**
     * Marks this parameter that it should not be logged.
     *
     * @return the parameter itself for fluent method calls
     */
    public P doNotLog() {
        this.logVisibility = Parameter.LogVisibility.NONE;
        return self();
    }

    protected boolean isVisible(Map<String, String> context) {
        if (this.visibility == Visibility.HIDDEN) {
            return false;
        }

        if (this.visibility == Visibility.NORMAL) {
            return true;
        }

        return get(context).isPresent();
    }

    protected String getEffectiveTemplateName(Map<String, String> context) {
        if (!isVisible(context)) {
            return HIDDEN_TEMPLATE_NAME;
        }
        return getTemplateName();
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    protected abstract String getTemplateName();

    protected String checkAndTransform(Value input) {
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

    protected Optional<V> get(Map<String, String> context) {
        return resolveFromString(Value.of(context.get(getName())));
    }

    protected V require(Map<String, String> context) {
        return get(context).orElseThrow(() -> Exceptions.createHandled()
                                                        .withNLSKey("Parameter.required")
                                                        .set("name", getLabel())
                                                        .handle());
    }

    protected String getName() {
        return name;
    }

    protected String getLabel() {
        return NLS.smartGet(label);
    }

    protected String getDescription() {
        return NLS.smartGet(description);
    }

    protected boolean isRequired() {
        return required;
    }

    protected Parameter.LogVisibility getLogVisibility() {
        return logVisibility;
    }
}
