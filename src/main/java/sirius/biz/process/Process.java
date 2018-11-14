/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.elastic.SearchContent;
import sirius.biz.elastic.SearchableEntity;
import sirius.biz.process.output.ProcessOutput;
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.AfterDelete;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.NestedList;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class Process extends SearchableEntity {

    private static final long MIN_COMPLETION_TIME_TO_DISABLE_AUTO_REFRESH = 20;
    private static final Duration ACTIVE_INTERVAL = Duration.ofMinutes(5);

    public static final Mapping TITLE = Mapping.named("title");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String title;

    public static final Mapping ICON = Mapping.named("icon");
    @NullAllowed
    private String icon;

    public static final Mapping STATE_MESSAGE = Mapping.named("stateMessage");
    @SearchContent
    @NullAllowed
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String stateMessage;

    public static final Mapping REQUIRED_PERMISSION = Mapping.named("requiredPermission");
    @NullAllowed
    private String requiredPermission;

    public static final Mapping PROCESS_TYPE = Mapping.named("processType");
    @NullAllowed
    private String processType;

    public static final Mapping USER_ID = Mapping.named("userId");
    @NullAllowed
    private String userId;

    public static final Mapping USER_NAME = Mapping.named("userName");
    @SearchContent
    @NullAllowed
    private String userName;

    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    private String tenantId;

    public static final Mapping TENANT_NAME = Mapping.named("tenantName");
    @SearchContent
    private String tenantName;

    public static final Mapping CONTEXT = Mapping.named("context");
    private final StringMap context = new StringMap();

    public static final Mapping LINKS = Mapping.named("links");
    private final NestedList<ProcessLink> links = new NestedList<>(ProcessLink.class);

    public static final Mapping OUTPUTS = Mapping.named("outputs");
    private final NestedList<ProcessOutput> outputs = new NestedList<>(ProcessOutput.class);

    public static final Mapping FILES = Mapping.named("files");
    private final NestedList<ProcessFile> files = new NestedList<>(ProcessFile.class);

    public static final Mapping COUNTERS = Mapping.named("counters");
    private final StringMap counters = new StringMap();

    public static final Mapping STARTED = Mapping.named("started");
    @NullAllowed
    private LocalDateTime started;

    public static final Mapping CANCELED = Mapping.named("canceled");
    @NullAllowed
    private LocalDateTime canceled;

    public static final Mapping COMPLETED = Mapping.named("completed");
    @NullAllowed
    private LocalDateTime completed;

    public static final Mapping ERRORNEOUS = Mapping.named("errorneous");
    private boolean errorneous;

    public static final Mapping STATE = Mapping.named("state");
    private ProcessState state;

    @Part
    private static Processes processes;

    @AfterDelete
    protected void onDelete() {
        files.forEach(file -> {
            try {
                processes.getStorage().delete(this, file);
            } catch (Exception e) {
                Exceptions.handle()
                          .to(Log.BACKGROUND)
                          .withSystemErrorMessage("Failed to delete process file: %s for process: %s - %s",
                                                  file.getFileId(),
                                                  getId(),
                                                  e.getMessage())
                          .handle();
            }
        });
    }

    public static String formatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }

        if (timestamp.toLocalDate().equals(LocalDate.now())) {
            return NLS.toUserString(timestamp.toLocalTime());
        }

        return NLS.toUserString(timestamp);
    }

    protected String formatDuration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return "";
        }

        long absSeconds = Duration.between(from, to).getSeconds();
        return Strings.apply("%02d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
    }

    public String getStartedAsString() {
        return formatTimestamp(getStarted());
    }

    public String getCompletedAsString() {
        return formatTimestamp(getCompleted());
    }

    public String getRuntimeAsString() {
        return formatDuration(getStarted(), getCompleted());
    }

    public boolean shouldAutorefresh() {
        if (getCompleted() == null || getState() != ProcessState.TERMINATED) {
            return true;
        }

        return Duration.between(getCompleted(), LocalDateTime.now()).getSeconds()
               < MIN_COMPLETION_TIME_TO_DISABLE_AUTO_REFRESH;
    }

    /**
     * Determines the bootstrap CSS class to be used for rendering the row of this process.
     */
    public String getRowClass() {
        if (state == ProcessState.STANDBY) {
            if (Duration.between(getStarted(), LocalDateTime.now()).compareTo(ACTIVE_INTERVAL) <= 0) {
                return "info";
            } else {
                return "default";
            }
        }

        if (state == ProcessState.RUNNING && !errorneous) {
            return "info";
        }

        if (state == ProcessState.CANCELED || (state == ProcessState.RUNNING && errorneous)) {
            return "warning";
        }

        if (errorneous) {
            return "danger";
        } else {
            return "success";
        }
    }

    public String getLabelClass() {
        if (state == ProcessState.STANDBY) {
            if (Duration.between(getStarted(), LocalDateTime.now()).compareTo(ACTIVE_INTERVAL) <= 0) {
                return "label-info";
            } else {
                return "";
            }
        }

        if (state == ProcessState.RUNNING && !errorneous) {
            return "label-info";
        }

        if (state == ProcessState.CANCELED || (state == ProcessState.RUNNING && errorneous)) {
            return "label-warning";
        }

        if (errorneous) {
            return "label-danger";
        } else {
            return "label-success";
        }
    }

    public List<Tuple<String, String>> getCounterList() {
        return Tuple.fromMap(getCounters().data())
                    .stream()
                    .map(counter -> Tuple.create(NLS.getIfExists(counter.getFirst(), null).orElse(counter.getFirst()),
                                                 counter.getSecond()))
                    .collect(Collectors.toList());
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIcon() {
        if (Strings.isEmpty(icon)) {
            if (getState() == ProcessState.STANDBY) {
                return "fa-retweet";
            } else {
                return "fa-cogs";
            }
        }

        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public StringMap getContext() {
        return context;
    }

    public NestedList<ProcessLink> getLinks() {
        return links;
    }

    public StringMap getCounters() {
        return counters;
    }

    public LocalDateTime getStarted() {
        return started;
    }

    public void setStarted(LocalDateTime started) {
        this.started = started;
    }

    public LocalDateTime getCompleted() {
        return completed;
    }

    public void setCompleted(LocalDateTime completed) {
        this.completed = completed;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public String getStateMessage() {
        return stateMessage;
    }

    public void setStateMessage(String stateMessage) {
        this.stateMessage = stateMessage;
    }

    public boolean isErrorneous() {
        return errorneous;
    }

    public void setErrorneous(boolean errorneous) {
        this.errorneous = errorneous;
    }

    public LocalDateTime getCanceled() {
        return canceled;
    }

    public void setCanceled(LocalDateTime canceled) {
        this.canceled = canceled;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public NestedList<ProcessOutput> getOutputs() {
        return outputs;
    }

    public NestedList<ProcessFile> getFiles() {
        return files;
    }
}
