/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.scripting.ScriptableEvent;
import sirius.kernel.commons.Context;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Triggered once a line has been loaded from a line-based file and parsed by an {@link sirius.biz.importer.format.ImportDictionary},
 * providing the context read which could be manipulated by scripts before any other action is taken.
 */
public class AfterLineLoadEvent extends ScriptableEvent {

    private final Context context;
    private final ImporterContext importerContext;

    /**
     * Creates a new event for the given context and importer context.
     *
     * @param context         the context loaded from the input file
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public AfterLineLoadEvent(Context context, ImporterContext importerContext) {
        this.importerContext = importerContext;
        this.context = context;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public Context getContext() {
        return context;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public ImporterContext getImporterContext() {
        return importerContext;
    }
}
