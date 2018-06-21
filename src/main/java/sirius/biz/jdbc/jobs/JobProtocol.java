/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.jobs;

import sirius.biz.jdbc.model.BizEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Created by aha on 22.07.16.
 */
//TODO indices
public class JobProtocol extends BizEntity {

    public static final Mapping TENANT = Mapping.named("tenant");
    @Length(50)
    private String tenant;

    public static final Mapping USER = Mapping.named("user");
    @Length(50)
    private String user;

    public static final Mapping USER_NAME = Mapping.named("userName");
    @Length(255)
    private String userName;

    public static final Mapping FACTORY = Mapping.named("factory");
    @Length(100)
    private String factory;

    public static final Mapping JOB = Mapping.named("job");
    @Length(100)
    private String job;

    public static final Mapping JOB_TITLE = Mapping.named("jobTitle");
    @Length(255)
    private String jobTitle;

    public static final Mapping JOB_LOG = Mapping.named("jobLog");
    @NullAllowed
    @Lob
    private String jobLog;

    public static final Mapping SUCCESSFUL = Mapping.named("successful");
    private boolean successful;

    public static final Mapping DURATION_IN_SECONDS = Mapping.named("durationInSeconds");
    private long durationInSeconds;

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getFactory() {
        return factory;
    }

    public void setFactory(String factory) {
        this.factory = factory;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getJobLog() {
        return jobLog;
    }

    public void setJobLog(String jobLog) {
        this.jobLog = jobLog;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public long getDurationInSeconds() {
        return durationInSeconds;
    }

    public void setDurationInSeconds(long durationInSeconds) {
        this.durationInSeconds = durationInSeconds;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
