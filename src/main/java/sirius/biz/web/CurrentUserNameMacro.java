/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.kernel.di.std.Register;
import sirius.kernel.tokenizer.Position;
import sirius.pasta.noodle.Environment;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.compiler.ir.Node;
import sirius.pasta.noodle.macros.BasicMacro;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Returns the current user name.
 * <p>
 * Next to simply using {@code UserContext.getCurrent().getUserName()} we check if the user is a
 * {@link sirius.biz.tenants.UserAccount} and in this case use the {@link UserAccountData#getShortName()}.
 */
@Register
public class CurrentUserNameMacro extends BasicMacro {

    @Override
    public Class<?> getType() {
        return String.class;
    }

    @Override
    protected void verifyArguments(CompilationContext compilationContext, Position pos, List<Class<?>> args) {
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("No arguments expected.");
        }
    }

    @Override
    public Object invoke(Environment environment, Object[] args) {
        UserInfo user = UserContext.getCurrentUser();
        return user.tryAs(sirius.biz.tenants.UserAccount.class)
                   .map(UserAccount::getUserAccountData)
                   .map(UserAccountData::getShortName)
                   .orElseGet(user::getUserName);
    }

    @Override
    public String getDescription() {
        return "Outputs the current user name.";
    }

    @Nonnull
    @Override
    public String getName() {
        return "currentUserName";
    }

    @Override
    public boolean isConstant(CompilationContext context, List<Node> args) {
        return false;
    }
}
