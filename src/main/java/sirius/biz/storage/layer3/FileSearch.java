/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Encapsulates a search for children within a {@link ChildProvider}.
 * <p>
 * This class accepts a set of filter criteria which are then applied on the results being passed in.
 * <p>
 * These criteria can also be queried and extracted to be applied in a more efficient manner (if possible). In this
 * case the filters should be disabled here.
 */
public class FileSearch {

    private boolean onlyDirectories;
    private String prefixFilter;
    private Limit limit = Limit.UNLIMITED;
    private Function<VirtualFile, Boolean> resultProcessor;

    protected FileSearch(Function<VirtualFile, Boolean> resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    /**
     * Creates a search which emits all results into the given processor as long as it returns <tt>true</tt>.
     *
     * @param resultProcessor the processor used to collect all results
     * @return the new search which can be passed in {@link ChildProvider#enumerate(VirtualFile, FileSearch)} or
     * {@link VirtualFile#children(FileSearch)}
     */
    public static FileSearch iterateInto(Function<VirtualFile, Boolean> resultProcessor) {
        return new FileSearch(resultProcessor);
    }

    /**
     * Creates a search which emits all results into the given processor .
     *
     * @param resultProcessor the processor used to collect all results
     * @return the new search which can be passed in {@link ChildProvider#enumerate(VirtualFile, FileSearch)} or
     * {@link VirtualFile#children(FileSearch)}
     */
    public static FileSearch iterateAll(Consumer<VirtualFile> resultProcessor) {
        return new FileSearch(file -> {
            resultProcessor.accept(file);
            return true;
        });
    }

    /**
     * Adds a prefix filter so that only files which name start with the given prefix are accepted.
     * <p>
     * Note that the prefix is compared in a case insensitive manner.
     * </p>
     *
     * @param prefixFilter the prefix to filter by
     * @return the search itself for fluent method calls
     */
    public FileSearch withPrefixFilter(String prefixFilter) {
        this.prefixFilter = Value.of(prefixFilter).toLowerCase();
        return this;
    }

    /**
     * Determines if a prefix filter is present.
     *
     * @return <tt>true</tt> if a filter is present, <tt>false</tt> otherwise
     */
    public boolean hasPrefixFiler() {
        return Strings.isFilled(prefixFilter);
    }

    /**
     * Returns the prefix filter.
     *
     * @return the prefix filter which has been set
     */
    @Nullable
    public String getPrefixFilter() {
        return prefixFilter;
    }

    /**
     * Disables the prefix filter.
     * <p>
     * This can be used if the filter has been extracted and externally applied to support a more efficient
     * implementation.
     *
     * @return the search itself for fluent method calls
     */
    public FileSearch disablePrefixFilter() {
        this.prefixFilter = null;
        return this;
    }

    /**
     * Adds a filter so that only directories are accepted.
     *
     * @return the search itself for fluent method calls
     */
    public FileSearch withOnlyDirectories() {
        this.onlyDirectories = true;
        return this;
    }

    /**
     * Determines if a directory only filter has been applied.
     *
     * @return <tt>true</tt> if this search only accepts directories, <tt>false</tt> otherwise
     */
    public boolean isOnlyDirectories() {
        return onlyDirectories;
    }

    /**
     * Disables the directory filter.
     * <p>
     * This can be used if the filter has been extracted and externally applied to support a more efficient
     * implementation.
     *
     * @return the search itself for fluent method calls
     */
    public FileSearch disableOnlyDirectories() {
        this.onlyDirectories = false;
        return this;
    }

    /**
     * Applies a limit which is used once all other filters have been fulfilled.
     *
     * @param limit the limit to apply
     * @return the search itself for fluent method calls
     */
    public FileSearch withLimit(Limit limit) {
        if (limit == null) {
            this.limit = Limit.UNLIMITED;
        } else {
            this.limit = limit;
        }

        return this;
    }

    /**
     * Determines if a limit is present.
     *
     * @return <tt>true</tt> if a limit is present, <tt>false</tt> otherwise
     */
    public boolean hasLimit() {
        return limit.getRemainingItems() != null;
    }

    /**
     * Obtains the limit which has been set.
     *
     * @return the limit or {@link Limit#UNLIMITED} if no limit has been specified
     */
    public Limit getLimit() {
        return limit;
    }

    /**
     * Disables the limit.
     * <p>
     * This can be used if the limit has been extracted and externally applied to support a more efficient
     * implementation.
     *
     * @return the search itself for fluent method calls
     */
    public FileSearch disableLimit() {
        this.limit = Limit.UNLIMITED;
        return this;
    }

    /**
     * Processes a result and applies the given filters and limit.
     *
     * @param file the file to process
     * @return <tt>true</tt> if more results can be processed <tt>false</tt> if the enumeration should be aborted
     */
    public boolean processResult(VirtualFile file) {
        if (!onlyDirectories || file.isDirectory()) {
            if (Strings.isEmpty(prefixFilter) || file.name().toLowerCase().startsWith(prefixFilter)) {
                if (limit.nextRow()) {
                    return resultProcessor.apply(file);
                }
            }
        }

        return limit.shouldContinue();
    }

    /**
     * Creates a sub-filter which can be used to pass this search into multiple <tt>ChildProviders</tt>.
     *
     * @return a sub search which has the same filters but only a partial part of the limit as this is enforced by
     * the central instance
     */
    public FileSearch forkChild() {
        FileSearch result = new FileSearch(this::processResult);
        result.onlyDirectories = onlyDirectories;
        result.prefixFilter = prefixFilter;
        if (limit.getRemainingItems() != null) {
            result.limit = new Limit(0, limit.getRemainingItems());
        }

        return result;
    }
}
