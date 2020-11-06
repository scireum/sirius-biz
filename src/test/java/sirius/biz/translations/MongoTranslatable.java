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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MongoTranslatable extends MongoEntity implements Translatable<MongoTranslations> {
    private static final Set<String> validLanguages = new HashSet<>(Arrays.asList("da",
                                                                                  "nl",
                                                                                  "en",
                                                                                  "fi",
                                                                                  "fr",
                                                                                  "de",
                                                                                  "hu",
                                                                                  "it",
                                                                                  "nb",
                                                                                  "pt",
                                                                                  "ro",
                                                                                  "ru",
                                                                                  "es",
                                                                                  "sv",
                                                                                  "tr"));

    private final MongoTranslations translations = new MongoTranslations(this, validLanguages);

    private final MongoTranslations unrestrictedTranslations = new MongoTranslations(this);

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

    public MongoTranslations getUnrestrictedTranslations() {
        return unrestrictedTranslations;
    }
}
