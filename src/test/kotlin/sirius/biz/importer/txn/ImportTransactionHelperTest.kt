/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.importer.Importer
import sirius.biz.jobs.batch.file.SyncSourceDeleteMode
import sirius.db.jdbc.OMA
import sirius.db.jdbc.constraints.SQLConstraint
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.time.Duration
import kotlin.test.*

/**
 * Tests the [ImportTransactionHelper] along with the import handlers marking the imported entities.
 */
@ExtendWith(SiriusExtension::class)
class ImportTransactionHelperTest {

    private lateinit var importer: Importer
    private lateinit var helper: ImportTransactionHelper

    @BeforeEach
    fun setup() {
        oma.select(SQLImportTransactionTestEntity::class.java).delete()
        importer = Importer("ImportTransactionHelperTest")
        helper = importer.findHelper(ImportTransactionHelper::class.java)
    }

    @AfterEach
    fun cleanup() {
        importer.close()
    }

    @Test
    fun `marking assigns the transaction id and takes over the source by default`() {
        val txnA = startTransaction("A", SyncSourceDeleteMode.ALL)
        var entity = create("x")

        entity = oma.refreshOrFail(entity)
        assertEquals(txnA, entity.importTransactionData.txnId)
        assertEquals("A", entity.importTransactionData.source)

        val txnB = startTransaction("B", SyncSourceDeleteMode.ALL)
        update(entity)

        entity = oma.refreshOrFail(entity)
        assertEquals(txnB, entity.importTransactionData.txnId)
        assertEquals("B", entity.importTransactionData.source)
    }

    @Test
    fun `deleteUnmarked with ALL deletes all unmarked entities`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        val a1 = create("a1")
        startTransaction("B", SyncSourceDeleteMode.ALL)
        val b1 = create("b1")
        startTransaction(null, SyncSourceDeleteMode.ALL)
        val empty = create("empty")

        startTransaction("A", SyncSourceDeleteMode.ALL)
        update(a1)
        deleteUnmarked()

        assertTrue { exists(a1) }
        assertFalse { exists(b1) }
        assertFalse { exists(empty) }
    }

    @Test
    fun `deleteUnmarked with SAME_SOURCE only deletes unmarked entities of the same source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        val a1 = create("a1")
        val a2 = create("a2")
        startTransaction("B", SyncSourceDeleteMode.ALL)
        val b1 = create("b1")
        startTransaction(null, SyncSourceDeleteMode.ALL)
        val empty = create("empty")

        startTransaction("A", SyncSourceDeleteMode.SAME_SOURCE)
        update(a1)
        deleteUnmarked()

        assertTrue { exists(a1) }
        assertFalse { exists(a2) }
        assertTrue { exists(b1) }
        assertTrue { exists(empty) }
    }

    @Test
    fun `deleteUnmarked with SAME_SOURCE_OR_EMPTY also deletes unmarked entities without a source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        val a1 = create("a1")
        val a2 = create("a2")
        startTransaction("B", SyncSourceDeleteMode.ALL)
        val b1 = create("b1")
        startTransaction(null, SyncSourceDeleteMode.ALL)
        val empty = create("empty")

        startTransaction("A", SyncSourceDeleteMode.SAME_SOURCE_OR_EMPTY)
        update(a1)
        deleteUnmarked()

        assertTrue { exists(a1) }
        assertFalse { exists(a2) }
        assertTrue { exists(b1) }
        assertFalse { exists(empty) }
    }

    @Test
    fun `finishing a transaction resets the source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        helper.finish()
        assertFalse { helper.isActive }

        helper.start()
        var entity = create("x")

        entity = oma.refreshOrFail(entity)
        assertNull(entity.importTransactionData.source)
    }

    /**
     * Finishes the current transaction and starts a new one with a distinct transaction id.
     */
    private fun startTransaction(source: String?, mode: SyncSourceDeleteMode): Long {
        helper.finish()
        awaitNextTransactionId()
        helper.start(source, mode)
        return helper.currentTransaction
    }

    /**
     * Transaction ids are timestamps, therefore we wait for the next millisecond to obtain a distinct one.
     */
    private fun awaitNextTransactionId() {
        val now = System.currentTimeMillis()
        while (System.currentTimeMillis() == now) {
            Thread.onSpinWait()
        }
    }

    private fun create(name: String) =
        importer.createOrUpdateNow(SQLImportTransactionTestEntity().apply { this.name = name })

    /**
     * Reloads the given entity and imports it again.
     */
    private fun update(entity: SQLImportTransactionTestEntity) {
        importer.createOrUpdateNow(oma.refreshOrFail(entity))
    }

    private fun deleteUnmarked() {
        helper.deleteUnmarked<SQLImportTransactionTestEntity, SQLConstraint>(
            SQLImportTransactionTestEntity::class.java,
            {},
            null
        )
    }

    private fun exists(entity: SQLImportTransactionTestEntity) =
        oma.find(SQLImportTransactionTestEntity::class.java, entity.id).isPresent

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
