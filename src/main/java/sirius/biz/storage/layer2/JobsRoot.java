package sirius.biz.storage.layer2;

import sirius.biz.jobs.JobConfigData;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.Jobs;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.presets.JobPresets;
import sirius.biz.storage.layer3.*;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides a {@link VFSRoot} to trigger jobs with an FTP file upload.
 */
@Register
public class JobsRoot implements VFSRoot {

    @Part
    private JobPresets jobPresets;

    @Part
    private Jobs jobs;

    @Part
    private VirtualFileSystem virtualFileSystem;

    private static final String JOBS_LABEL = "jobs";

    private List<JobFactory> jobsWithFileParameters = null;

    @Override
    public Optional<VirtualFile> findChild(VirtualFile parent, String name) {
        if (!UserContext.getCurrentUser().hasPermission(Jobs.PERMISSION_EXECUTE_JOBS)) {
            return Optional.empty();
        }

        VirtualFile directory = createDirectory(parent, name);
        FileLevel level = determineFolderLevel(directory);

        if (level == FileLevel.SPACE || level == FileLevel.JOB || level == FileLevel.PRESET) {
            return Optional.of(directory);
        }

        if (level == FileLevel.FILE) {
            return Optional.of(createPlaceholder(parent, name));
        }

        return Optional.empty();
    }

    private FileLevel determineFolderLevel(@Nonnull VirtualFile file) {
        if (file.isFile()) {
            return FileLevel.UNKNOWN;
        }

        if (file.parent() == null) {
            return FileLevel.ROOT;
        }

        FileLevel level = determineFolderLevel(file.parent());

        if (level == FileLevel.ROOT && Strings.areEqual(file.name(), JOBS_LABEL)) {
            return FileLevel.SPACE;
        }

        if (level == FileLevel.SPACE && isValidJob(file)) {
            return FileLevel.JOB;
        }

        if (level == FileLevel.JOB && isValidPreset(file)) {
            return FileLevel.PRESET;
        }

        if (level == FileLevel.PRESET) {
            return FileLevel.FILE;
        }

        return FileLevel.UNKNOWN;
    }

    private List<JobFactory> getPossibleJobs() {
        if (jobsWithFileParameters == null) {
            jobsWithFileParameters = jobs.getAvailableJobs(null)
                    .filter(JobFactory::canStartInBackground)
                    .filter(this::isFileJob)
                    .collect(Collectors.toList());
        }

        return jobsWithFileParameters;
    }

    private boolean isFileJob(JobFactory factory) {
        return factory.getParameters().stream().filter(parameter -> parameter instanceof FileParameter).count() == 1;
    }

    private boolean isValidJob(VirtualFile dir) {
        return getPossibleJobs().stream().anyMatch(jobFactory -> Strings.areEqual(jobFactory.getName(), dir.name()));
    }

    private boolean isValidPreset(VirtualFile dir) {
        return getPresetsOfJob(dir.parent()).stream()
                .anyMatch(preset -> Strings.areEqual(preset.getJobConfigData().getLabel(),
                        dir.name()));
    }

    private List<? extends JobPreset> getPresetsOfJob(VirtualFile parentDir) {
        JobFactory jobFactory = getJobFactory(parentDir);
        return jobPresets.fetchPresets(jobFactory);
    }

    private JobFactory getJobFactory(VirtualFile dir) {
        return getPossibleJobs().stream()
                .filter(job -> Strings.areEqual(job.getName(), dir.name()))
                .findFirst()
                .orElseThrow(() -> Exceptions.handle()
                        .withSystemErrorMessage(
                                "Could not find the corresponding job with name %s",
                                dir.name())
                        .handle());
    }

