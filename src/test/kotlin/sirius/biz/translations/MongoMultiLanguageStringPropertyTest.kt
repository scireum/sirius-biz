/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations

import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.tenants.TenantsHelper
import sirius.db.mongo.Mango
import sirius.db.mongo.Mongo
import sirius.kernel.SiriusExtension
import sirius.kernel.async.CallContext
import sirius.kernel.di.std.Part
import sirius.kernel.testutil.Reflections

@ExtendWith(SiriusExtension::class)
class MongoMultiLanguageStringPropertyTest {

    companion object {
        @Part
        @JvmStatic
        private lateinit var mango: Mango

        @Part
        @JvmStatic
        private lateinit var mongo: Mongo
    }

    @Test
    fun `Comparing persisted data with null keys works as expected`() {
        var entity = MongoMultiLanguageStringEntity()
        entity.multiLangText.addText("de", null)
        mango.update(entity)
        entity = mango.tryRefresh(entity)
        entity.multiLangText.addText("de", null)

        mango.update(entity)
        assertEquals(MultiLanguageString(), entity.multiLangText)
    }

    @Test
    fun `store works without fallback`() {
        val entity = MongoMultiLanguageStringRequiredNoFallbackEntity()
        entity.multiLangText.addText("de", "")
        mango.update(entity)
        mango.refreshOrFail(entity)
    }

    @Test
    fun `store retrieve and validate`() {
        TenantsHelper.installTestTenant()
        val entity = MongoMultiLanguageStringEntity()
        entity.multiLangText.addText("de", "Schmetterling")
        entity.multiLangText.addText("en", "Butterfly")
        mango.update(entity)
        val output = mango.refreshOrFail(entity)

        assertEquals(2, output.multiLangText.size())
        assertTrue { output.multiLangText.hasText("de") }
        assertFalse { output.multiLangText.hasText("fr") }
        assertEquals("Schmetterling", output.multiLangText.fetchText("de"))
        assertNull(output.multiLangText.fetchText("fr"))
        assertEquals("Schmetterling", output.multiLangText.fetchText("de", "en"))
        assertEquals("Butterfly", output.multiLangText.fetchText("fr", "en"))
        assertNull(output.multiLangText.fetchText("fr", "es"))
        assertEquals(Optional.of("Schmetterling"), output.multiLangText.getText("de"))
        assertTrue { output.multiLangText.getText("fr").isEmpty }

        CallContext.getCurrent().language = "en"

        assertEquals("Butterfly", output.multiLangText.fetchText())
        assertEquals(Optional.of("Butterfly"), output.multiLangText.text)

        CallContext.getCurrent().language = "fr"

        assertNull(output.multiLangText.fetchText())
        assertTrue { output.multiLangText.text.isEmpty }
    }

    @Test
    fun `store using default language`() {
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().language = "en"
        val entity = MongoMultiLanguageStringEntity()
        entity.multiLangText.addText("Butterfly")
        mango.update(entity)
        val output = mango.refreshOrFail(entity)

        assertEquals("Butterfly", output.multiLangText.fetchText())
        assertNull(output.multiLangText.fetchText("de"))
    }

    @Test
    fun `raw data check`() {
        TenantsHelper.installTestTenant()
        val entity = MongoMultiLanguageStringEntity()
        entity.multiLangText.addText("pt", "Borboleta")
        entity.multiLangText.addText("es", "Mariposa")
        entity.multiLangText.addText("en", "")
        entity.multiLangText.addText("de", null)
        mango.update(entity)

        val expectedString = "[Document{{lang=pt, text=Borboleta}}, Document{{lang=es, text=Mariposa}}]"
        val storedString = mongo.find()
                .where("id", entity.id)
                .singleIn("mongomultilanguagestringentity")
                .get()
                .get("multiLangText")
                .asString()

        assertEquals(expectedString, storedString)
    }

    @Test
    fun `fallback can not be added to field without fallback enabled`() {
        val entity = MongoMultiLanguageStringEntity()
        assertThrows<IllegalStateException> {
            entity.multiLangText.setFallback("test")
        }
    }

