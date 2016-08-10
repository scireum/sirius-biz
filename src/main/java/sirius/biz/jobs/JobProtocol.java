/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs;

import sirius.biz.model.BizEntity;
import sirius.db.mixing.Column;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Created by aha on 22.07.16.
 */
//TODO indices
public class JobProtocol extends BizEntity {

    public static final Column TENANT = Column.named("tenant");
    @Length(50)
    private String tenant;

    public static final Column USER = Column.named("user");
    @Length(50)
    private String user;

    public static final Column USER_NAME = Column.named("userName");
    @Length(255)
    private String userName;

    public static final Column FACTORY = Column.named("factory");
    @Length(100)
    private String factory;

    public static final Column JOB = Column.named("job");
    @Length(100)
    private String job;

    public static final Column JOB_TITLE = Column.named("jobTitle");
    @Length(255)
    private String jobTitle;

    public static final Column JOB_LOG = Column.named("jobLog");
    @NullAllowed
    @Lob
    private String jobLog;

    public static final Column SUCCESSFUL = Column.named("successful");
    private boolean successful;

    public static final Column DURATION_IN_SECONDS = Column.named("durationInSeconds");
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
