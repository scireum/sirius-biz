/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.analytics.events.Event;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Records the successful/failed conversion of a {@link BlobVariant} by a {@link Converter}.
 */
public class ConversionEvent extends Event {

    /**
     * Contains the database ID (if available) of the file used as a source for the conversion.
     */
    public static final Mapping SOURCE_ID = Mapping.named("sourceId");
    @NullAllowed
    private String sourceId;

    /**
     * Contains the file name of the file used as a source for the conversion.
     */
    public static final Mapping SOURCE_FILE_NAME = Mapping.named("sourceFileName");
    private String sourceFileName;

    /**
     * Contains the file type / extension of the file used as a source for the conversion.
     */
    public static final Mapping SOURCE_TYPE = Mapping.named("sourceType");
    private String sourceType;

    /**
     * Contains the database ID (if available) of the file used as the target or destination for the conversion.
     */
    public static final Mapping TARGET_ID = Mapping.named("targetId");
    @NullAllowed
    private String targetId;

    /**
     * Contains the name of the type / variant being created during conversion.
     */
    public static final Mapping TARGET_TYPE = Mapping.named("targetType");
    private String targetType;

    /**
     * Contains the key of the {@link Converter} running the conversion.
     */
    public static final Mapping CONVERTER = Mapping.named("converter");
    @NullAllowed
    private String converter;

    /**
     * Contains the name of the actual tool / application used for the conversion.
     */
    public static final Mapping TOOL_NAME = Mapping.named("toolName");
    @NullAllowed
    private String toolName;

    /**
     * Contains whether or not the conversion resulted in the desired output file.
     */
    public static final Mapping SUCCESSFUL = Mapping.named("successful");
    private boolean successful;

    /**
     * Contains an optional descriptive reason when the conversion failed.
     */
    public static final Mapping FAIL_REASON = Mapping.named("failReason");
    @NullAllowed
    private String failReason;

    /**
     * Contains how long the conversion took in milliseconds.
     */
    public static final Mapping DURATION = Mapping.named("duration");
    private long duration;

    /**
     * Contains the ID of the tenant owning the source and target files being converted.
     */
    public static final Mapping TENANT = Mapping.named("tenant");
    @NullAllowed
    private String tenant;

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getConverter() {
        return converter;
    }

    public void setConverter(String converter) {
        this.converter = converter;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
