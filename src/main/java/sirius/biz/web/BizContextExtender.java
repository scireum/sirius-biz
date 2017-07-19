/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.codelists.CodeLists;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.tagliatelle.RenderContextExtender;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Makes central frameworks available in Tagliatelle without any import or reference.
 */
@Register
public class BizContextExtender implements RenderContextExtender {

    @Part
    private CodeLists codeLists;

    @Override
    public void collectParameterTypes(BiConsumer<String, Class<?>> parameterCollector) {
        parameterCollector.accept("codeLists", CodeLists.class);
    }

    @Override
    public void collectParameterValues(Consumer<Object> parameterCollector) {
        parameterCollector.accept(codeLists);
    }
}
