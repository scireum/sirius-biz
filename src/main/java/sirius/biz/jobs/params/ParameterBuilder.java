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
import sirius.web.controller.Message;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Provides a mutable instance which can be used to build {@link Parameter parameters}.
 * <p>
 * A parameter is set up using subclasses of this class which are eventually converted using {@link #build()}.
 * This yields an immutable parameter object which can safely made visible (i.e. as constant) to be shared
 * across jobs.
 *
 * @param <V> the type of values produced by this parameter
 * @param <P> recursive type reference to support fluent method calls
 */
public abstract class ParameterBuilder<V, P extends ParameterBuilder<V, P>> {

    /**
     * Provides a tri-state value for the visibility of a parameter.
     */
    private enum Visibility {NORMAL, ONLY_WITH_VALUE, HIDDEN}

    protected String name;
    protected String label;
    protected String description;
    protected boolean required;
    protected Visibility visibility = Visibility.NORMAL;
    protected Parameter.LogVisibility logVisibility = Parameter.LogVisibility.NORMAL;
    protected Predicate<Map<String, String>> shouldHide = map -> false;
    protected Predicate<Map<String, String>> shouldClear = map -> false;
    protected Function<Map<String, String>, Optional<V>> updater = map -> Optional.empty();
    protected Function<Map<String, String>, Optional<Message>> validator = map -> Optional.empty();

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

    /**
     * Creates an immutable parameter from this builder.
     * <p>
     * Once the parameter is built, the builder itself should no longer be used.
     *
     * @return the immutable parameter to be used in {@link sirius.biz.jobs.JobFactory jobs}
     */
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
     * Sets a predicate that is consulted when checking whether the parameter should be hidden.
     *
     * @param shouldHide the predicate
     * @return the parameter itself for fluent method calls
     * @see #isVisible(Map) for the usage
     */
    public P hideWhen(Predicate<Map<String, String>> shouldHide) {
        this.shouldHide = shouldHide;
        return self();
    }

    /**
     * Sets a predicate that is consulted when checking whether the parameter should be cleared.
     *
     * @param shouldClear the predicate
     * @return the parameter itself for fluent method calls
     * @see #needsClear(Map) for the usage
     */
    public P clearWhen(Predicate<Map<String, String>> shouldClear) {
        this.shouldClear = shouldClear;
        return self();
    }

    /**
     * Sets an updater that is consulted when checking whether the parameter value should be updated.
     *
     * @param updater the function to compute the updated value
     * @return the parameter itself for fluent method calls
     * @see #computeValueUpdate(Map) for the usage
     */
    public P withUpdater(Function<Map<String, String>, Optional<V>> updater) {
        this.updater = updater;
        return self();
    }

    /**
     * Sets a validator that is consulted when checking whether the user should see a message.
     *
     * @param validator the validator
     * @return the parameter itself for fluent method calls
     * @see #validate(Map) for the usage
     */
    public P withValidator(Function<Map<String, String>, Optional<Message>> validator) {
        this.validator = validator;
        return self();
    }

    /**
     * A convenience method for {@link #hideWhen(Predicate)}.
     * <p>
     * The BiPredicate allows to chain the method calls directly onto the constructor, where usually the parameter
     * itself is not available as a variable yet.
     *
     * @param shouldHide the predicate
     * @return the parameter itself for fluent method calls
     */
    public P hideWhen(BiPredicate<P, Map<String, String>> shouldHide) {
        this.shouldHide = ctx -> shouldHide.test(self(), ctx);
        return self();
    }

    /**
     * A convenience method for {@link #clearWhen(Predicate)}.
     * <p>
     * The BiPredicate allows to chain the method calls directly onto the constructor, where usually the parameter
     * itself is not available as a variable yet.
     *
     * @param shouldClear the predicate
     * @return the parameter itself for fluent method calls
     */
    public P clearWhen(BiPredicate<P, Map<String, String>> shouldClear) {
        this.shouldClear = ctx -> shouldClear.test(self(), ctx);
        return self();
    }

    /**
     * A convenience method for {@link #withUpdater(Function)}.
     * <p>
     * The BiPredicate allows to chain the method calls directly onto the constructor, where usually the parameter
     * itself is not available as a variable yet.
     *
     * @param updater the update function
     * @return the parameter itself for fluent method calls
     */
    public P withUpdater(BiFunction<P, Map<String, String>, Optional<V>> updater) {
        this.updater = ctx -> updater.apply(self(), ctx);
        return self();
    }

    /**
     * A convenience method for {@link #withValidator(Function)}.
     * <p>
     * The BiPredicate allows to chain the method calls directly onto the constructor, where usually the parameter
     * itself is not available as a variable yet.
     *
     * @param validator the validate function
     * @return the parameter itself for fluent method calls
     */
    public P withValidator(BiFunction<P, Map<String, String>, Optional<Message>> validator) {
        this.validator = ctx -> validator.apply(self(), ctx);
        return self();
    }

    /**
     * A convenience method for {@link #hideWhen(Predicate)} that hides this parameter, when the other parameter has no
     * value.
     *
     * @param parameter the other parameter
     * @param <T>       the type of the other parameter
     * @return the parameter itself for fluent method calls
     */
    public <T> P hideWhenEmpty(Parameter<T> parameter) {
        return hideWhen(ctx -> parameter.get(ctx).isEmpty());
    }

    /**
     * A convenience method for {@link #clearWhen(Predicate)} that clears the parameter when it is hidden.
     *
     * @return the parameter itself for fluent method calls
     */
    public P clearWhenHidden() {
        return clearWhen(shouldHide);
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
        return hideWhen(ctx -> false);
    }

    /**
     * Marks this parameter as hidden.
     *
     * @return the parameter itself for fluent method calls
     */
    public P hidden() {
        return hideWhen(ctx -> true);
    }

    /**
     * Marks this parameter as hidden if no value is present.
     *
     * @return the parameter itself for fluent method calls
     */
    public P hiddenIfEmpty() {
        return hideWhen(ctx -> get(ctx).isEmpty());
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

    /**
     * Determines if the parameter is currently visible.
     *
     * @param parameterContext the context containing all parameter values
     * @return <tt>true</tt> if the parameter is visible, <tt>false</tt> otherwise
     */
    protected boolean isVisible(Map<String, String> parameterContext) {
        return !shouldHide.test(parameterContext);
    }

    /**
     * Checks whether the parameter value should be cleared in the frontend.
     *
     * @param parameterContext the values of all parameters
     * @return true when the parameter value should be cleared
     */
    public boolean needsClear(Map<String, String> parameterContext) {
        return shouldClear.test(parameterContext);
    }

    /**
     * Checks whether the parameter value should be updated to a new value.
     *
     * @param parameterContext the values of all parameters
     * @return an Optional, filled with the new value if the value should be updated
     */
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext);
    }

    /**
     * Validates the value of the parameter.
     *
     * @param parameterContext the values of all parameters
     * @return a message containing a displayable info-, warning- or error-message, or am empty optional if no such
     * message should be displayed
     */
    public Optional<Message> validate(Map<String, String> parameterContext) {
        return validator.apply(parameterContext);
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    protected abstract String getTemplateName();

    /**
     * Returns the name of the legacy template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    protected String getLegacyTemplateName() {
        throw new UnsupportedOperationException("Legacy template not implemented!");
    }

    /**
     * Verifies the value given for this parameter
     *
     * @param input the input wrapped as <tt>Value</tt>
     * @return a serialized string version of the given input which can later be resolved using
     * {@link ParameterBuilder#resolveFromString(Value)}
     */
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
     * @return a serialized string version of the given input which can later be resolved using
     * {@link #resolveFromString(Value)}
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
     * @param parameterContext the context to read the parameter value from
     * @return the resolved value wrapped as optional or an empty optional if there is no value available
     */
    public Optional<V> get(Map<String, String> parameterContext) {
        return resolveFromString(Value.of(parameterContext.get(getName())));
    }

    /**
     * Reads and resolves the value for this parameter from the given context.
     * <p>
     * Fails if no value could be resolved from the given context.
     *
     * @param parameterContext the context to read the parameter value from
     * @return the resolved value
     * @throws sirius.kernel.health.HandledException if no value for this parameter is available in the given context
     */
    protected V require(Map<String, String> parameterContext) {
        return get(parameterContext).orElseThrow(() -> Exceptions.createHandled()
                                                                 .withNLSKey("Parameter.required")
                                                                 .set("name", getLabel())
                                                                 .handle());
    }

    /**
     * Returns the name of the parameter.
     *
     * @return the name of the parameter
     */
    protected String getName() {
        return name;
    }

    /**
     * Returns the label of the parameter
     *
     * @return the {@link NLS#smartGet(String) auto translated} label of the parameter
     */
    protected String getLabel() {
        return NLS.smartGet(label);
    }

    /**
     * Returns the description of the parameter
     *
     * @return the {@link NLS#smartGet(String) auto translated} description of the parameter
     */
    protected String getDescription() {
        return NLS.smartGet(description);
    }

    /**
     * Determines if this parameter is required.
     *
     * @return <tt>true</tt> if a value has to be present for this parameter, <tt>false</tt> otherwise
     */
    protected boolean isRequired() {
        return required;
    }

    /**
     * Returns a {@link Parameter.LogVisibility} value which indicates in which log this parameter should be logged.
     *
     * @return an enum value indicating the log behavior of this parameter
     */
    protected Parameter.LogVisibility getLogVisibility() {
        return logVisibility;
    }
}
