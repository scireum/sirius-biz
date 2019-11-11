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
 * Implements an {@link VFSRoot} to trigger jobs with an ftp file upload.
 */
@Register
public class JobsRoot implements VFSRoot {

    @Part
    private JobPresets jobPresets;

    @Part
    private Jobs jobs;

    @Part
    private VirtualFileSystem vfs;


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
        MutableVirtualFile file = new MutableVirtualFile(parent, name);

        file.withCanCreateChildren(ignored -> true);
        file.withCanProvideInputStream(ignored -> false);
        file.withCanProvideOutputStream(ignored -> false);
        file.withDirectoryFlagSupplier(ignored -> true);
        file.withExistsFlagSupplier(ignored -> true);
        file.withCanRenameHandler(ignored -> false);
        file.withCanDeleteHandler(ignored -> false);
        file.withCanMoveHandler(ignored -> false);
        file.withDeleteHandler(ignored -> false);
        file.withCanCreateDirectoryHandler(ignored -> false);

        file.withChildren(this);

        return file;
    }

    private VirtualFile createPlaceholder(VirtualFile parent, String name) {
        MutableVirtualFile file = new MutableVirtualFile(parent, name);
        file.withCanProvideOutputStream(ignored -> true);
        file.withDirectoryFlagSupplier(ignored -> false);
        file.withOutputStreamSupplier(ignored -> {
            VirtualFile storedFile = createFile(name);
            JobPreset jobPreset = getPreset(parent);
            OutputStream out = storedFile.createOutputStream();

            return overrideOutputStream(out, jobPreset, storedFile);
        });

        return file;
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

    private VirtualFile createFile(String name) {
        VirtualFile work = vfs.resolve("/work");

        return work.findChild(name)
                .orElseThrow(() -> Exceptions.handle()
                        .withSystemErrorMessage(
                                "FTP Job Trigger: The virtual file with name %s could not be created",
                                name)
                        .handle());
    }

    private enum FileLevel {
        ROOT, SPACE, JOB, PRESET, FILE, UNKNOWN
    }
}
