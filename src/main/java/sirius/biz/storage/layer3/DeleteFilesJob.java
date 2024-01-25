/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.jobs.batch.DefaultBatchProcessFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.FileParameter;
import sirius.biz.jobs.params.LocalDateParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.StringParameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Defines a job which deletes files in the background.
 */
public class DeleteFilesJob extends BatchJob {

    private static final Parameter<VirtualFile> SOURCE_PATH_PARAMETER =
            new FileParameter("sourcePath", "$DeleteFilesJob.sourcePath").withDescription(
                    "$DeleteFilesJob.sourcePath.help").directoriesOnly().markRequired().build();

    private static final Parameter<Boolean> SIMULATION_PARAMETER =
            new BooleanParameter("simulation", "$DeleteFilesJob.simulation").withDescription(
                    "$DeleteFilesJob.simulation.help").withDefaultTrue().build();

    private static final Parameter<Boolean> DELETE_RECURSIVE_PARAMETER =
            new BooleanParameter("recursive", "$DeleteFilesJob.recursive").withDescription(
                    "$DeleteFilesJob.recursive.help").withDefaultTrue().build();

    private static final Parameter<Boolean> DELETE_EMPTY_DIRECTORIES_PARAMETER =
            new BooleanParameter("deleteEmpty", "$DeleteFilesJob.deleteEmpty").withDescription(
                                                                                      "$DeleteFilesJob.deleteEmpty.help")
                                                                              .withDefaultTrue()
                                                                              .hideWhenFalseOrEmpty(
                                                                                      DELETE_RECURSIVE_PARAMETER)
                                                                              .build();

    private static final Parameter<String> PATH_FILTER_PARAMETER =
            new StringParameter("filter", "$DeleteFilesJob.pathFilter").withDescription(
                    "$DeleteFilesJob.pathFilter.help").build();

    private static final Parameter<LocalDate> LAST_MODIFIED_BEFORE_PARAMETER =
            new LocalDateParameter("lastModifiedBefore", "$DeleteFilesJob.lastModifiedBefore").withDescription(
                    "$DeleteFilesJob.lastModifiedBefore.help").build();

    private static final Parameter<Boolean> ONLY_UNUSED_PARAMETER =
            new BooleanParameter("onlyUnused", "$DeleteFilesJob.onlyUnused").withDescription(
                    "$DeleteFilesJob.onlyUnused.help").withDefaultTrue().build();

    private static final String PATH_KEY = "path";

    private boolean simulation;
    private boolean recursive;
    private boolean deleteEmpty;
    private LocalDateTime lastModifiedBefore;
    private PathMatcher pathMatcher;
    private boolean onlyUnused;

    @Part
    private static VirtualFileSystem vfs;

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected DeleteFilesJob(ProcessContext process) {
        super(process);
    }

    @Override
    public void execute() throws Exception {
        VirtualFile sourcePath = process.require(SOURCE_PATH_PARAMETER);
        if (!sourcePath.exists() || !sourcePath.isDirectory()) {
            process.log(ProcessLog.error()
                                  .withNLSKey("DeleteFilesJob.sourcePath.invalid")
                                  .withContext(PATH_KEY, sourcePath.path()));
            return;
        }

        simulation = process.require(SIMULATION_PARAMETER);
        recursive = process.require(DELETE_RECURSIVE_PARAMETER);
        deleteEmpty = process.require(DELETE_EMPTY_DIRECTORIES_PARAMETER);
        onlyUnused = process.require(ONLY_UNUSED_PARAMETER);
        process.getParameter(PATH_FILTER_PARAMETER).ifPresent(this::initializePathMatcher);
        process.getParameter(LAST_MODIFIED_BEFORE_PARAMETER)
               .ifPresent(date -> lastModifiedBefore = date.atStartOfDay());
        handleDirectory(sourcePath);
    }

    private void initializePathMatcher(String filter) {
        // We do not want to force end-user to provide full paths (including the starting folder).
        // this makes sure that inputs like *.jpg, /foo/*.jpg and foo/*.* matches these patterns
        // inside the subtree
        String pattern = "glob:**";
        if (!filter.startsWith("/")) {
            pattern += "/";
        }
        pathMatcher = FileSystems.getDefault().getPathMatcher(pattern + filter);
    }

