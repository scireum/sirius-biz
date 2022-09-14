/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
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
 * Permits to select a file and/or directory in the {@link VirtualFileSystem}.
 */
public class FileParameter extends ParameterBuilder<VirtualFile, FileParameter> {

    @Part
    private static VirtualFileSystem vfs;

    private String basePath;
    private List<String> acceptedExtensions;
    private boolean allowFiles = true;
    private boolean allowDirectories = false;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public FileParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Allow only files.
     * <p>
     * This is the default behavior, but you are encouraged to specify it anyway, as it enhances readability.
     *
     * @return the parameter itself for fluent method calls
     */
    public FileParameter filesOnly() {
        this.allowFiles = true;
        this.allowDirectories = false;
        return self();
    }

    /**
     * Allow only directories.
     *
     * @return the parameter itself for fluent method calls
     */
    public FileParameter directoriesOnly() {
        this.allowFiles = false;
        this.allowDirectories = true;
        return self();
    }

    /**
     * Allow both files and directories.
     *
     * @return the parameter itself for fluent method calls
     */
    public FileParameter filesAndDirectories() {
        this.allowFiles = true;
        this.allowDirectories = true;
        return self();
    }

    public boolean isFilesOnly() {
        return allowFiles && !allowDirectories;
    }

    /**
     * Whether files are allowed as parameter.
     *
     * @return true if files are allowed
     */
    public boolean allowsFiles() {
        return allowFiles;
    }

    /**
     * Whether directories are allowed as parameter.
     *
     * @return true if directories are allowed
     */
    public boolean allowsDirectories() {
        return allowDirectories;
    }

    /**
     * Permits to specify a base path to use when selecting a file.
     *
     * @param basePath the default value (or base path) to use
     * @return the parameter itself for fluent method calls
     */
    public FileParameter withBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    /**
     * Specifies a file list of accepted file extensions.
     *
     * @param extensions the list of accepted file extensions
     * @return the parameter itself for fluent method calls
     */
    public FileParameter withAcceptedExtensions(String... extensions) {
        this.acceptedExtensions = Arrays.asList(extensions);
        return this;
    }

    /**
     * Specifies a file list of accepted file extensions.
     *
     * @param extensions the list of accepted file extensions
     * @return the parameter itself for fluent method calls
     */
    public FileParameter withAcceptedExtensionsList(@Nonnull List<String> extensions) {
        this.acceptedExtensions = new ArrayList<>(extensions);
        return this;
    }

    @Override
    protected String getTemplateName() {
        return "/templates/biz/jobs/params/fileordirectoryfield.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return null;
        }

        Optional<VirtualFile> virtualFile = resolveFromString(input);
        if (virtualFile.isEmpty()) {
            String nlsKey = "FileParameter.invalidFileOrDirectory";
            if (!allowFiles) {
                nlsKey = "FileParameter.invalidDirectory";
            } else if (!allowDirectories) {
                nlsKey = "FileParameter.invalidFile";
            }
            throw new IllegalArgumentException(NLS.fmtr(nlsKey).set("path", input.asString()).format());
        }
        verifyExtensions(virtualFile.get());

        return virtualFile.get().path();
    }

    @Override
    protected Optional<VirtualFile> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }

        return Optional.ofNullable(vfs.resolve(input.asString()))
                       .filter(file -> allowDirectories && allowFiles && file.parent().exists() || file.exists())
                       .filter(file -> allowDirectories || file.isFile())
                       .filter(file -> allowFiles || file.isDirectory());
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

    /**
     * Returns the value to be used by the field in the template.
     *
     * @param context the current parameter set
     * @return the string value to use in the field
     */
    public String getFieldValue(Map<String, String> context) {
        return get(context).map(VirtualFile::path).orElse("");
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return super.computeValueUpdate(parameterContext).map(NLS::toUserString);
    }

    public String getBasePath() {
        return basePath;
    }
}
