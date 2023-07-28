/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.codelists.jdbc.SQLCodeList
import sirius.biz.codelists.jdbc.SQLCodeListEntry
import sirius.biz.tenants.TenantsHelper
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.time.Duration

@ExtendWith(SiriusExtension::class)
class CodeListsTest {

    companion object {
        @Part
        private lateinit var codeLists: CodeLists<Long, SQLCodeList, SQLCodeListEntry>

        @Part
        private lateinit var oma: OMA

        @BeforeAll
        @JvmStatic
        fun setup() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }

    @Test
    fun `Getting entries of a code list works`() {
        TenantsHelper.installTestTenant()
        val entries = codeLists.getEntries("test")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `Auto-creating a list and values works`() {
        TenantsHelper.installTestTenant()
        val value = codeLists.getValue("test", "testCode")
        assertEquals("testCode", value)
    }

    @Test
    fun `tryGetValue auto-creates if possible and reports correctly otherwise`() {
        TenantsHelper.installTestTenant()
        assertTrue(codeLists.tryGetValue("test", "unknownCode").isPresent())
        assertTrue(codeLists.tryGetValue("hard-test", "unknownCode").isEmpty())
    }
}
