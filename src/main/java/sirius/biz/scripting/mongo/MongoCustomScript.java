/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting.mongo;

import sirius.biz.importer.AutoImport;
import sirius.biz.mongo.PrefixSearchContent;
import sirius.biz.tenants.mongo.MongoTenantAware;
import sirius.biz.web.Autoloaded;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Unique;
import sirius.db.mongo.Mango;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

/**
 * Stores a custom scripting profile for a tenant.
 */
@Index(name = "lookup", columns = {"tenant", "code"}, columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
public class MongoCustomScript extends MongoTenantAware {

    /**
     * Contains the code or name of the script.
     */
    public static final Mapping CODE = Mapping.named("code");
    @Unique(within = "tenant")
    @PrefixSearchContent
    @Autoloaded
    @AutoImport
    private String code;

    /**
     * Contains the actual scripting code.
     */
    public static final Mapping SCRIPT = Mapping.named("script");
    @NullAllowed
    @Autoloaded
    @AutoImport
    private String script;

    @Override
    public String toString() {
        if (Strings.isFilled(code)) {
            return code;
        } else {
            return NLS.get("MongoCustomScript.unnamedScript");
        }
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
