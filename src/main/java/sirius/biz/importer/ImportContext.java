package sirius.biz.importer;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Context;

/**
 * Extends the {@link Context} by adding some import specific utility methods.
 */
public class ImportContext extends Context {

    /**
     * Creates a new import context
     *
     * @return a newly created and empty import context
     */
    public static ImportContext create() {
        return new ImportContext();
    }

    /**
     * Associates the given <tt>value</tt> to the given <tt>key</tt>, while returning <tt>this</tt>
     * to permit fluent method chains.
     *
     * @param key   the key to which the value will be bound
     * @param value the value to be associated with the given key
     * @return <tt>this</tt> to permit fluent method calls
     */
    public ImportContext set(Mapping key, Object value) {
        set(key.getName(), value);
        return this;
    }

    @Override
    public ImportContext set(String key, Object value) {
        super.set(key, value);
        return this;
    }
}
