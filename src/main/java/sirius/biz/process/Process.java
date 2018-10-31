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
import sirius.db.es.annotations.ESOption;
import sirius.db.es.annotations.IndexMode;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.types.StringMap;

import java.time.LocalDateTime;

public class Process extends SearchableEntity {

    public static final Mapping TITLE = Mapping.named("title");
    @SearchContent
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String title;

    public static final Mapping STATE_MESSAGE = Mapping.named("stateMessage");
    @SearchContent
    @NullAllowed
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String stateMessage;

    public static final Mapping PROCESS_TYPE = Mapping.named("processType");
    @NullAllowed
    private String processType;

    public static final Mapping USER_ID = Mapping.named("userId");
    private String userId;

    public static final Mapping USER_NAME = Mapping.named("userName");
    @SearchContent
    private String userName;

    public static final Mapping TENANT_ID = Mapping.named("tenantId");
    private String tenantId;

    public static final Mapping TENANT_NAME = Mapping.named("tenantName");
    @SearchContent
    private String tenantName;

    public static final Mapping CONTEXT = Mapping.named("context");
    @IndexMode(indexed = ESOption.FALSE, docValues = ESOption.FALSE)
    private String context;

    public static final Mapping LINKS = Mapping.named("links");
    private final StringMap links = new StringMap();

    public static final Mapping COUNTERS = Mapping.named("counters");
    private final StringMap counters = new StringMap();

    public static final Mapping SCHEDULED = Mapping.named("scheduled");
    private LocalDateTime scheduled;

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

    /**
     * Determines the bootstrap CSS class to be used for rendering the row of this process.
     */
    public String getRowClass() {
        //TODO wrooooong - fix logic
        if (completed == null && state == ProcessState.SCHEDULED) {
            return "default";
        }

        if (completed == null && state == ProcessState.RUNNING) {
            return "info";
        }

        if (completed != null && !errorneous) {
            return "success";
        }

        return "warning";
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public StringMap getLinks() {
        return links;
    }

    public StringMap getCounters() {
        return counters;
    }

    public LocalDateTime getScheduled() {
        return scheduled;
    }

    public void setScheduled(LocalDateTime scheduled) {
        this.scheduled = scheduled;
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
}
