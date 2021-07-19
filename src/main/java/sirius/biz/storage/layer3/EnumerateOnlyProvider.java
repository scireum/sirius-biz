/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.ValueHolder;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Represents a child provider which only enumerates its children.
 * <p>
 * Resolving is done by searching for the appropriate file / directory in the list  of children.
 */
public class EnumerateOnlyProvider implements ChildProvider {

    private final BiConsumer<VirtualFile, FileSearch> listProvider;

    /**
     * Creates a new provider which delegates the enumeration call to the given provider.
     *
     * @param listProvider the actual provider which enumerates all children by populating the given {@link FileSearch}
     */
    public EnumerateOnlyProvider(BiConsumer<VirtualFile, FileSearch> listProvider) {
        this.listProvider = listProvider;
    }

    @Override
   @Nullable
    public VirtualFile findChild(VirtualFile parent, String name) {
        ValueHolder<VirtualFile> result = new ValueHolder<>(null);
        enumerate(parent, FileSearch.iterateInto(file -> {
            if (Strings.areEqual(file.name(), name)) {
                result.set(file);
                return false;
            } else {
                return true;
            }
        }));

        return result.get();
    }

    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        listProvider.accept(parent, search);
    }
}
