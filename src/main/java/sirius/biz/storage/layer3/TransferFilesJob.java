/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.FileParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.util.StorageUtils;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a generic job to copy or move {@link VirtualFile virtual files} in the background.
 */
@Register(framework = StorageUtils.FRAMEWORK_STORAGE)
public class TransferFilesJob extends SimpleBatchProcessJobFactory {

    /**
     * Contains the name of this job.
     */
    public static final String NAME = "transfer-files";

    /**
     * Contains the parameter name of the source file.
     */
    public static final String SOURCE_PARAMETER_NAME = "source";

    /**
     * Contains the parameter name of the destination file.
     */
    public static final String DESTINATION_PARAMETER_NAME = "destination";

    /**
     * Contains the parameter name of the mode to use.
     */
    public static final String MODE_PARAMETER_NAME = "mode";

    /**
     * Contains the parameter name which determines if "smart" copying should be used.
     */
    public static final String SMART_TRANSFER_PARAMETER_NAME = "smartTransfer";

    public static final String CONTINUE_ON_ERROR_PARAMETER_NAME = "continueOnError";

    /**
     * Determines the mode to use when transferring files.
     */
    public enum TransferMode {
        COPY, MOVE, COPY_CONTENTS, MOVE_CONTENTS;

        @Override
        public String toString() {
            return NLS.get(TransferMode.class.getSimpleName() + "." + name());
        }
    }

    private final Parameter<VirtualFile> sourceParameter =
            new FileParameter(SOURCE_PARAMETER_NAME, "$TransferFilesJob.source").filesAndDirectories()
                                                                                .markRequired()
                                                                                .build();
    private final Parameter<VirtualFile> destinationParameter =
            new FileParameter(DESTINATION_PARAMETER_NAME, "$TransferFilesJob.destination").filesAndDirectories()
                                                                                          .markRequired()
                                                                                          .build();
    private final Parameter<TransferMode> modeParameter =
            new EnumParameter<>(MODE_PARAMETER_NAME, "$TransferFilesJob.mode", TransferMode.class).withDefault(
                    TransferMode.COPY).markRequired().build();

    private final Parameter<Boolean> smartTransferParameter =
            new BooleanParameter(SMART_TRANSFER_PARAMETER_NAME, "$TransferFilesJob.smartTransfer").withDefaultTrue()
                                                                                                  .build();

    private final Parameter<Boolean> continueOnErrorParameter =
            new BooleanParameter(CONTINUE_ON_ERROR_PARAMETER_NAME, "$TransferFilesJob.continueOnError").withDescription(
                    "$TransferFilesJob.continueOnError.help").build();

    @Part
    private VirtualFileSystem virtualFileSystem;

    @Override
    public String getIcon() {
        return "fa-regular fa-copy";
    }

    @Override
    public int getPriority() {
        return 5100;
    }

    @Override
    protected void execute(ProcessContext process) throws Exception {
        VirtualFile source = process.require(sourceParameter);
        VirtualFile destination = process.require(destinationParameter);
        TransferMode mode = process.require(modeParameter);

        if (mode == TransferMode.COPY_CONTENTS || mode == TransferMode.MOVE_CONTENTS) {
            if (!source.isDirectory()) {
                process.log(ProcessLog.error()
                                      .withNLSKey("TransferFilesJob.illegalSource")
                                      .withContext("sourceFile", source));
                return;
            }
            if (!destination.isDirectory()) {
                process.log(ProcessLog.error()
                                      .withNLSKey("TransferFilesJob.illegalDestination")
                                      .withContext("destinationFile", destination));
                return;
            }
        }

        Transfer transfer = source.transferTo(destination).batch(process);

        if (Boolean.TRUE.equals(process.require(smartTransferParameter))) {
            transfer.smartTransfer();
        }

        if (Boolean.TRUE.equals(process.require(continueOnErrorParameter))) {
            transfer.continueOnError();
        }

        switch (mode) {
            case COPY -> transfer.copy();
            case MOVE -> transfer.move();
            case COPY_CONTENTS -> transfer.copyContents();
            case MOVE_CONTENTS -> transfer.moveContents();
        }
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        String source = sourceParameter.get(context).map(VirtualFile::name).orElse("?");
        String destination = destinationParameter.get(context).map(VirtualFile::name).orElse("?");
        String mode = modeParameter.get(context).orElse(TransferMode.COPY).toString();
        return Strings.apply("%s: %s -> %s", mode, source, destination);
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(sourceParameter);
        parameterCollector.accept(destinationParameter);
        parameterCollector.accept(modeParameter);
        parameterCollector.accept(smartTransferParameter);
        parameterCollector.accept(continueOnErrorParameter);
    }

    @Nonnull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return StandardCategories.MISC;
    }
}
