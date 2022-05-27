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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
    private Set<String> fileExtensionFilters;
    private Limit limit = Limit.UNLIMITED;
    private final Predicate<VirtualFile> resultProcessor;

    protected FileSearch(Predicate<VirtualFile> resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    /**
     * Creates a search which emits all results into the given processor as long as it returns <tt>true</tt>.
     *
     * @param resultProcessor the processor used to collect all results
     * @return the new search which can be passed in {@link ChildProvider#enumerate(VirtualFile, FileSearch)} or
     * {@link VirtualFile#children(FileSearch)}
     */
    public static FileSearch iterateInto(Predicate<VirtualFile> resultProcessor) {
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
     * Also, if no file extension is given, the extension is also compared to the prefix to increase search hits.
     * </p>
     *
     * @param prefixFilter the prefix to filter by
     * @return the search itself for fluent method calls
     */
    public FileSearch withPrefixFilter(String prefixFilter) {
        if (Strings.isEmpty(prefixFilter) || Strings.isEmpty(prefixFilter.trim())) {
            this.prefixFilter = null;
        } else {
            this.prefixFilter = prefixFilter.trim().toLowerCase();
        }
        return this;
    }

    /**
     * Adds a file extension to filter on.
     * <p>
     * Once added, only files with the given extension will be accepted. Note that this method can be invoked multiple
     * time to accept several file extensions.
     * <p>
     * Note that this filter doesn't apply to directories.
     *
     * @param fileExtension the file extension to accept.
     * @return the search itself for fluent method calls
     */
    public FileSearch withFileExtension(String fileExtension) {
        if (Strings.isEmpty(fileExtension) || Strings.isEmpty(fileExtension.trim())) {
            return this;
        }
        if (fileExtensionFilters == null) {
            fileExtensionFilters = new HashSet<>();
        }

        this.fileExtensionFilters.add(fileExtension.trim().toLowerCase());

        return this;
    }

    /**
     * Returns the prefix filter.
     * <p>
     * The prefix filter is guaranteed to be in lowercase.
     *
     * @return the prefix filter which has been set
     */
    public Optional<String> getPrefixFilter() {
        return Optional.ofNullable(prefixFilter);
    }

    /**
     * Provides the set of accepted file extensions for files.
     * <p>
     * If the set isn't empty, only files with one of the given file extensions will be accepted. Note that
     * the file extensions are guaranteed to be lowercase and will <b>not</b> start with a ".".
     * <p>
     * Note that directories will always be accepted independently of this set.
     *
     * @return the set of accepted file extensions.
     */
    public Set<String> getFileExtensionFilters() {
        return fileExtensionFilters == null ?
               Collections.emptySet() :
               Collections.unmodifiableSet(fileExtensionFilters);
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
     * Applies a limit which is used once all other filters have been fulfilled.
     *
     * @param limit the limit to apply
     * @return the search itself for fluent method calls
     */
    public FileSearch withLimit(Limit limit) {
        this.limit = Objects.requireNonNullElse(limit, Limit.UNLIMITED);

        return this;
    }

    /**
     * Determines if there is an upper limit of the maximal number of remaining items.
     * <p>
     * This can be used to optimize queries performed by the <tt>Layer 2</tt>.
     *
     * @return the maximal numbers of items to supply into {@link #processResult(VirtualFile)}
     * (assuming that the filters will match). Returns an empty optional if there is no upper limit.
     */
    public Optional<Integer> getMaxRemainingItems() {
        return Optional.ofNullable(limit.getRemainingItems());
    }

    /**
     * Processes a result and applies the given filters and limit.
     *
     * @param file the file to process
     * @return <tt>true</tt> if more results can be processed <tt>false</tt> if the enumeration should be aborted
     */
    public boolean processResult(VirtualFile file) {
        if (matchesFiltering(file) && limit.nextRow()) {
            return resultProcessor.test(file);
        }

        return limit.shouldContinue();
    }

    private boolean matchesFiltering(VirtualFile file) {
        if (onlyDirectories && !file.isDirectory()) {
            return false;
        }

        if (Strings.isFilled(prefixFilter) && !file.name().toLowerCase().startsWith(prefixFilter) && (Strings.isEmpty(
                file.fileExtension()) || !file.fileExtension().toLowerCase().startsWith(prefixFilter))) {
            return false;
        }

        // We only apply the extension filter on files not on directory so that one can still browse
        // through a directory structure when looking for a file of a specific type.
        if (fileExtensionFilters != null && !file.isDirectory()) {
            String fileExtension = file.fileExtension();
            if (Strings.isEmpty(fileExtension)) {
                return false;
            }

            return fileExtensionFilters.contains(fileExtension.toLowerCase());
        }

        return true;
    }
}
