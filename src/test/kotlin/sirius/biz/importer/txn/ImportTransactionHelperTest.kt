/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.importer.Importer
import sirius.biz.jobs.batch.file.SyncSourceDeleteMode
import sirius.db.mixing.BaseEntity
import sirius.db.mixing.BaseMapper
import sirius.db.mixing.query.constraints.Constraint
import sirius.kernel.SiriusExtension
import kotlin.test.*

/**
 * Provides a base for testing the [ImportTransactionHelper] along with the import handlers marking the imported
 * entities.
 *
 * @param entityType the type of entities to import
 */
@ExtendWith(SiriusExtension::class)
abstract class ImportTransactionHelperTest<E>(private val entityType: Class<E>)
        where E : BaseEntity<*>, E : ImportTransactionTestEntity {

    /**
     * Provides the mapper used to persist the entities.
     */
    protected abstract val mapper: BaseMapper<in E, *, *>

    private lateinit var importer: Importer
    private lateinit var helper: ImportTransactionHelper

    @BeforeEach
    fun setup() {
        mapper.select(entityType).delete()
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

        entity = mapper.refreshOrFail(entity)
        assertEquals(txnA, entity.importTransactionData.txnId)
        assertEquals("A", entity.importTransactionData.source)

        val txnB = startTransaction("B", SyncSourceDeleteMode.ALL)
        update(entity)

        entity = mapper.refreshOrFail(entity)
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
    fun `keeping the source leaves unchanged entities with their source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        var entity = create("x")

        val txnB = startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        update(entity)

        entity = mapper.refreshOrFail(entity)
        assertEquals(txnB, entity.importTransactionData.txnId)
        assertEquals("A", entity.importTransactionData.source)
    }

    @Test
    fun `keeping the source assigns changed entities to the source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        var entity = create("x")

        val txnB = startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        update(entity) { it.name = "y" }

        entity = mapper.refreshOrFail(entity)
        assertEquals("y", entity.name)
        assertEquals(txnB, entity.importTransactionData.txnId)
        assertEquals("B", entity.importTransactionData.source)
    }

    @Test
    fun `keeping the source assigns entities changed in beforeSave to the source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        var entity = create("x")

        startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        update(entity) { it.derivedNameSuffix = "!" }

        entity = mapper.refreshOrFail(entity)
        assertEquals("x!", entity.derivedName)
        assertEquals("B", entity.importTransactionData.source)
    }

    @Test
    fun `keeping the source assigns new entities to the source`() {
        val txnB = startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        var entity = create("x")

        entity = mapper.refreshOrFail(entity)
        assertEquals(txnB, entity.importTransactionData.txnId)
        assertEquals("B", entity.importTransactionData.source)
    }

    @Test
    fun `keeping the source permits the original source to delete unchanged entities`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        val x = create("x")
        val y = create("y")

        // B confirms x without changing it and therefore must not delete it, nor take it over...
        startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        update(x)
        deleteUnmarked()
        assertTrue { exists(x) }
        assertTrue { exists(y) }

        // ...so that A can delete it, once it no longer provides it
        startTransaction("A", SyncSourceDeleteMode.SAME_SOURCE)
        update(y)
        deleteUnmarked()
        assertFalse { exists(x) }
        assertTrue { exists(y) }
    }

    @Test
    fun `starting a transaction resets keeping the source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        var entity = create("x")

        startTransaction("B", SyncSourceDeleteMode.ALL)
        helper.keepSourceOfUnchangedEntities()
        // Start a new transaction without finishing the previous one, as e.g. done by RelationalEntityImportJob...
        awaitNextTransactionId()
        helper.start("B", SyncSourceDeleteMode.ALL)
        update(entity)

        entity = mapper.refreshOrFail(entity)
        assertEquals("B", entity.importTransactionData.source)
    }

    @Test
    fun `finishing a transaction resets the source`() {
        startTransaction("A", SyncSourceDeleteMode.ALL)
        helper.finish()
        assertFalse { helper.isActive }

        helper.start()
        var entity = create("x")

        entity = mapper.refreshOrFail(entity)
        assertNull(entity.importTransactionData.source)
    }

    @Test
    fun `marking nested entities assigns the source without checking for changes by default`() {
        val txnA = startTransaction("A", SyncSourceDeleteMode.ALL)
        val nested = NestedTestEntity()

        helper.mark(nested) { error("The change check must only be invoked when keeping the source") }

        assertEquals(txnA, nested.importTransactionData.txnId)
        assertEquals("A", nested.importTransactionData.source)
    }

    @Test
    fun `keeping the source leaves unchanged nested entities with their source`() {
        val nested = NestedTestEntity()
        nested.importTransactionData.source = "A"

        val txnB = startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        helper.mark(nested) { false }

        assertEquals(txnB, nested.importTransactionData.txnId)
        assertEquals("A", nested.importTransactionData.source)
    }

    @Test
    fun `keeping the source assigns changed nested entities to the source`() {
        val nested = NestedTestEntity()
        nested.importTransactionData.source = "A"

        startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        helper.mark(nested) { true }

        assertEquals("B", nested.importTransactionData.source)
    }

    @Test
    fun `keeping the source assigns nested entities marked without a change check to the source`() {
        val nested = NestedTestEntity()
        nested.importTransactionData.source = "A"

        startTransaction("B", SyncSourceDeleteMode.SAME_SOURCE)
        helper.keepSourceOfUnchangedEntities()
        helper.mark(nested)

        assertEquals("B", nested.importTransactionData.source)
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

    private fun create(name: String): E =
        importer.createOrUpdateNow(entityType.getDeclaredConstructor().newInstance().apply { this.name = name })

    /**
     * Reloads the given entity, applies the given changes and imports it again.
     */
    private fun update(
        entity: E,
        changes: (E) -> Unit = {}
    ) {
        importer.createOrUpdateNow(mapper.refreshOrFail(entity).also(changes))
    }

    private fun deleteUnmarked() {
        helper.deleteUnmarked<E, Constraint>(entityType, {}, null)
    }

    private fun exists(entity: E) = mapper.find(entityType, entity.id).isPresent

    /**
     * Represents an entity which is nested in another one and therefore not persisted on its own.
     */
    private class NestedTestEntity : ImportTransactionalEntity {
        private val importTransactionData = ImportTransactionData()

        override fun getImportTransactionData() = importTransactionData
    }
}
