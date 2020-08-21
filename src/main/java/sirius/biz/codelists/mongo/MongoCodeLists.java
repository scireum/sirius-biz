/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.codelists.CodeLists;
import sirius.biz.translations.mongo.MongoTranslations;
import sirius.kernel.di.std.Register;

/**
 * Provides the MongoDB implementation of {@link CodeLists}.
 */
@Register(classes = {CodeLists.class, MongoCodeLists.class}, framework = MongoCodeLists.FRAMEWORK_CODE_LISTS_MONGO)
public class MongoCodeLists extends CodeLists<String, MongoCodeList, MongoTranslations, MongoCodeListEntry> {

    /**
     * Names the framework which must be enabled to activate the code lists feature.
     */
    public static final String FRAMEWORK_CODE_LISTS_MONGO = "biz.code-lists-mongo";

    @Override
    protected Class<MongoCodeListEntry> getEntryType() {
        return MongoCodeListEntry.class;
    }

    @Override
    protected Class<MongoCodeList> getListType() {
        return MongoCodeList.class;
    }
}
