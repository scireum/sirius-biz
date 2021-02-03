/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import sirius.db.es.ElasticEntity;
import sirius.db.mixing.Mapping;

public class ESMultiLanguageStringEntity extends ElasticEntity {

    public static final Mapping MULTI_LANG = Mapping.named("multiLang");
    private final MultiLanguageString multiLang = new MultiLanguageString();

    public MultiLanguageString getMultiLang() {
        return multiLang;
    }
}
