/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;

public interface CodeListEntry<I, L extends BaseEntity<I> & CodeList> {

    Mapping CODE_LIST = Mapping.named("codeList");

    BaseEntityRef<I, L> getCodeList();

    Mapping CODE_LIST_ENTRY_DATA = Mapping.named("codeListEntryData");

    CodeListEntryData getCodeListEntryData();
}
