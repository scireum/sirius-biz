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
import sirius.web.http.QueryString;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    /**
     * Initializes the path matcher used to filter files using the GLOB pattern.
     * <p>
     * We do not want to force end-user to provide full paths (including the starting folder). This makes sure that
     * inputs like *.jpg, /foo/*.jpg and foo/*.* matches these patterns inside the subtree
     *
     * @param filter the string to use as filter
     */
    private void initializePathMatcher(String filter) {
        String pattern = "glob:**";
        if (!filter.startsWith("/")) {
            pattern += "/";
        }
        pathMatcher = FileSystems.getDefault().getPathMatcher(pattern + filter);
    }

    /**
     * Handles the given directory.
     * <p>
     * As first step, this function is called recursively for all child directories.
     * Then all child files are processed, and finally the directory itself is deleted if it was left empty, and we
     * are allowed to do so (nothing was skipped and the user requested the deletion of empty directories).
     * <p>
     * Note that in non-recursive mode, only the children of the start directory are processed.
     *
     * @param directory the  {@link VirtualFile} representing a directory
     * @return <tt>true</tt> if the directory has been deleted, <tt>false</tt> otherwise
     */
    private boolean handleDirectory(VirtualFile directory) {
        Monoflop childSkipped = Monoflop.create();
        boolean isRoot = "/".equals(directory.parent().path());

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

        if (!recursive || isRoot) {
            // non-recursive mode will not delete the start directory itself, the same for the root directory,
            // so we early abort here.
            return false;
        }

        if (childSkipped.isToggled() && deleteEmpty) {
            // We want to delete empty directories, but some children were skipped, so we can't delete this directory.
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.directory.notEmpty")
                                  .withContext(PATH_KEY, directory.path())
                                  .withMessageType("$DeleteFilesJob.warn.notEmpty"));
            incrementDirectorySkipped();
            return false;
        }

        if (!deleteEmpty) {
            // No children were skipped, but we are not allowed to delete empty dirs.
            incrementDirectorySkipped();
            return false;
        }

        return deleteVirtualFile(directory);
    }

    /**
     * Handles the given file.
     *
     * @param file the  {@link VirtualFile} representing a file
     * @return <tt>true</tt> if the file has been deleted, <tt>false</tt> otherwise
     */
    private boolean handleFile(VirtualFile file) {
        if (pathMatcher != null && !pathMatcher.matches(Path.of(file.path()))) {
            incrementFileSkipped();
            return false;
        }

        if (lastModifiedBefore != null && file.lastModifiedDate() != null && !file.lastModifiedDate()
                                                                                  .isBefore(lastModifiedBefore)) {
            incrementFileSkipped();
            return false;
        }

        if (file.readOnly()) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.file.readOnly")
                                  .withContext(PATH_KEY, file.path())
                                  .withMessageType("$DeleteFilesJob.warn.readOnly"));
            incrementFileSkipped();
            return false;
        }

        if (onlyUnused && vfs.isInUse(file)) {
            process.log(ProcessLog.warn()
                                  .withNLSKey("DeleteFilesJob.file.inUse")
                                  .withContext(PATH_KEY, file.path())
                                  .withMessageType("$DeleteFilesJob.warn.inUse"));
            incrementFileSkipped();
            return false;
        }

        return deleteVirtualFile(file);
    }

    /**
     * Deletes the given virtual file.
     *
     * @param file a {@link VirtualFile} to delete
     * @return <tt>true</tt> if the file has been deleted, <tt>false</tt> otherwise
     */
    private boolean deleteVirtualFile(VirtualFile file) {
        boolean isDirectory = file.isDirectory();
        try {
            if (!file.canDelete()) {
                String messageKey =
                        isDirectory ? "DeleteFilesJob.directory.cannotDelete" : "DeleteFilesJob.file.cannotDelete";
                process.log(ProcessLog.warn()
                                      .withNLSKey(messageKey)
                                      .withContext(PATH_KEY, file.path())
                                      .withMessageType("$DeleteFilesJob.warn.cannotDelete"));
                if (isDirectory) {
                    incrementDirectorySkipped();
                } else {
                    incrementFileSkipped();
                }
                return false;
            }

            if (!simulation) {
                file.delete();
            }
            String messageKey = isDirectory ? "DeleteFilesJob.directory.deleted" : "DeleteFilesJob.file.deleted";
            process.log(ProcessLog.info().withNLSKey(messageKey).withContext(PATH_KEY, file.path()));
            process.incCounter(isDirectory ?
                               "DeleteFilesJob.count.directoriesDeleted" :
                               "DeleteFilesJob.count.filesDeleted");
        } catch (HandledException exception) {
            process.log(ProcessLog.error().withMessage(exception.getMessage()));
            return false;
        }

        return true;
    }

    private void incrementFileSkipped() {
        process.incCounter("DeleteFilesJob.count.filesSkipped");
    }

    private void incrementDirectorySkipped() {
        process.incCounter("DeleteFilesJob.count.directoriesSkipped");
    }

    /**
     * Defines a factory to create new instances of {@link DeleteFilesJob}.
     */
    @Register(framework = StorageUtils.FRAMEWORK_STORAGE)
    @Permission(VirtualFileSystemController.PERMISSION_VIEW_FILES)
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
            return "fa-solid fa-trash";
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
            return PersistencePeriod.THREE_MONTHS;
        }

        @Override
        protected BatchJob createJob(ProcessContext process) throws Exception {
            return new DeleteFilesJob(process);
        }

        @Override
        protected void computePresetFor(@Nonnull QueryString queryString,
                                        @Nullable Object targetObject,
                                        Map<String, Object> preset) {
            if (targetObject instanceof VirtualFile virtualFile && virtualFile.isDirectory()) {
                preset.put(SOURCE_PATH_PARAMETER.getName(), virtualFile.path());
            }
            super.computePresetFor(queryString, targetObject, preset);
        }

        @Override
        protected boolean hasPresetFor(@Nonnull QueryString queryString, @Nullable Object targetObject) {
            return (targetObject instanceof VirtualFile virtualFile
                    && virtualFile.isDirectory()
                    && virtualFile.canDelete());
        }

        @Nonnull
        @Override
        public String getName() {
            return "delete-files";
        }
    }
}
