/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.kernel.async.TaskContext;
import sirius.kernel.di.std.Register;
import sirius.kernel.tokenizer.Position;
import sirius.pasta.noodle.Environment;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.ir.Node;
import sirius.pasta.noodle.macros.BasicMacro;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides a shortcut to invoke {@link TaskContext#log(String, Object...)}.
 */
@Register
@NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
public class LogMacro extends BasicMacro {
    @Override
    protected Class<?> getType() {
        return void.class;
    }

    @Override
    protected void verifyArguments(CompilationContext compilationContext, Position pos, List<Class<?>> args) {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("At least one argument is expected.");
        }
    }

    @Override
    public Object invoke(Environment environment, Object[] args) {
        if (args.length == 1) {
            TaskContext.get().log(String.valueOf(args[0]));
        } else {
            Object[] params = new Object[args.length - 1];
            System.arraycopy(args, 1, params, 0, args.length - 1);
            TaskContext.get().log(String.valueOf(args[0]), params);
        }

        return null;
    }

    @Override
    public boolean isConstant(CompilationContext context, List<Node> args) {
        return false;
    }

    @Override
    public String getDescription() {
        return "Invokes TaskContext.log(). This can either take a single argument as message or a pattern and formatting arguments.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "log";
    }
}
