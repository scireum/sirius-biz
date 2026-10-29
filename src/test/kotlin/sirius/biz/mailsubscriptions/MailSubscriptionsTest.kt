/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.mailsubscriptions

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.tenants.TenantsHelper
import sirius.biz.tenants.jdbc.SQLUserAccount
import sirius.db.jdbc.OMA
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the [MailSubscriptions].
 */
@ExtendWith(SiriusExtension::class)
class MailSubscriptionsTest {

    private lateinit var userAccount: SQLUserAccount

    @Test
    fun `mail types are read from the config and ordered by their priority`() {
        assertEquals(listOf("job-failed", "job-warnings", "job-success"), mailSubscriptions.mailTypes)
    }

    @Test
    fun `a new user is not subscribed to any mail type`() {
        assertTrue(mailSubscriptions.getSubscribedMailTypes(userAccount).isEmpty())
        assertFalse(mailSubscriptions.isSubscribed(userAccount, "job-failed"))
    }

    @Test
    fun `subscriptions are stored and can be read again`() {
        mailSubscriptions.updateSubscriptions(userAccount, listOf("job-failed"))

        val reloadedAccount = oma.refreshOrFail(userAccount)
        assertTrue(mailSubscriptions.isSubscribed(reloadedAccount, "job-failed"))
        assertFalse(mailSubscriptions.isSubscribed(reloadedAccount, "job-success"))
    }

    @Test
    fun `several subscriptions are stored at once without overwriting each other`() {
        mailSubscriptions.updateSubscriptions(userAccount, listOf("job-failed", "job-warnings", "job-success"))

        val reloadedAccount = oma.refreshOrFail(userAccount)
        assertEquals(
            setOf("job-failed", "job-warnings", "job-success"),
            mailSubscriptions.getSubscribedMailTypes(reloadedAccount)
        )
    }

    @Test
    fun `storing no mail types removes all subscriptions`() {
        mailSubscriptions.updateSubscriptions(userAccount, listOf("job-failed", "job-success"))
        mailSubscriptions.updateSubscriptions(oma.refreshOrFail(userAccount), emptyList())

        val reloadedAccount = oma.refreshOrFail(userAccount)
        assertTrue(mailSubscriptions.getSubscribedMailTypes(reloadedAccount).isEmpty())
    }

    @BeforeEach
    fun createUserAccount() {
        // A separate account is used, as the shared test user must not be modified.
        userAccount = SQLUserAccount()
        userAccount.tenant.setValue(TenantsHelper.getTestTenant())
        userAccount.userAccountData.login.username = "mail-subscriptions-" + UUID.randomUUID()
        oma.update(userAccount)
    }

    @AfterEach
    fun deleteUserAccount() {
        oma.delete(userAccount)
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var oma: OMA

        @Part
        @JvmStatic
        private lateinit var mailSubscriptions: MailSubscriptions

        @BeforeAll
        @JvmStatic
        fun setup() {
            oma.readyFuture.await(Duration.ofSeconds(60))
        }
    }
}
