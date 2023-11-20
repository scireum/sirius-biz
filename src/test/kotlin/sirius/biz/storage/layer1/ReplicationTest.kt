/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.biz.storage.layer1.replication.ReplicationBackgroundLoop
import sirius.biz.storage.s3.ObjectStores
import sirius.kernel.BaseSpecification
import sirius.kernel.SiriusExtension
import sirius.kernel.Tags
import sirius.kernel.async.BackgroundLoop
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
/**
 * Tests the [ReplicationBackgroundLoop].
 */
@Tag(Tags.NIGHTLY)
@ExtendWith(SiriusExtension::class)
class ReplicationTest {
    @Test
    fun `updates are replicated correctly`() {
        val testData = "test".toByteArray(StandardCharsets.UTF_8)
        storage.getSpace("repl-primary")
            .upload("repl-update-test", ByteArrayInputStream(testData), testData.size.toLong())
        awaitReplication()
        val downloaded = storage.getSpace("reply-secondary").download("repl-update-test")
        assertTrue { downloaded.isPresent() }

        val res = InputStreamReader(downloaded.get().getInputStream(), StandardCharsets.UTF_8)
        assertEquals("test", InputStreamReader(downloaded.get().getInputStream(), StandardCharsets.UTF_8).readText())
    }

    @Test
    fun `deletes are replicated correctly`() {

        val testData = "test".toByteArray(StandardCharsets.UTF_8)
        storage.getSpace("repl-primary")
            .upload("repl-delete-test", ByteArrayInputStream(testData), testData.size.toLong())

        awaitReplication()
        storage.getSpace("repl-primary").delete("repl-delete-test")
        awaitReplication()
        val downloaded = storage.getSpace("reply-secondary").download("repl-delete-test")
        assertFalse { downloaded.isPresent() }
    }

    companion object {
        @Part
        @JvmStatic
        private lateinit var storage: ObjectStorage

        fun awaitReplication() {
            BackgroundLoop.nextExecution(
                ReplicationBackgroundLoop::class.java
            ).await(Duration.ofMinutes(1))
            // Give the sync some time to actually complete its tasks...
            Wait.seconds(10.0)
        }

    }

}
