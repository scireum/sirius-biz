/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.work;

import sirius.db.redis.Redis;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.health.Exceptions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class DistributedTasks {

    private Map<String, Semaphore> concurrencyTokens = new ConcurrentHashMap<>();

    @Part
    private NeighborhoodWatch orchestration;

    @Part
    private Redis redis;

    @Parts(DistributedTaskExecutor.class)
    private List<DistributedTaskExecutor> executors;
    private volatile int index = 0;

    public void submitTask(String queue, String... data) {

    }

    public synchronized Runnable fetchWork() {
        if (executors.isEmpty()) {
            return null;
        }

        int initialIndex = index;
        while (true) {
            DistributedTaskExecutor executor = executors.get(index++);
            if (index >= executors.size()) {
                index = 0;
            }
            if (orchestration.isBackgroundJobLocallyEnabled(executor.getName())) {
                if (orchestration.isBackgroundJobGloballyEnabled(executor.getName())) {
                    if (aquireConcurrencyToken(executor)) {
                        try {
                            Runnable work = pullWork(executor);
                            if (work != null) {
                                return work;
                            } else {
                                releaseConcurrencyToken(executor);
                            }
                        } catch (Exception e) {
                            releaseConcurrencyToken(executor);
                            Exceptions.handle(e);
                        }
                    }
                }
            }

            if (initialIndex == index) {
                return null;
            }
        }
    }

    private void releaseConcurrencyToken(DistributedTaskExecutor executor) {
        String concurrencyToken = executor.getConcurrencyToken();
        if (Strings.isEmpty(concurrencyToken)) {
            return;
        }
        getSemaphore(concurrencyToken).release();
    }

    private boolean aquireConcurrencyToken(DistributedTaskExecutor executor) {
        String concurrencyToken = executor.getConcurrencyToken();
        if (Strings.isEmpty(concurrencyToken)) {
            return true;
        }
        return getSemaphore(concurrencyToken).tryAcquire();
    }

    private Semaphore getSemaphore(String name) {
        return null ; //concurrencyTokens.computeIfAbsent(name, () -> new Semaphore());
    }

    private Runnable pullWork(DistributedTaskExecutor executor) {
//        String task = redis.pollQueue(executor.getName());
//        if (task == null) {
//            return null;
//        }
//
//        return () -> {
//            try {
//                executor.execute(Values.of(task.split("\\|")));
//            } catch (Exception e) {
//                //TODO
//            } finally {
//                releaseConcurrencyToken(executor);
//            }
//        };
        return null;
    }
}