    private JobPreset getPreset(VirtualFile dir) {
        return getPresetsOfJob(dir.parent()).stream()
                .filter(preset -> Strings.areEqual(preset.getJobConfigData().getLabel(),
                        dir.name()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        if (!UserContext.getCurrentUser().hasPermission(Jobs.PERMISSION_EXECUTE_JOBS)) {
            return;
        }

        FileLevel level = determineFolderLevel(parent);
        if (level == FileLevel.ROOT) {
            search.processResult(createDirectory(parent, JOBS_LABEL));
            return;
        }

        if (level == FileLevel.SPACE) {
            getPossibleJobs().stream()
                    .map(JobFactory::getName)
                    .map(name -> createDirectory(parent, name))
                    .forEach(search::processResult);
            return;
        }

        if (level == FileLevel.JOB) {
            getPresetsOfJob(parent).stream()
                    .map(JobPreset::getJobConfigData)
                    .map(JobConfigData::getLabel)
                    .map(name -> createDirectory(parent, name))
                    .forEach(search::processResult);
        }
    }

    @Override
    public int getPriority() {
        return 0;
    }

    private VirtualFile createDirectory(VirtualFile parent, String name) {
        MutableVirtualFile directory = new MutableVirtualFile(parent, name);

        directory.withCanCreateChildren(ignored -> true);
        directory.withCanProvideInputStream(ignored -> false);
        directory.withCanProvideOutputStream(ignored -> false);
        directory.withDirectoryFlagSupplier(ignored -> true);
        directory.withExistsFlagSupplier(ignored -> true);
        directory.withCanRenameHandler(ignored -> false);
        directory.withCanDeleteHandler(ignored -> false);
        directory.withCanMoveHandler(ignored -> false);
        directory.withDeleteHandler(ignored -> false);
        directory.withCanCreateDirectoryHandler(ignored -> false);

        directory.withChildren(this);

        return directory;
    }

    private VirtualFile createPlaceholder(VirtualFile parent, String name) {
        MutableVirtualFile placeholder = new MutableVirtualFile(parent, name);
        placeholder.withCanProvideOutputStream(ignored -> true);
        placeholder.withDirectoryFlagSupplier(ignored -> false);
        placeholder.withOutputStreamSupplier(ignored -> {
            VirtualFile storedFile = createFile(name);
            JobPreset jobPreset = getPreset(parent);
            OutputStream out = storedFile.createOutputStream();

            return overrideOutputStream(out, jobPreset, storedFile);
        });

        return placeholder;
    }

    private VirtualFile createFile(String name) {
        VirtualFile work = virtualFileSystem.resolve("/work");

        return work.findChild(name)
                .orElseThrow(() -> Exceptions.handle()
                        .withSystemErrorMessage(
                                "FTP Job Trigger: The virtual file with name %s could not be created",
                                name)
                        .handle());
    }

    private OutputStream overrideOutputStream(OutputStream streamToOverride, JobPreset jobPreset, VirtualFile file) {
        return new OutputStream() {
            private OutputStream out = streamToOverride;

            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }

            @Override
            public void close() throws IOException {
                if (out != null) {
                    out.close();
                    triggerJobWithPreset(jobPreset, file.path());
                }

                out = null;
            }
        };
    }

    private void triggerJobWithPreset(JobPreset preset, String path) {
        JobFactory jobFactory = preset.getJobConfigData().getJobFactory();

        String fileParameterName = jobFactory.getParameters()
                .stream()
                .filter(p -> p instanceof FileParameter)
                .map(Parameter::getName)
                .findFirst()
                .orElse("");

        jobFactory.startInBackground(param -> {
            if (Strings.isFilled(fileParameterName) && Strings.areEqual(param, fileParameterName)) {
                return Value.of(path);
            }

            return Value.of(preset.getJobConfigData().getConfigMap().get(param));
        });
    }

    /**
     * Categorizes a {@link VirtualFile} into a specific file level depending on its path from the root directory.
     */
    private enum FileLevel {
        /**
         * The root file level is used for a {@link VirtualFile directory} without a parent.
         */
        ROOT,

        /**
         * Used only for {@link VirtualFile directories} with a parent directory of the {@link FileLevel#ROOT} level.
         */
        SPACE,

        /**
         * Used only for {@link VirtualFile directories} with a parent directory of the {@link FileLevel#SPACE} level.
         */
        JOB,

        /**
         * Used only for {@link VirtualFile directories} with a parent directory of the {@link FileLevel#JOB} level.
         */
        PRESET,

        /**
         * Used only for {@link VirtualFile files} with a parent directory of the {@link FileLevel#PRESET} level.
         */
        FILE,

        /**
         * Used if no other file level could be determined.
         */
        UNKNOWN
    }
}
