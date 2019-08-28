/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.biz.jobs.params.Parameter;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Permits to select a directory in the {@link VirtualFileSystem}.
 */
public class DirectoryParameter extends Parameter<VirtualFile, DirectoryParameter> {

    @Part
    private static VirtualFileSystem vfs;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public DirectoryParameter(String name, String label) {
        super(name, label);
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/storage/directoryfield.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return null;
        }

        Optional<VirtualFile> virtualFile = resolveFromString(input);
        if (!virtualFile.isPresent()) {
            throw new IllegalArgumentException(NLS.fmtr("DirectoryParameter.invalidPath")
                                                  .set("path", input.asString())
                                                  .format());
        }
        return virtualFile.get().path();
    }

    @Override
    protected Optional<VirtualFile> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }

        return vfs.tryResolve(input.asString()).filter(file -> file.exists() && file.isDirectory());
    }
}
