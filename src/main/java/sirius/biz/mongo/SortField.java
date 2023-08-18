/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import sirius.biz.protocol.NoJournal;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.StringCleanup;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Provides a container composite to be used in MongoDB entities which can be used for sorting.
 * <p>
 * As MongoDB doesn't sort values properly (as we use it without collations), we use a normalized sort field here.
 * This field is populated with all values which are marked with {@link SortValue}.
 */
public class SortField extends Composite {

    /**
     * Contains the effective field to sort by.
     */
    public static final Mapping SORT_FIELD = Mapping.named("sortField");
    @SuppressWarnings("java:S1700")
    @Explain("We actually do want the same name here.")
    @NoJournal
    @NullAllowed
    private String sortField;

    @Transient
    private final MongoEntity owner;

    /**
     * Creates a sort field for the given entity.
     *
     * @param owner the owning entity which contains the sort field
     */
    public SortField(MongoEntity owner) {
        this.owner = owner;
    }

    /**
     * Normalizes the given text using {@link StringCleanup#reduceCharacters(String)}, lower-casing it after.
     *
     * @param text the text to normalize
     * @return the normalized text in lower-case
     */
    public static String normalizeText(String text) {
        if (Strings.isEmpty(text)) {
            return text;
        }

        return Strings.cleanup(text, StringCleanup::reduceCharacters, StringCleanup::lowercase);
    }

    @BeforeSave
    protected void fillSortField() {
        if (owner.is(CustomSortValues.class)) {
            fillUsingCustomSortValues();
        } else {
            fillUsingAnnotations();
        }
    }

    private void fillUsingAnnotations() {
        String sortFieldContents = owner.getDescriptor()
                                        .getProperties()
                                        .stream()
                                        .filter(property -> property.isAnnotationPresent(SortValue.class))
                                        .sorted(Comparator.comparing(property -> property.getAnnotation(SortValue.class)
                                                                                         .map(SortValue::order)
                                                                                         .orElse(99)))
                                        .map(property -> property.getValue(owner))
                                        .map(NLS::toUserString)
                                        .collect(Collectors.joining(" "));

        this.sortField = Strings.cleanup(sortFieldContents, StringCleanup::reduceCharacters, StringCleanup::lowercase);
    }

    private void fillUsingCustomSortValues() {
        StringBuilder sortFieldContents = new StringBuilder();
        Monoflop monoflop = Monoflop.create();
        owner.as(CustomSortValues.class).emitSortValues(value -> {
            if (monoflop.successiveCall()) {
                sortFieldContents.append(" ");
            }

            if (value != null) {
                sortFieldContents.append(NLS.toUserString(value));
            }
        });

        this.sortField = normalizeText(sortFieldContents.toString());
    }

    public String getSortField() {
        return sortField;
    }
}
