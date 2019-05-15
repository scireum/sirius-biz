/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.mixing.Mapping;
import sirius.db.mongo.MongoEntity;

public class AutoLoadEntity extends MongoEntity {

    @Autoloaded
    private String stringField;
    public static final Mapping STRING_FIELD = Mapping.named("stringField");

    @Autoloaded
    private int intField;
    public static final Mapping INT_FIELD = Mapping.named("intField");

    public String getStringField() {
        return stringField;
    }

    public void setStringField(String stringField) {
        this.stringField = stringField;
    }

    public int getIntField() {
        return intField;
    }

    public void setIntField(int intField) {
        this.intField = intField;
    }
}
