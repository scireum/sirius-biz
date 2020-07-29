/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;

/**
 * Represents translation data
 */
public class TranslationData extends Composite {
    /**
     * Contains the unique name of the entity this translation data belongs to.
     */
    public static final Mapping OWNER = Mapping.named("owner");
    @Length(255)
    private String owner;

    /**
     * Represents the name of the translated field (e.g. "name", or "description").
     */
    public static final Mapping FIELD = Mapping.named("field");
    @Length(255)
    private String field;

    /**
     * Contains the language code used for the translation.
     */
    public static final Mapping LANG = Mapping.named("lang");
    @Length(3)
    private String lang;

    /**
     * Contains the translation text.
     */
    public static final Mapping TEXT = Mapping.named("text");
    @Lob
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
