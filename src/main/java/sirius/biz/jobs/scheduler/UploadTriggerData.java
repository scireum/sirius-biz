package sirius.biz.jobs.scheduler;

import sirius.biz.jobs.batch.file.FileImportJobFactory;
import sirius.biz.storage.StoredObject;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.*;

import java.util.List;

/**
 * Contains a composite which describes the trigger logic of an {@link SchedulerEntry}.
 */
public class UploadTriggerData extends Composite {

    /**
     * Controls if the trigger with a file upload is enabled.
     */
    public static final Mapping ENABLED = Mapping.named("enabled");
    @Autoloaded
    private boolean enabled;

    /**
     * The file which should be watched. If the file is changed the job should be triggered.
     */
    public static final Mapping FILE_TO_WATCH = Mapping.named("fileToWatch");
    @NullAllowed
    @Trim
    @Length(64)
    private String fileToWatch;

    @Transient
    private SchedulerEntry entry;

    public UploadTriggerData(SchedulerEntry entry) {
        this.entry = entry;
    }

    /**
     * Provides a list of all files which are changed.
     * <p>
     * Each entry contains the {@link StoredObject#getObjectKey()} of a file.
     *
     * @param changedFiles list of object keys of the changed files
     * @return <tt>true</tt> if the {@link SchedulerEntry} should be executed, <tt>false</tt> otherwise
     */
    public boolean shouldRun(List<String> changedFiles) {
        return changedFiles.contains(fileToWatch);
    }

    @BeforeSave
    protected void saveFileToWatch() {
        if (!enabled || !showUploadTriggerData()) {
            fileToWatch = null;
            return;
        }

        fileToWatch = entry.getJobConfigData().getConfigMap().get("file");
    }

    /**
     * Controls if the upload trigger data form should be displayed.
     *
     * @return <tt>true</tt> if the data should be shown, <tt>false</tt> otherwise
     */
    public boolean showUploadTriggerData() {
        return entry.getJobConfigData().getJobFactory() instanceof FileImportJobFactory;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFileToWatch() {
        return fileToWatch;
    }

    public void setFileToWatch(String fileToWatch) {
        this.fileToWatch = fileToWatch;
    }
}
