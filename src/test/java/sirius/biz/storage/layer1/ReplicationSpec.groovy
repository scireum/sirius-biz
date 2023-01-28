/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1

import org.junit.jupiter.api.Tag
import sirius.biz.storage.layer1.replication.ReplicationBackgroundLoop
import sirius.kernel.BaseSpecification
import sirius.kernel.Tags
import sirius.kernel.async.BackgroundLoop
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part

import java.nio.charset.StandardCharsets
import java.time.Duration

@Tag(Tags.NIGHTLY)
class ReplicationSpec extends BaseSpecification {

    @Part
    private static ObjectStorage storage

    def awaitReplication() {
        BackgroundLoop.nextExecution(ReplicationBackgroundLoop.class).await(Duration.ofMinutes(1))
        // Give the sync some time to actually complete its tasks...
        Wait.seconds(10)
    }

    def "updates are replicated correctly"() {
        given:
        def testData = "test".getBytes(StandardCharsets.UTF_8)
        when:
        storage.getSpace("repl-primary").upload("repl-update-test", new ByteArrayInputStream(testData), testData.length)
        and:
        awaitReplication()
        def downloaded = storage.getSpace("reply-secondary").download("repl-update-test")
        then:
        downloaded.isPresent()
        and:
        new InputStreamReader(downloaded.get().getInputStream(), StandardCharsets.UTF_8).readLine() == "test"
    }

    def "deletes are replicated correctly"() {
        given:
        def testData = "test".getBytes(StandardCharsets.UTF_8)
        when:
        storage.getSpace("repl-primary").upload("repl-delete-test", new ByteArrayInputStream(testData), testData.length)
        and:
        awaitReplication()
        and:
        storage.getSpace("repl-primary").delete("repl-delete-test")
        and:
        awaitReplication()
        def downloaded = storage.getSpace("reply-secondary").download("repl-delete-test")
        then:
        !downloaded.isPresent()
    }

}
