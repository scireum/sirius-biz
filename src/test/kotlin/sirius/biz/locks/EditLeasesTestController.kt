/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import sirius.kernel.di.std.Part
import sirius.kernel.di.std.Register
import sirius.web.controller.BasicController
import sirius.web.controller.Routed
import sirius.web.http.WebContext
import sirius.web.services.InternalService
import sirius.web.services.JSONStructuredOutput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exposes [EditLeases] via routes, as an editor would, so that [EditLeasesTest] can verify the HTTP contract.
 */
@Register
class EditLeasesTestController : BasicController() {

    @Part
    private lateinit var editLeases: EditLeases

    /**
     * Performs a lease action like the lease endpoint of an editor.
     */
    @Routed("/test/edit-lease/:1")
    @InternalService
    fun lease(webContext: WebContext, output: JSONStructuredOutput, id: String) {
        editLeases.handleAction(webContext, output, SCOPE, id)
    }

    /**
     * Applies a (simulated) write like a write route of an editor.
     */
    @Routed("/test/edit-lease/:1/write")
    @InternalService
    fun write(webContext: WebContext, output: JSONStructuredOutput, id: String) {
        editLeases.assertHeldBy(webContext, SCOPE, id)
        val started = writeStarted
        val finish = finishWrite
        if (started != null && finish != null) {
            started.countDown()
            finish.await(10, TimeUnit.SECONDS)
        }
        output.property("written", true)
    }

    companion object {
        /**
         * The scope used by all routes of this controller.
         */
        const val SCOPE = "test-editor"

        /**
         * Once set, the write route counts this down after passing the lease check and then blocks until
         * [finishWrite] is counted down, so a test can keep the write in flight.
         */
        @Volatile
        var writeStarted: CountDownLatch? = null

        /**
         * Releases a write blocked after [writeStarted].
         */
        @Volatile
        var finishWrite: CountDownLatch? = null
    }
}
