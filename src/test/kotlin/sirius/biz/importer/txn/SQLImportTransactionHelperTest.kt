/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn

import org.junit.jupiter.api.BeforeAll
import sirius.db.jdbc.OMA
import sirius.kernel.di.std.Part
import java.time.Duration

/**
 * Tests the [ImportTransactionHelper] using JDBC entities.
 */
class SQLImportTransactionHelperTest :
    ImportTransactionHelperTest<SQLImportTransactionTestEntity>(SQLImportTransactionTestEntity::class.java) {

    override val mapper get() = oma

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }
}
