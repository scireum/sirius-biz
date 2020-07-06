/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import sirius.kernel.BaseSpecification

/**
 * Provides constants used in specs for every database type (MongoDB, JDBC)
 */
class TranslationsSpec extends BaseSpecification {
    protected static final DESCRIPTION_TEXT = "This is a test"
    protected static final GERMAN_TEXT = "Das ist ein Test"
    protected static final SWEDISH_TEXT = "Detta är ett prov"
    // note: these language codes must be in sirius.biz.translations.BasicTranslations.supportedLanguages
    protected static final GERMAN = "de"
    protected static final SWEDISH = "sv"
}