    @Test
    fun `fallback can be added and retrieved`() {
        TenantsHelper.installTestTenant()
        val entity = MongoMultiLanguageStringEntity()
        entity.multiLangTextWithFallback.addText("de", "In Ordnung")
        entity.multiLangTextWithFallback.addText("en", "Fine")
        entity.multiLangTextWithFallback.setFallback("OK")
        mango.update(entity)
        val output = mango.refreshOrFail(entity)

        assertEquals(3, output.multiLangTextWithFallback.size())
        assertTrue { output.multiLangTextWithFallback.hasText("de") }
        assertTrue { output.multiLangTextWithFallback.hasText("en") }
        assertEquals(true, Reflections.callPrivateMethod(output.multiLangTextWithFallback, "hasFallback"))

        assertEquals("In Ordnung", output.multiLangTextWithFallback.fetchTextOrFallback("de"))
        assertEquals("Fine", output.multiLangTextWithFallback.fetchTextOrFallback("en"))
        assertEquals("OK", output.multiLangTextWithFallback.fetchTextOrFallback("fr"))

        assertEquals("In Ordnung", output.multiLangTextWithFallback.fetchText("de"))
        assertNull(output.multiLangTextWithFallback.fetchText("fr"))

        assertEquals("In Ordnung", output.multiLangTextWithFallback.fetchText("de"))
        assertNull(output.multiLangTextWithFallback.fetchText("fr"))

        assertEquals(Optional.of("In Ordnung"), output.multiLangTextWithFallback.getText("de"))
        assertEquals(Optional.of("OK"), output.multiLangTextWithFallback.getText("fr"))

        CallContext.getCurrent().language = "en"

        assertEquals("Fine", output.multiLangTextWithFallback.fetchText())
        assertEquals(Optional.of("Fine"), output.multiLangTextWithFallback.text)

        CallContext.getCurrent().language = "fr"

        assertEquals("OK", output.multiLangTextWithFallback.fetchTextOrFallback())
        assertEquals(Optional.of("OK"), output.multiLangTextWithFallback.text)
    }

    @Test
    fun `new null values are not stored`() {
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().language = "en"
        val entity = MongoMultiLanguageStringEntity()
        entity.multiLangTextWithFallback.addText(null)
        entity.multiLangTextWithFallback.addText("de", "Super")
        entity.multiLangTextWithFallback.setFallback(null)
        mango.update(entity)
        val output = mango.refreshOrFail(entity)

        assertNull(output.multiLangTextWithFallback.fetchText())
        assertNull(output.multiLangTextWithFallback.fetchText("en"))
        assertEquals("Super", output.multiLangTextWithFallback.fetchText("de"))
        assertNull(output.multiLangTextWithFallback.fetchText("fallback"))
    }

    @Test
    fun `keys with null values are removed from the underlying map if a key already exists`() {
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().language = "en"
        var entity = MongoMultiLanguageStringEntity()
        entity.multiLangText.addText("en", "Super")
        mango.update(entity)
        entity = mango.refreshOrFail(entity)

        assertEquals("Super", entity.multiLangText.fetchText())

        entity.multiLangText.addText("en", null)
        mango.update(entity)
        val output = mango.refreshOrFail(entity)

        assertNull(output.multiLangTextWithFallback.fetchText())
    }

    @Test
    fun `asserts setData removes null keys before persisting`() {
        TenantsHelper.installTestTenant()
        CallContext.getCurrent().language = "en"
        val entity = MongoMultiLanguageStringEntity()
        val data = mapOf("en" to "Great", "de" to null)
        entity.multiLangText.setData(data)
        mango.update(entity)
        val output = mango.refreshOrFail(entity)

        assertEquals("Great", output.multiLangText.fetchText("en"))
        assertNull(output.multiLangText.fetchText("de"))
        assertEquals(1, output.multiLangText.size())
    }

    @Test
    fun `trying to directly call modify should throw an unsupported operation exception`() {
        val entity = MongoMultiLanguageStringEntity()

        assertThrows<UnsupportedOperationException> {
            entity.multiLangText.modify()["de"] = null
        }
    }
}
