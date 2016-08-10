/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.codelists.CodeLists;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.templates.rythm.RythmExtension;

import java.util.function.Consumer;

/**
 * Makes central frameworks available in rythm without any import or reference.
 */
@Register
public class BizRythmExtension implements RythmExtension {

    @Part
    private CodeLists codeLists;

    @Override
    public void collectExtensionNames(Consumer<Tuple<String, Class<?>>> names) {
        names.accept(Tuple.create("codeLists", CodeLists.class));
    }

    @Override
    public void collectExtensionValues(Consumer<Tuple<String, Object>> values) {
        values.accept(Tuple.create("codeLists", codeLists));
    }
}
