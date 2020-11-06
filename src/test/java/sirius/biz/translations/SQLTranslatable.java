/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.biz.translations.jdbc.SQLTranslations;
import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SQLTranslatable extends SQLEntity implements Translatable<SQLTranslations> {
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

    private final SQLTranslations translations = new SQLTranslations(this, validLanguages);

    private final SQLTranslations unrestrictedTranslations = new SQLTranslations(this);

    public static final Mapping DESCRIPTION = Mapping.named("description");
    @Length(255)
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public SQLTranslations getTranslations() {
        return translations;
    }

    public SQLTranslations getUnrestrictedTranslations() {
        return unrestrictedTranslations;
    }
}
