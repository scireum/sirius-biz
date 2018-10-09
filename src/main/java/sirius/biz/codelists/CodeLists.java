/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SQLEntity;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Provides a framework to map string values (<tt>codes</tt>) to values defined by the user.
 * <p>
 * A code list is referenced by its unique name (code) and can be used to map codes to user defined texts. Additionally
 * a second values (<tt>additionalValue</tt>) can be stored per code.
 * <p>
 * The required data is stored via {@link CodeList} and {@link CodeListEntry}. The {@link CodeListController} is used
 * to provide an administration GUI for the user.
 */
@Register(classes = CodeLists.class, framework = CodeLists.FRAMEWORK_CODE_LISTS)
public class CodeLists {

    /**
     * Names the framework which must be enabled to activate the code lists feature.
     */
    public static final String FRAMEWORK_CODE_LISTS = "biz.code-lists";

    @Part
    private OMA oma;

    private static final Log LOG = Log.get("codelists");

    /**
     * Returns the value from the given code list associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value associated with the code or the code itself if no value exists
     */
    @Nullable
    @SuppressWarnings("squid:S2637")
    @Explain("code cannot be null due to Strings.isEmpty")
    public String getValue(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return null;
        }

        return getValues(codeList, code).getFirst();
    }

    /**
     * Determines if the code list contains the given code.
     *
     * @param codeList the code list to search in
     * @param code     the code to check
     * @return <tt>true</tt> if the code exists, <tt>false</tt> otherwise
     */
    public boolean hasValue(@Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return false;
        }

        CodeList cl = findOrCreateCodelist(codeList);
        return oma.select(CodeListEntry.class).eq(CodeListEntry.CODE_LIST, cl).eq(CodeListEntry.CODE, code).exists();
    }

    /**
     * Returns the value from the given code list associated with the given code or throws an exception if no matching
     * entry exists.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value associated with the code
     * @throws sirius.kernel.health.HandledException if no entry exists for the given code
     */
    public String getRequiredValue(@Nonnull String codeList, @Nonnull String code) {
        CodeList cl = findOrCreateCodelist(codeList);
        CodeListEntry cle = oma.select(CodeListEntry.class)
                               .eq(CodeListEntry.CODE_LIST, cl)
                               .eq(CodeListEntry.CODE, code)
                               .queryFirst();

        if (cle == null) {
            throw Exceptions.handle()
                            .to(LOG)
                            .withSystemErrorMessage("Unable to find required code ('%s') in code list ('%s')",
                                                    code,
                                                    codeList)
                            .handle();
        }

        return cle.getValue();
    }

    /**
     * Returns the value and the additionalValue associated with the given code.
     * <p>
     * If no matching entry exists, the code itself will be returned as value and the additional value (the second of
     * the tuple) will be <tt>null</tt>.
     *
     * @param codeList the code list to search in
     * @param code     the code to lookup
     * @return the value and the additional value associated with the given code, wrapped as tuple
     */
    public Tuple<String, String> getValues(@Nonnull String codeList, @Nonnull String code) {
        CodeList cl = findOrCreateCodelist(codeList);
        CodeListEntry cle = oma.select(CodeListEntry.class)
                               .eq(CodeListEntry.CODE_LIST, cl)
                               .eq(CodeListEntry.CODE, code)
                               .queryFirst();
        if (cle == null) {
            if (!cl.isAutofill()) {
                return Tuple.create(code, null);
            }
            cle = new CodeListEntry();
            cle.getCodeList().setValue(cl);
            cle.setCode(code);
            cle.setValue(code);
            oma.update(cle);
        }

        return Tuple.create(cle.getValue(), cle.getAdditionalValue());
    }

    private CodeList findOrCreateCodelist(@Nonnull String codeList) {
        if (Strings.isEmpty(codeList)) {
            throw new IllegalArgumentException("codeList must not be empty");
        }
        CodeList cl = oma.select(CodeList.class).fields(SQLEntity.ID).eq(CodeList.CODE, codeList).queryFirst();
        if (cl == null) {
            Extension ext = Sirius.getSettings().getExtension("code-lists", codeList);
            cl = new CodeList();
            cl.setCode(codeList);
            cl.setName(codeList);
            if (ext != null && !ext.isDefault()) {
                cl.setName(ext.get("name").asString());
                cl.setDescription(ext.get("description").asString());
                cl.setAutofill(ext.get("autofill").asBoolean());
            }

            oma.update(cl);
        }

        return cl;
    }

    /**
     * Returns all entries of a code list.
     *
     * @param codeList the code list to fetch entries from
     * @return a list of all avilable entries in the given code list, sorted by priority
     */
    public List<CodeListEntry> getEntries(@Nonnull String codeList) {
        CodeList cl = findOrCreateCodelist(codeList);
        return oma.select(CodeListEntry.class)
                  .eq(CodeListEntry.CODE_LIST, cl)
                  .orderAsc(CodeListEntry.PRIORITY)
                  .orderAsc(CodeListEntry.CODE)
                  .queryList();
    }
}
