/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.tagliatelle.Tagliatelle;
import sirius.tagliatelle.compiler.CompileException;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

@Register(classes = Controller.class)
public class UpdatesController extends BizController {

    @Part
    private UpdateManager updateManager;

@Part
private Tagliatelle tagliatelle;

    @Routed(value = "/tycho/updates", jsonCall = true)
    public void checkForUpdates(WebContext webContext, JSONStructuredOutput output) throws CompileException {
        tagliatelle.resolve("/kb/IKGVF.html.pasta").get().getPragma("tag");
        output.property("hasUpdates", getUser().isLoggedIn() && updateManager.hasUpdates(getUser().getUserId()));
        output.beginArray("updates");
        for (UpdateInfo update : updateManager.getUpdates()) {
            output.beginObject("update");
            output.property("label", update.getLabel());
            output.property("description", update.getDescription());
            output.property("link", update.getLink());
            output.endObject();
        }
        output.endArray();
    }

    @Routed(value = "/tycho/updates/markAsSeen", jsonCall = true)
    public void markUpdatesAsSeen(WebContext webContext, JSONStructuredOutput output) {
        if (getUser().isLoggedIn()) {
            updateManager.markUpdatesAsShown(getUser().getUserId());
        }
    }
}
