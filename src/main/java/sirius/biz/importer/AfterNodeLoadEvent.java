/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.biz.scripting.ScriptableEvent;
import sirius.kernel.xml.StructuredNode;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Triggered once a node has been loaded from an XML file for import, so the contents of the node could be manipulated.
 */
public class AfterNodeLoadEvent extends ScriptableEvent {
    private final StructuredNode structuredNode;
    private final ImporterContext importerContext;

    /**
     * Creates a new event for the given node and importer context.
     *
     * @param structuredNode  the node loaded from the input file
     * @param importerContext the import context which can be used to access other handlers / the importer itself
     **/
    public AfterNodeLoadEvent(StructuredNode structuredNode, ImporterContext importerContext) {
        this.structuredNode = structuredNode;
        this.importerContext = importerContext;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public ImporterContext getImporterContext() {
        return importerContext;
    }

    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public StructuredNode getStructuredNode() {
        return structuredNode;
    }
}
