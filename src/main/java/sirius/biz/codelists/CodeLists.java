/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists;

import sirius.biz.tenants.Tenant;
import sirius.biz.tenants.Tenants;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.extensions.Extension;
import sirius.kernel.extensions.Extensions;
import sirius.mixing.Entity;
import sirius.mixing.OMA;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Created by aha on 11.05.15.
 */
@Register(classes = CodeLists.class)
public class CodeLists {

    public String getValue(@Nonnull String codeList, @Nullable String code) {
        return getValue(tenants.getRequiredTenant(), codeList, code);
    }

    @Part
    private OMA oma;

    @Part
    private Tenants tenants;

    public String getValue(@Nonnull Tenant tenant, @Nonnull String codeList, @Nullable String code) {
        if (Strings.isEmpty(code)) {
            return null;
        }
        return getValues(tenant, codeList, code).getFirst();
    }

    public Tuple<String, String> getValues(@Nonnull Tenant tenant, @Nonnull String codeList, @Nonnull String code) {
        CodeList cl = findOrCreateCodelist(tenant, codeList);
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

    private CodeList findOrCreateCodelist(@Nonnull Tenant tenant, @Nonnull String codeList) {
        if (Strings.isEmpty(codeList)) {
            throw new IllegalArgumentException("codeList must not be empty");
        }
        CodeList cl = oma.select(CodeList.class).fields(Entity.ID).eq(CodeList.CODE, codeList).queryFirst();
        if (cl == null) {
            Extension ext = Extensions.getExtension("code-lists", codeList);
            cl = new CodeList();
            cl.setCode(codeList);
            cl.setName(codeList);
            if (ext != null && !ext.isDefault()) {
                cl.setName(ext.get("name").asString());
                cl.setDescription(ext.get("description").asString());
                cl.setAutofill(ext.get("autofill").asBoolean());
            }
            cl.getTenant().setValue(tenant);
            oma.update(cl);
        }

        return cl;
    }

    public List<CodeListEntry> getEntries(@Nonnull String codeList) {
        return getEntries(tenants.getRequiredTenant(), codeList);
    }

    public List<CodeListEntry> getEntries(@Nonnull Tenant tenant, @Nonnull String codeList) {
        CodeList cl = findOrCreateCodelist(tenant, codeList);
        return oma.select(CodeListEntry.class)
                  .eq(CodeListEntry.CODE_LIST, cl)
                  .orderAsc(CodeListEntry.PRIORITY)
                  .orderAsc(CodeListEntry.CODE)
                  .queryList();
    }

}
