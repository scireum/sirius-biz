/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.biz.translations.mongo.MongoTranslations;
import sirius.db.mixing.Mapping;
import sirius.db.mongo.MongoEntity;

public class MongoTranslatableTestEntity extends MongoEntity implements Translatable<MongoTranslations> {

    private MongoTranslations translations = new MongoTranslations(this);

    public static final Mapping DESCRIPTION = Mapping.named("description");

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private String description;

    @Override
    public MongoTranslations getTranslations() {
        return translations;
    }
}
