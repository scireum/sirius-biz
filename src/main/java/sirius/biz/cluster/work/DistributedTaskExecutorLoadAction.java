/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work;

import sirius.kernel.di.ClassLoadAction;
import sirius.kernel.di.Injector;
import sirius.kernel.di.MutableGlobalContext;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

/**
 * Automatically registers all sublcasses of {@link DistributedTaskExecutor} with the class name as name.
 */
public class DistributedTaskExecutorLoadAction implements ClassLoadAction {

    @Nullable
    @Override
    public Class<? extends Annotation> getTrigger() {
        return null;
    }

    @Override
    public void handle(@Nonnull MutableGlobalContext mutableGlobalContext, @Nonnull Class<?> aClass) throws Exception {
        if (!DistributedTaskExecutor.class.isAssignableFrom(aClass) || Modifier.isAbstract(aClass.getModifiers())) {
            return;
        }

        if (aClass.isAnnotationPresent(Register.class)) {
            Injector.LOG.WARN(
                    "%s should not wear an @Registered, as subclasses of DistributedTaskExecutor are automatically loaded.",
                    aClass.getName());
        }

        try {
            Object anInstance = aClass.getDeclaredConstructor().newInstance();
            mutableGlobalContext.registerPart(anInstance.getClass().getName(),
                                              anInstance,
                                              DistributedTaskExecutor.class);
        } catch (Exception exception) {
            Injector.LOG.WARN("Cannot register %s as DistributedTaskExecutor: %s (%s)",
                              aClass.getName(),
                              exception.getMessage(),
                              exception.getClass().getName());
        }
    }
}
