/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import java.util.function.Supplier;

/**
 * Used as target class to transform {@link sirius.db.mixing.Property properties} to {@link FieldDefinition fields}.
 * <p>
 * We use a supplier here as the transformed objects are cached but we want a new instance on each invocation to
 * reflect things like user specific translations etc.
 */
public interface FieldDefinitionSupplier extends Supplier<FieldDefinition> {
}
