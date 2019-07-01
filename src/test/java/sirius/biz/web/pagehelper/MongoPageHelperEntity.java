/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.pagehelper;

import sirius.db.mixing.Mapping;
import sirius.db.mongo.MongoEntity;

public class MongoPageHelperEntity extends MongoEntity {

    private String stringField;
    public static final Mapping STRING_FIELD = Mapping.named("stringField");

    private boolean booleanField;
    public static final Mapping BOOLEAN_FIELD = Mapping.named("booleanField");

    public String getStringField() {
        return stringField;
    }

    public void setStringField(String stringField) {
        this.stringField = stringField;
    }

    public boolean isBooleanField() {
        return booleanField;
    }

    public void setBooleanField(boolean booleanField) {
        this.booleanField = booleanField;
    }
}
