/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.params.ParameterBuilder;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Permits to select a directory in the {@link VirtualFileSystem}.
 *
 * @param <P> the effective parameter type for fluent method calls
 */
public abstract class BaseFileParameter<P extends ParameterBuilder<VirtualFile, P>> extends
                                                                                    ParameterBuilder<VirtualFile, P> {

    @Part
    protected static VirtualFileSystem vfs;

    protected String basePath;
    protected List<String> acceptedExtensions;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    protected BaseFileParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Permits to specify a base path to use when selecting a file.
     *
     * @param basePath the default value (or base path) to use
     * @return the parameter itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public P withBasePath(String basePath) {
        this.basePath = basePath;
        return (P) this;
    }

    /**
     * Specifies a file list of accepted file extensions.
     *
     * @param extensions the list of accepted file extensions
     * @return the parameter itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public P withAcceptedExtensions(String... extensions) {
        this.acceptedExtensions = Arrays.asList(extensions);
        return (P) this;
    }

    /**
     * Specifies a file list of accepted file extensions.
     *
     * @param extensions the list of accepted file extensions
     * @return the parameter itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public P withAcceptedExtensionsList(@Nonnull List<String> extensions) {
        this.acceptedExtensions = new ArrayList<>(extensions);
        return (P) this;
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return null;
        }

        Optional<VirtualFile> virtualFile = resolveFromString(input);
        if (virtualFile.isEmpty()) {
            throw new IllegalArgumentException(NLS.fmtr(getErrorMessageKey()).set("path", input.asString()).format());
        }
        verifyExtensions(virtualFile.get());

        return virtualFile.get().path();
    }

    private void verifyExtensions(VirtualFile selectedFile) {
        if (acceptedExtensions == null) {
            return;
        }

        if (selectedFile.isDirectory()) {
            return;
        }

        String fileExtension = selectedFile.fileExtension();
        for (String extension : acceptedExtensions) {
            if (Strings.equalIgnoreCase(extension, fileExtension)) {
                return;
            }
        }

        throw new IllegalArgumentException(NLS.fmtr("BaseFileParameter.invalidFileExtension")
                                              .set("name", selectedFile.name())
                                              .set("extensions", Strings.join(acceptedExtensions, ", "))
                                              .format());
    }

    protected abstract String getErrorMessageKey();

    /**
     * Returns the value to be used by the field in the template.
     *
     * @param context the current parameter set
     * @return the string value to use in the field
     */
    public String getFieldValue(Map<String, String> context) {
        return get(context).map(VirtualFile::path).orElse("");
    }

    public String getBasePath() {
        return basePath;
    }
}
