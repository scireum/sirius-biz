/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import java.util.Optional;

/**
 * Contains the possible page sizes.
 */
public enum PageSize {

    S(25, 24), M(50, 48), L(100, 96);

    private final int sizeTable;
    private final int sizeCard;

    /**
     * Creates a new page size option.
     *
     * @param sizeTable the size for table display mode
     * @param sizeCard  the size for card display mode
     */
    PageSize(int sizeTable, int sizeCard) {
        this.sizeTable = sizeTable;
        this.sizeCard = sizeCard;
    }

    /**
     * Determines the {@link PageSize} for the given optional size.
     *
     * @param size the optional size
     * @return the corresponding page size or the default one
     */
    public static PageSize getPageSizeFor(Optional<Integer> size) {
        if (size.isPresent()) {
            for (PageSize option : values()) {
                if (option.getSizeTable() == size.get()) {
                    return option;
                }
            }
        }
        return ContentPageSize.DEFAULT_PAGE_SIZE;
    }

    /**
     * Returns the size for the given display mode.
     *
     * @param displayMode the display mode
     * @return the size for the given display mode
     */
    public int getSize(ContentListLayout.DisplayMode displayMode) {
        if (ContentListLayout.DisplayMode.CARDS == displayMode) {
            return sizeCard;
        }
        return sizeTable;
    }

    public int getSizeTable() {
        return sizeTable;
    }

    public int getSizeCard() {
        return sizeCard;
    }
}
