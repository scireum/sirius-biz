/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.commons.Strings;

import java.time.LocalDateTime;

public class AcademyVideoData extends Composite {

    public static final Mapping ACADEMY = Mapping.named("academy");
    @Length(150)
    private String academy;

    public static final Mapping VIDEO_ID = Mapping.named("videoId");
    @Length(50)
    private String videoId;

    public static final Mapping TRACK_ID = Mapping.named("trackId");
    @Length(50)
    private String trackId;

    public static final Mapping TRACK_NAME = Mapping.named("trackName");
    @Length(255)
    private String trackName;

    public static final Mapping TITLE = Mapping.named("title");
    @Length(255)
    private String title;

    public static final Mapping PREVIEW_URL = Mapping.named("previewUrl");
    @Length(512)
    private String previewUrl;

    public static final Mapping DESCRIPTION = Mapping.named("description");
    @Lob
    @NullAllowed
    private String description;

    public static final Mapping DURATION = Mapping.named("duration");
    private int duration;

    public static final Mapping PRIORITY = Mapping.named("priority");
    private int priority;

    public static final Mapping REQUIRED_FEATURE = Mapping.named("requiredFeature");
    @Length(255)
    @NullAllowed
    private String requiredFeature;

    public static final Mapping REQUIRED_PERMISSION = Mapping.named("requiredPermission");
    @Length(255)
    @NullAllowed
    private String requiredPermission;

    public static final Mapping MANDATORY = Mapping.named("mandatory");
    private boolean mandatory;

    public static final Mapping CREATED = Mapping.named("created");
    private LocalDateTime created;

    public static final Mapping LAST_UPDATED = Mapping.named("lastUpdated");
    private LocalDateTime lastUpdated;

    public static final Mapping DELETED = Mapping.named("deleted");
    private boolean deleted = false;

    public void loadFrom(AcademyVideoData other) {
        this.academy = other.academy;
        this.videoId = other.videoId;
        this.trackId = other.trackId;
        this.trackName = other.trackName;
        this.title = other.title;
        this.description = other.description;
        this.previewUrl = other.previewUrl;
        this.duration = other.duration;
        this.priority = other.priority;
        this.requiredFeature = other.requiredFeature;
        this.requiredPermission = other.requiredPermission;
        this.mandatory = other.mandatory;
        this.deleted = other.deleted;
    }

    public String getDurationAsString() {
        long minutes = duration / 60;
        long seconds = duration % 60;

        return Strings.apply("%02d:%02d", minutes, seconds);
    }

    public String getAcademy() {
        return academy;
    }

    public void setAcademy(String academy) {
        this.academy = academy;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getRequiredFeature() {
        return requiredFeature;
    }

    public void setRequiredFeature(String requiredFeature) {
        this.requiredFeature = requiredFeature;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }
}
