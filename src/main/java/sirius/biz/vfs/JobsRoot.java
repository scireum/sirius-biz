/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.vfs;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.Jobs;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.VirtualObjectParameter;
import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.storage.Storage;
import sirius.biz.storage.StoredObject;
import sirius.biz.tenants.jdbc.SQLTenants;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.io.OutputStream;
import java.util.function.Consumer;

@Register
public class JobsRoot implements VFSRoot {

    @Part
    private JobPresets presets;

    @Part
    private Storage storage;

    @Part
    private SQLTenants tenants;

    @Part
    private Jobs jobs;

    @Override
    public void collectRootFolders(VirtualFile parent, Consumer<VirtualFile> fileCollector) {
        if (presets == null || jobs == null) {
            return;
        }
        UserInfo currentUser = UserContext.getCurrentUser();
        if (!currentUser.hasPermission(Jobs.PERMISSION_EXECUTE_JOBS)) {
            return;
        }

        fileCollector.accept(new VirtualFile(parent, "jobs").withChildren(this::listFileJobs));
    }

    private void listFileJobs(VirtualFile jobsFolder, Consumer<VirtualFile> collector) {
        jobs.getAvailableJobs(null)
            .filter(JobFactory::canStartInBackground)
            .filter(this::isFileJob)
            .forEach(fileJobFactory -> {
                collector.accept(new VirtualFile(jobsFolder,
                                                 fileJobFactory.getName()).withChildren((jobFolder, presetCollector) -> listPresets(
                        fileJobFactory,
                        jobFolder,
                        presetCollector)));
            });
    }

    private void listPresets(JobFactory fileJobFactory, VirtualFile jobFolder, Consumer<VirtualFile> presetCollector) {
        presets.fetchPresets(fileJobFactory).forEach(preset -> {
            VirtualFile virtualFile = new VirtualFile(jobFolder, preset.getJobConfigData().getLabel());
            virtualFile.withChildren((ig, nored) -> {
            });
            virtualFile.withCreateFileHandler(filename -> uploadAndTrigger(fileJobFactory,
                                                                           preset,
                                                                           jobFolder,
                                                                           filename));
            presetCollector.accept(virtualFile);
        });
    }

    private OutputStream uploadAndTrigger(JobFactory fileJobFactory,
                                          JobPreset preset,
                                          VirtualFile jobFolder,
                                          String filename) {
        try {
            StoredObject buffer = storage.findOrCreateObjectByPath(tenants.getRequiredTenant(), "work", filename);
            return storage.updateFile(buffer, () -> trigger(fileJobFactory, preset, buffer));
        } catch (Exception e) {
            throw Exceptions.handle(e);
        }
    }

    private void trigger(JobFactory fileJobFactory, JobPreset preset, StoredObject buffer) {
        String parameterName = fileJobFactory.getParameters()
                                             .stream()
                                             .filter(p -> p instanceof VirtualObjectParameter)
                                             .map(Parameter::getName)
                                             .findFirst()
                                             .orElse(null);

        preset.getJobConfigData().getJobFactory().startInBackground(param -> {
            if (Strings.areEqual(param, parameterName)) {
                return Value.of(buffer.getObjectKey());
            } else {
                return Value.of(preset.getJobConfigData().getConfigMap().get(param));
            }
        });
    }

    private boolean isFileJob(JobFactory factory) {
        return factory.getParameters().stream().filter(parameter -> parameter instanceof VirtualObjectParameter).count()
               == 1;
    }
}
