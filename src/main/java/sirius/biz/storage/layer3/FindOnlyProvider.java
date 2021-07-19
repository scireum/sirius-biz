/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Represents a {@link ChildProvider} which can only resolve files / direcoties but never enumerate them.
 * <p>
 * This is useful for virtual folders which accept (non-exisiting) child files, which are then processed elsewhere.
 */
public class FindOnlyProvider implements ChildProvider {

    private final BiFunction<VirtualFile, String, VirtualFile> resolver;

    /**
     * Creates a new provider which delegates the find call to the given resolver.
     *
     * @param resolver the actual resolver which tries to find the requested file. Note that the resolver may return
     *                 <tt>null</tt> to indicate that no matching file / directory exists.
     */
    public FindOnlyProvider(BiFunction<VirtualFile, String, VirtualFile> resolver) {
        this.resolver = resolver;
    }

    @Override
    @Nullable
    public VirtualFile findChild(VirtualFile parent, String name) {
        return resolver.apply(parent, name);
    }

    @Override
    public void enumerate(VirtualFile parent, FileSearch search) {
        // Intentionally left empty...
    }
}
