/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Represents an entity used to test {@link EntityParameter} and {@link MultiSelectEntityParameter}.
 */
public class ParameterTestEntity extends SQLEntity {

    public static final Mapping NAME = Mapping.named("name");
    @NullAllowed
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
