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

public class SQLTranslatableTestEntity extends SQLEntity implements Translatable<SQLTranslations> {

    private SQLTranslations translations = new SQLTranslations(this);

    public static final Mapping DESCRIPTION = Mapping.named("description");

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private String description;

    @Override
    public SQLTranslations getTranslations() {
        return translations;
    }
}
