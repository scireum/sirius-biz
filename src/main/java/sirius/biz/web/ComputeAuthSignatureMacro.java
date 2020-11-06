/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import parsii.tokenizer.Position;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.tagliatelle.compiler.CompilationContext;
import sirius.tagliatelle.expression.Expression;
import sirius.tagliatelle.macros.Macro;
import sirius.tagliatelle.rendering.LocalRenderContext;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Computes a signature which can be verified using BizController.verifySignedLink.
 *
 * This is intended to be used in POST requests. For GET request see {@link sirius.biz.web.SignLinkMacro}.
 */
@Register
public class ComputeAuthSignatureMacro implements Macro {

    @Override
    public Class<?> getType() {
        return String.class;
    }

    @Override
    public void verifyArguments(CompilationContext context, Position pos, List<Expression> args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Expected a single argument.");
        }
    }

    @Override
    public Object eval(LocalRenderContext ctx, Expression[] args) {
        Object value = args[0].eval(ctx);
        if (Strings.isEmpty(value)) {
            return null;
        }

        return BizController.computeURISignature(value.toString());
    }

    @Override
    public boolean isConstant(Expression[] expressions) {
        return true;
    }

    @Override
    public String getDescription() {
        return "Computes a signature which can be verified using BizController.verifySignedLink";
    }

    @Nonnull
    @Override
    public String getName() {
        return "computeAuthSignature";
    }
}
