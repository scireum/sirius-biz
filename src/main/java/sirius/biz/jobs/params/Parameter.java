/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.jobs.JobFactory;
import sirius.biz.process.ProcessContext;
import sirius.kernel.commons.Value;
import sirius.kernel.di.transformers.Composable;
import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Defines a parameter which queries a value for a {@link JobFactory} as parameter.
 * <p>
 * A new parameter is created via one of the subclasses of {@link ParameterBuilder}. These are mainly separated so
 * that a resulting parameter is immutable and can be safely shared as a global constant.
 * <p>
 * The parameters for a job are returned in {@link JobFactory#getParameters()} and are most probably collected via
 * {@link sirius.biz.jobs.BasicJobFactory#collectParameters(Consumer)}.
 * <p>
 * The value can either be fetched via {@link #get(Map)} or with a helper like
 * {@link ProcessContext#getParameter(Parameter)} or {@link ProcessContext#require(Parameter)}.
 * <p>
 * Note that the underlying builder can be accessed using {@code parameter.as(ParameterBuilderClass.class)}. However,
 * this must only be used in templates to invoke non-mutating methods when rendering the parameter template. A
 * <tt>Parameter</tt> itself should remain immutable once created.
 *
 * @param <V> the type of values produced by this parameter
 */
public class Parameter<V> extends Composable {

    /**
     * Provides a tri-state value indicating in which log the parameter can appear.
     */
    public enum LogVisibility {NORMAL, SYSTEM, NONE}

    private final ParameterBuilder<V, ?> delegate;

    protected Parameter(ParameterBuilder<V, ?> delegate) {
        this.delegate = delegate;
        Class<?> delegateClass = delegate.getClass();
        while (delegateClass != null && delegateClass != ParameterBuilder.class) {
            attach(delegateClass, delegate);
            delegateClass = delegateClass.getSuperclass();
        }
    }

    /**
     * Determines if the parameter is currently visible.
     *
     * @param context the context containing all parameter values
     * @return <tt>true</tt> if the parameter is visible, <tt>false</tt> otherwise
     */
    public boolean isVisible(Map<String, String> context) {
        return delegate.isVisible(context);
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    public String getTemplateName() {
        return delegate.getTemplateName();
    }

    /**
     * Verifies the value given for this parameter
     *
     * @param input the input wrapped as <tt>Value</tt>
     * @return a serialized string version of the given input which can later be resolved using
     * {@link ParameterBuilder#resolveFromString(Value)}
     */
    public String checkAndTransform(Value input) {
        return delegate.checkAndTransform(input);
    }

    /**
     * Reads and resolves the value for this parameter from the given context.
     *
     * @param context the context to read the parameter value from
     * @return the resolved value wrapped as optional or an empty optional if there is no value available
     */
    public Optional<V> get(Map<String, String> context) {
        return delegate.get(context);
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
        return delegate.require(context);
    }

    /**
     * Returns the name of the parameter.
     *
     * @return the name of the parameter
     */
    public String getName() {
        return delegate.getName();
    }

    /**
     * Returns the label of the parameter
     *
     * @return the {@link NLS#smartGet(String) auto translated} label of the parameter
     */
    public String getLabel() {
        return delegate.getLabel();
    }

    /**
     * Returns the description of the parameter
     *
     * @return the {@link NLS#smartGet(String) auto translated} description of the parameter
     */
    public String getDescription() {
        return delegate.getDescription();
    }

    /**
     * Determines if this parameter is required.
     *
     * @return <tt>true</tt> if a value has to be present for this parameter, <tt>false</tt> otherwise
     */
    public boolean isRequired() {
        return delegate.isRequired();
    }

    /**
     * Returns a {@link LogVisibility} value which indicates in which log this parameter should be logged.
     *
     * @return an enum value indicating the log behavior of this parameter
     */
    public LogVisibility getLogVisibility() {
        return delegate.getLogVisibility();
    }

    /**
     * Reveals the type of the underlying parameter builder to determine what type of parameter is present.
     *
     * @return the type of the builder which was used to create this parameter.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends ParameterBuilder<?, ?>> getBuilderType() {
        return (Class<? extends ParameterBuilder<?, ?>>) delegate.getClass();
    }

    /**
     * Provides access to the underlying builder of this parameter.
     * @return the builder which was used for this parameter
     */
    public ParameterBuilder<?, ?> getBuilder() {
        return delegate;
    }
}
