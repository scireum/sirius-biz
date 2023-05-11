package sirius.biz.jobs.params;

import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a lazily initialised single-select-parameter.
 * <p>
 * This class extends {@link SelectStringParameter} and is intended for cases where options need to be loaded via helper
 * instances. These references are typically not availailable at construction time of the parameter, as the job
 * factories (that use the parameters) are created "too early" during dependency injection.
 */
public abstract class LazySelectStringParameter extends SelectStringParameter {

    protected boolean initialised = false;

    protected LazySelectStringParameter(String name, String label) {
        super(name, label);
    }

    protected void initialiseIfNecessary() {
        if (initialised) {
            return;
        }

        initialise();
        initialised = true;
    }

    protected abstract void initialise();

    @Override
    public List<Tuple<String, String>> getValues() {
        initialiseIfNecessary();
        return super.getValues();
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        initialiseIfNecessary();
        return super.checkAndTransformValue(input);
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        initialiseIfNecessary();
        return super.computeValueUpdate(parameterContext);
    }

    @Override
    protected Optional<String> resolveFromString(@Nonnull Value input) {
        initialiseIfNecessary();
        return super.resolveFromString(input);
    }
}
