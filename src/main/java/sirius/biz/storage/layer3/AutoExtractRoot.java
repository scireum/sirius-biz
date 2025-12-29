/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.JobStartingRoot;
import sirius.biz.jobs.presets.JobPreset;
import sirius.biz.jobs.presets.JobPresets;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides a base class to create a {@link VirtualFileSystem VFS} root which automatically extracts uploaded archives
 * like ZIP files.
 * <p>
 * This is done by automatically starting the {@link ExtractArchiveJob}.
 */
public abstract class AutoExtractRoot extends JobStartingRoot {

    @Part
    protected ExtractArchiveJob extractArchiveJob;

    @Part
    @Nullable
    protected JobPresets presets;

    @Override
    protected void populateRoot(MutableVirtualFile rootDirectory) {
        rootDirectory.withChildren(new FindOnlyProvider(this::createExtractJob));
    }

    protected VirtualFile createExtractJob(VirtualFile parent, String name) {
        MutableVirtualFile result = MutableVirtualFile.checkedCreate(parent, name);

        Optional<? extends JobPreset> preset = Optional.ofNullable(presets)
                                                       .flatMap(jobPresets -> jobPresets.fetchPresets(extractArchiveJob)
                                                                                        .stream()
                                                                                        .filter(p -> Strings.areEqual(p.getJobConfigData()
                                                                                                                       .getLabel(),
                                                                                                                      getExpectedPresetName()))
                                                                                        .findFirst());

        result.withOutputStreamSupplier(uploadFile -> uploadAndTrigger(extractArchiveJob,
                                                                       asParameterProvider(preset),
                                                                       uploadFile));
        return result;
    }

    /**
     * Determines the name of the {@link JobPreset} used to fetch a customer specific config.
     *
     * @return the name of the job preset to look for. If a preset with the name is found, it will be used, otherwise
     * all standard parameters will be applied.
     */
    protected abstract String getExpectedPresetName();

    @SuppressWarnings("OptionalIsPresent")
    @Explain("This is way more readable this way.")
    private Function<String, Value> asParameterProvider(Optional<? extends JobPreset> preset) {
        return param -> {
            Optional<Object> hardcodedParameter = fetchParameterValue(param);
            if (hardcodedParameter.isPresent()) {
                return Value.of(hardcodedParameter.get());
            } else if (preset.isPresent()) {
                return preset.get().getJobConfigData().fetchParameter(param);
            } else {
                return Value.EMPTY;
            }
        };
    }

    protected Optional<Object> fetchParameterValue(String param) {
        if (Strings.areEqual(param, ExtractArchiveJob.DESTINATION_PARAMETER_NAME)) {
            return Optional.ofNullable(determineDestination());
        } else {
            return determineParameterValue(param);
        }
    }

    /**
     * Determines the destination directory to place the extracted files to.
     *
     * @return the destination to extract to
     */
    protected abstract VirtualFile determineDestination();

    /**
     * Permits to provide a hardcoded value for the given parameter.
     * <p>
     * This will have precedence over the preset which might be applied.
     *
     * @param param the parameter to fetch the value for
     * @return the value to be used for the parameter or an empty optional if the preset or no value should be used
     */
    protected abstract Optional<Object> determineParameterValue(String param);
}
