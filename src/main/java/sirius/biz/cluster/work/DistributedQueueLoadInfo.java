/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.health.CachingLoadInfoProvider;
import sirius.web.health.LoadInfo;

import java.util.function.Consumer;

/**
 * Provides {@link LoadInfo load infos} for all distributed task queues.
 */
@Register
public class DistributedQueueLoadInfo extends CachingLoadInfoProvider {

    @Part
    private DistributedTasks tasks;

    @Override
    protected void computeLoadInfos(Consumer<LoadInfo> consumer) {
        for (DistributedQueueInfo queue : tasks.getQueues()) {
            consumer.accept(new LoadInfo("queue-" + queue.getName(),
                                         queue.getName(),
                                         tasks.getQueueLength(queue.getName())));
        }
    }

    @Override
    public String getLabel() {
        return "Distributed Queue";
    }
}