    private boolean handleDirectory(VirtualFile directory) {
        Monoflop childSkipped = Monoflop.create();

        if (recursive) {
            // Only enters child directories in recursive mode
            directory.allChildren().excludeFiles().subTreeOnly().maxDepth(1).iterate(subDirectory -> {
                if (!handleDirectory(subDirectory)) {
                    childSkipped.toggle();
                }
                return process.isActive();
            });
        }

        directory.allChildren().excludeDirectories().subTreeOnly().maxDepth(1).iterate(file -> {
            if (!handleFile(file)) {
                childSkipped.toggle();
            }
            return process.isActive();
        });

        if (!recursive) {
            // non-recursive mode will not delete the start directory itself, so we early abort here.
            return false;
        }

        if (childSkipped.isToggled() && deleteEmpty) {
            // We want to delete empty directories, but some children were skipped, so we can't delete this directory.
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.directory.notEmpty")
                                  .withContext(PATH_KEY, directory.path()));
            return false;
        }

        if (!deleteEmpty) {
            // No children were skipped, but we are not allowed to delete empty dirs.
            return false;
        }

        return deleteVirtualFile(directory);
    }

    private boolean handleFile(VirtualFile file) {
        if (pathMatcher != null && !pathMatcher.matches(Path.of(file.path()))) {
            return false;
        }

        if (lastModifiedBefore != null && file.lastModifiedDate() != null && !file.lastModifiedDate()
                                                                                  .isBefore(lastModifiedBefore)) {
            return false;
        }

        if (file.readOnly()) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.file.readOnly")
                                  .withContext(PATH_KEY, file.path()));
            return false;
        }

        if (onlyUnused && vfs.isInUse(file)) {
            process.log(ProcessLog.warn().withNLSKey("DeleteFilesJob.file.inUse").withContext(PATH_KEY, file.path()));
            return false;
        }

        return deleteVirtualFile(file);
    }

    private boolean deleteVirtualFile(VirtualFile file) {
        try {
            if (!file.canDelete()) {
                String messageKey = file.isDirectory() ?
                                    "DeleteFilesJob.directory.cannotDelete" :
                                    "DeleteFilesJob.file.cannotDelete";
                process.log(ProcessLog.warn().withNLSKey(messageKey).withContext(PATH_KEY, file.path()));
                return false;
            }

            if (!simulation) {
                file.delete();
            }

            String messageKey = file.isDirectory() ? "DeleteFilesJob.directory.deleted" : "DeleteFilesJob.file.deleted";
            process.log(ProcessLog.info().withNLSKey(messageKey).withContext(PATH_KEY, file.path()));
        } catch (HandledException exception) {
            process.log(ProcessLog.error().withMessage(exception.getMessage()));
            return false;
        }

        return true;
    }

    /**
     * Defines a factory to create new instances of {@link DeleteFilesJob}.
     */
    @Register(framework = StorageUtils.FRAMEWORK_STORAGE)
    public static class DeleteFilesJobFactory extends DefaultBatchProcessFactory {

        @Override
        protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
            parameterCollector.accept(SOURCE_PATH_PARAMETER);
            parameterCollector.accept(DELETE_RECURSIVE_PARAMETER);
            parameterCollector.accept(DELETE_EMPTY_DIRECTORIES_PARAMETER);
            parameterCollector.accept(PATH_FILTER_PARAMETER);
            parameterCollector.accept(LAST_MODIFIED_BEFORE_PARAMETER);
            parameterCollector.accept(ONLY_UNUSED_PARAMETER);
            parameterCollector.accept(SIMULATION_PARAMETER);
        }

        @Override
        public String getCategory() {
            return StandardCategories.MISC;
        }

        @Override
        public String getIcon() {
            return "fa fa-trash";
        }

        @Override
        protected String createProcessTitle(Map<String, String> context) {
            if (SIMULATION_PARAMETER.get(context).orElse(false)) {
                return Strings.apply("%s (%s) - %s",
                                     getLabel(),
                                     SIMULATION_PARAMETER.getLabel(),
                                     context.get(SOURCE_PATH_PARAMETER.getName()));
            }
            return Strings.apply("%s - %s", getLabel(), context.get(SOURCE_PATH_PARAMETER.getName()));
        }

        @Override
        protected PersistencePeriod getPersistencePeriod() {
            return PersistencePeriod.ONE_YEAR;
        }

        @Override
        protected BatchJob createJob(ProcessContext process) throws Exception {
            return new DeleteFilesJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "delete-files";
        }
    }
}
