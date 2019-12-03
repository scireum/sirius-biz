/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Value;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Permits to select a directory in the {@link VirtualFileSystem}.
 */
public class DirectoryParameter extends BaseFileParameter<DirectoryParameter> {

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
    protected String getErrorMessageKey() {
        return "DirectoryParameter.invalidPath";
    }

    @Override
    protected Optional<VirtualFile> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }

        return Optional.ofNullable(vfs.resolve(input.asString())).filter(file -> file.exists() && file.isDirectory());
    }
}
