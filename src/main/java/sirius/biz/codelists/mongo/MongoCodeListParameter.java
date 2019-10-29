/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.codelists.mongo;

import sirius.biz.jobs.params.CodeListParameter;

/**
 * Implements the mongo specific {@link CodeListParameter}
 */
public class MongoCodeListParameter extends CodeListParameter<String, MongoCodeList> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    public MongoCodeListParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Creates a new parameter with the given name.
     *
     * @param name the name of the parameter
     */
    public MongoCodeListParameter(String name) {
        super(name);
    }

    /**
     * Returns the type of entities represented by this parameter.
     *
     * @return the type of entities represented by this
     */
    @Override
    protected Class<MongoCodeList> getType() {
        return MongoCodeList.class;
    }
}
