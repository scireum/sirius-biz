/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.tokenizer.Position;
import sirius.pasta.noodle.Environment;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.ir.Node;
import sirius.pasta.noodle.macros.BasicMacro;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Signs a link which can be verified using BizController.verifySignedLink.
 * <p>
 * This is intended to be used in GET requests. For POST request see {@link sirius.biz.web.ComputeAuthSignatureMacro}.
 */
@Register
public class SignLinkMacro extends BasicMacro {

    @Override
    public Class<?> getType() {
        return String.class;
    }

    @Override
    protected void verifyArguments(CompilationContext compilationContext, Position pos, List<Class<?>> args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Expected a single argument.");
        }
    }

    @Override
    public Object invoke(Environment environment, Object[] args) {
        Object value = args[0];
        if (Strings.isEmpty(value)) {
            return null;
        }

        return BizController.signLink(value.toString());
    }

    @Override
    public String getDescription() {
        return "Signs a link which can be verified using BizController.verifySignedLink";
    }

    @Nonnull
    @Override
    public String getName() {
        return "signLink";
    }

    @Override
    public boolean isConstant(CompilationContext context, List<Node> args) {
        return false;
    }
}
