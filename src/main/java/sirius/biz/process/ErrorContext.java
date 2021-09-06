/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.SubContext;
import sirius.kernel.async.TaskContext;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.UnitOfWork;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Provides a local context to enhance error messages (mostly within {@link sirius.biz.jobs.batch.BatchJob jobs}).
 * <p>
 * When importing data (especially for complex files like XML), the error messages generated by <tt>sirius-db</tt>
 * are often not concise enough. Therefore, we populate this error context to keep track which entity was modified
 * and which actual action as been attempted.
 */
public class ErrorContext implements SubContext {

    private final Map<String, String> context = new LinkedHashMap<>();

    /**
     * Reveals the context for the current thread.
     *
     * @return the current error context
     */
    public static ErrorContext get() {
        return CallContext.getCurrent().get(ErrorContext.class);
    }

    /**
     * Adds a value to the error context.
     *
     * @param label the name of the value (this will be {@link NLS#smartGet(String) auto translated).
     * @param value the value to store
     * @return the extended context for fluent method calls
     */
    public ErrorContext withContext(String label, Object value) {
        if (Strings.isEmpty(label)) {
            return this;
        }
        if (Strings.isEmpty(value)) {
            this.context.remove(label);
        } else {
            this.context.put(label, NLS.toUserString(value));
        }

        return this;
    }

    /**
     * Removes the value with the given label from the context.
     *
     * @param label the label of the value to remove
     * @return the modified context for fluent method calls
     */
    public ErrorContext removeContext(String label) {
        return withContext(label, null);
    }

    /**
     * Adds the given value to the context, performs the given task and then removes the value again.
     *
     * @param label the name of the value (this will be {@link NLS#smartGet(String) auto translated}).
     * @param value the value to store
     * @param task  the task to perform while the value is set
     * @return the context for fluent method calls
     * @throws Exception any error as thrown by the given task. Note that the exceptions are not handled and also
     *                   not automatically {@link #enhanceMessage(String) enhanced}.
     */
    public ErrorContext inContext(String label, Object value, UnitOfWork task) throws Exception {
        withContext(label, value);
        try {
            task.execute();
        } finally {
            removeContext(label);
        }

        return this;
    }

    /**
     * Executes the given producer and if any exception happens, applies the given failure description and throws a handled exception.
     *
     * @param failureDescription annotates a given error message so that the user is notified what task actually went
     *                           wrong. This should be in "negative form" like "Cannot perform x because: message" as
     *                           it is only used for error reporting.
     * @param producer           the producer to execute
     * @return the object created by the given producer
     */
    public <T> T executeAndGet(UnaryOperator<String> failureDescription, Producer<T> producer) {
        try {
            return producer.create();
        } catch (Exception exception) {
            throw Exceptions.createHandled()
                            .withDirectMessage(failureDescription.apply(exception.getMessage()))
                            .handle();
        }
    }

    /**
     * Obtains the current context as string.
     *
     * @return the current error context as string
     */
    public String getContextAsString() {
        return context.entrySet()
                      .stream()
                      .map(entry -> NLS.smartGet(entry.getKey()) + ": " + entry.getValue())
                      .collect(Collectors.joining(", "));
    }

    private void logException(HandledException exception) {
        TaskContext taskContext = TaskContext.get();
        if (taskContext.getAdapter() instanceof ProcessContext processContext) {
            processContext.log(ProcessLog.error()
                                         .withHandledException(exception)
                                         .withMessage(enhanceMessage(exception.getMessage())));
        } else {
            taskContext.log(enhanceMessage(exception.getMessage()));
        }
    }

    /**
     * Enhances the given error message by appending the current content.
     *
     * @param errorMessage the message to enhance
     * @return the enhanced error message
     */
    public String enhanceMessage(String errorMessage) {
        String contextAsString = getContextAsString();
        if (Strings.isFilled(contextAsString)) {
            return errorMessage + " (" + contextAsString + ")";
        } else {
            return errorMessage;
        }
    }

    /**
     * Performs the given task and handles / {@link #enhanceMessage(String) enhances} all thrown errors.
     *
     * @param task the task to actually perform
     */
    public void perform(UnitOfWork task) {
        performAndGet(() -> {
            task.execute();
            return null;
        });
    }

    /**
     * Performs the given task and directly logs any occurring error.
     * <p>
     *
     * @param label the name of the value (this will be {@link NLS#smartGet(String) auto translated}).
     * @param value the value to store
     * @param task  the task to perform
     */
    public void performInContext(String label, Object value, UnitOfWork task) {
        performInContextAndGet(label, value, () -> {
            task.execute();
            return null;
        });
    }

    /**
     * Executes the given supplier and directly reports any occurring error.
     *
     * @param producer the producer to execute
     * @return an optional containing the object returned by the supplier or an empty optional if exceptions happened during execution
     */
    public <T> Optional<T> performAndGet(Producer<T> producer) {
        return performInContextAndGet(null, null, producer);
    }

    /**
     * Executes the given supplier and directly reports any occurring error.
     *
     * @param label    the name of the value (this will be {@link NLS#smartGet(String) auto translated}).
     * @param value    the value to store
     * @param producer the producer to execute
     * @return an optional containing the object returned by the supplier or an empty optional if exceptions happened during execution
     */
    public <T> Optional<T> performInContextAndGet(String label, Object value, Producer<T> producer) {
        withContext(label, value);
        try {
            return Optional.ofNullable(producer.create());
        } catch (HandledException exception) {
            logException(exception);
        } catch (Exception exception) {
            String message = exception.getMessage() + " (" + exception.getClass().getName() + ")";
            logException(Exceptions.handle().to(Log.BACKGROUND).error(exception).withDirectMessage(message).handle());
        } finally {
            removeContext(label);
        }
        return Optional.empty();
    }

    /**
     * Executes the given task and if any exception happens, applies the given failure description and throws a handled exception.
     *
     * @param failureDescription annotates a given error message so that the user is notified what task actually went
     *                           wrong. This should be in "negative form" like "Cannot perform x because: message" as
     *                           it is only used for error reporting.
     * @param task               the task to execute
     */
    public void execute(UnaryOperator<String> failureDescription, UnitOfWork task) {
        executeAndGet(failureDescription, () -> {
            task.execute();
            return null;
        });
    }

    /**
     * Performs the given task as "import" of the given entity.
     * <p>
     * This will report a canned error message if the task fails.
     *
     * @param entityDescriptor the name or label which identifies the entity being imported. When in doubt, use
     *                         <tt>entity::toString</tt>.
     * @param task             the import task to perform
     */
    public void performImport(Supplier<String> entityDescriptor, UnitOfWork task) {
        perform(() -> execute(message -> NLS.fmtr("ErrorContext.importError")
                                            .set("message", message)
                                            .set("entity", entityDescriptor.get())
                                            .format(), task));
    }

    /**
     * Performs the given task "deleting" the given entity.
     * <p>
     * This will report a canned error message if the task fails.
     *
     * @param entityDescriptor the name or label which identifies the entity being deleted. When in doubt, use
     *                         <tt>entity::toString</tt>.
     * @param task             the import task to perform
     */
    public void performDelete(Supplier<String> entityDescriptor, UnitOfWork task) {
        perform(() -> execute(message -> NLS.fmtr("ErrorContext.deleteError")
                                            .set("message", message)
                                            .set("entity", entityDescriptor.get())
                                            .format(), task));
    }

    @Override
    public SubContext fork() {
        ErrorContext fork = new ErrorContext();
        fork.context.putAll(this.context);
        return fork;
    }

    @Override
    public void detach() {
        context.clear();
    }
}
