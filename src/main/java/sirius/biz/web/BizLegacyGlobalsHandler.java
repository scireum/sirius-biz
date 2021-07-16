/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.di.std.Register;
import sirius.pasta.noodle.compiler.LegacyGlobalsHandler;

/**
 * Replaces magic constants which were proviously automatically available in Tagliatelle.
 */
@Register
public class BizLegacyGlobalsHandler extends LegacyGlobalsHandler {

    @Override
    protected String determineReplacement(String name) {
        return switch (name) {
            case "codeLists" -> "part(CodeLists.class)";
            case "jobsService" -> "part(Jobs.class)";
            case "tenantsHelper" -> "part(Tenants.class)";
            case "isenguard" -> "part(Isenguard.class)";
            case "appBaseUrl" -> "BizController.getBaseUrl()";
            default -> null;
        };
    }
}
