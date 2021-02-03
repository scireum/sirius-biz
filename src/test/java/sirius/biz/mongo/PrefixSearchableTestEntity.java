/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.biz.translations.MultiLanguageString;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.StringList;
import sirius.db.mixing.types.StringMap;

public class PrefixSearchableTestEntity extends PrefixSearchableEntity {

    @PrefixSearchContent
    private String test;

    @PrefixSearchContent
    private final StringMap map = new StringMap();

    @PrefixSearchContent
    @NullAllowed
    private final MultiLanguageString multiLanguageText = new MultiLanguageString();

    @PrefixSearchContent
    private final StringList list = new StringList();

    @NullAllowed
    private String unsearchableTest;

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public String getUnsearchableTest() {
        return unsearchableTest;
    }

    public void setUnsearchableTest(String unsearchableTest) {
        this.unsearchableTest = unsearchableTest;
    }

    public StringMap getMap() {
        return map;
    }

    public MultiLanguageString getMultiLanguageText() {
        return multiLanguageText;
    }

    public StringList getList() {
        return list;
    }
}
