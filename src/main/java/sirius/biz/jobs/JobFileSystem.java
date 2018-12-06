/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.vfs.VFSRoot;
import sirius.biz.vfs.VirtualFile;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import java.util.function.Consumer;

@Register
public class JobFileSystem implements VFSRoot {

    @Part
    private Jobs jobs;

    @Override
    public void collectRootFolders(VirtualFile parent, Consumer<VirtualFile> fileCollector) {
        fileCollector.accept(new VirtualFile(parent, "jobs").withChildren((jobsFile, collector) -> {
            jobs.getAvailableJobs(null).filter(this::hasProperParameters).forEach(job -> {
                VirtualFile jobFile = new VirtualFile(jobsFile, job.getName());
                jobFile.withOutputStreamSupplier(null);
                collector.accept(jobFile);
            });
        }));
    }

    private boolean hasProperParameters(JobFactory jobFactory) {
        return true;
    }
}
