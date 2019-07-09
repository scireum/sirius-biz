/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer1

import com.google.common.base.Charsets
import com.google.common.io.CharStreams
import sirius.biz.storage.layer1.replication.ReplicationManager
import sirius.kernel.BaseSpecification
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part

class ReplicationSpec extends BaseSpecification {

    @Part
    private static PhysicalObjectStorage storage

    @Part
    private static ReplicationManager replicationManager

    def awaitReplication() {
        int maxWait = 5
        while (maxWait-- > 0) {
            if (replicationManager.getReplicationTaskStorage().get().countTotalNumberOfTasks() == 0) {
                return
            }

            Wait.seconds(10)
        }

        throw new IllegalStateException("Replication did not complete within 5 attempts")
    }

    def "updates are replicated correctly"() {
        given:
        def testData = "test".getBytes(Charsets.UTF_8)
        when:
        storage.getSpace("repl-primary").upload("test", new ByteArrayInputStream(testData), testData.length)
        and:
        awaitReplication()
        def downloaded = storage.getSpace("reply-secondary").download("test")
        then:
        downloaded.isPresent()
        and:
        CharStreams.toString(new InputStreamReader(downloaded.get().getInputStream(), Charsets.UTF_8)) == "test"
    }

}
