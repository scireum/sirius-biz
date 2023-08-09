/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web.autoloading;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;

@Register
public class AutoLoadController extends BizController {

    @Routed("/auto-load-controller/:1")
    public void route(WebContext ctx, String id) {
        AutoLoadEntity entity = find(AutoLoadEntity.class, id);

        boolean a = prepareSave(ctx).saveEntity(entity);

        ctx.respondWith().json().beginResult().property("id", entity.getId()).endResult();
    }
}
