/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.jdbc;

import sirius.biz.jobs.params.CodeListParameter;

public class SQLCodeListParameter extends CodeListParameter<Long, SQLCodeList> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    public SQLCodeListParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    public SQLCodeListParameter(String name) {
        super(name);
    }

    /**
     * Returns the type of entities represented by this parameter.
     *
     * @return the type of entities represented by this
     */
    @Override
    protected Class<SQLCodeList> getType() {
        return SQLCodeList.class;
    }
}
