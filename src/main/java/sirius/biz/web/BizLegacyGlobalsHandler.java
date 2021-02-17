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
        switch (name) {
            case "codeLists":
                return "part(CodeLists.class)";
            case "jobsService":
                return "part(Jobs.class)";
            case "tenantsHelper":
                return "part(Tenants.class)";
            case "isenguard":
                return "part(Isenguard.class)";
            case "appBaseUrl":
                return "BizController.getBaseUrl()";
            default:
                return null;
        }
    }
}
