/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn

import sirius.db.mongo.Mango
import sirius.kernel.di.std.Part

/**
 * Tests the [ImportTransactionHelper] using MongoDB entities.
 */
class MongoImportTransactionHelperTest :
    ImportTransactionHelperTest<MongoImportTransactionTestEntity>(MongoImportTransactionTestEntity::class.java) {

    override val mapper get() = mango

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango
    }
}
