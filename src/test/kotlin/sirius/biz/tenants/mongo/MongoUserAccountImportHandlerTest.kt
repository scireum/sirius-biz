/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.mongo

import io.mockk.mockk
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import sirius.biz.importer.ImporterContext
import sirius.kernel.SiriusExtension
import kotlin.test.assertContains

/**
 * Tests the [MongoUserAccountImportHandler].
 */
@ExtendWith(SiriusExtension::class)
class MongoUserAccountImportHandlerTest {

    companion object {
        @JvmStatic
        private fun getExpectedExportMappings(): List<String> = listOf(
            "trace_createdAt",
            "userAccountData_login_username",
            "userAccountData_email",
            "userAccountData_person_salutation",
            "userAccountData_person_title",
            "userAccountData_person_firstname",
            "userAccountData_person_lastname",
            "userAccountData_permissions_permissions",
            "userAccountData_login_accountLocked",
            "performance-flags",
            "userAccountData_login_lastSeen",
            "userAccountData_login_lastLogin",
            "userAccountData_login_numberOfLogins",
            "userAccountData_login_lastExternalLogin",
            "userAccountData_login_lastPasswordChange"
        )
    }

    @ParameterizedTest()
    @MethodSource("getExpectedExportMappings")
    fun `default export mappings contain the expected columns`(expectedColumnName: String) {
        val factory = MongoUserAccountImportHandler.MongoUserAccountImportHandlerFactory()
        val sut = factory.create(MongoUserAccount::class.java, mockk<ImporterContext>())

        val result = sut.getDefaultExportMapping()

        assertContains(
            result,
            expectedColumnName,
            "getDefaultExportMapping() does not contain column $expectedColumnName"
        )
    }
}
