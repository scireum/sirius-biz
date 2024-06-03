/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.health.Exceptions;
import sirius.web.security.UserContext;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.util.Locale;

/**
 * Validates an XML source against an XSD source and keeps track if any error occurred during validation.
 */
public class XMLValidator {

    private final ProcessContext process;

    /**
     * Creates a validator that logs errors into the given process context.
     *
     * @param process the context to log errors into
     */
    public XMLValidator(ProcessContext process) {
        this.process = process;
    }

    /**
     * Determines if the {@link Source xml source} is valid by validating it with the given {@link Source xsd source}.
     *
     * @param xml the XML source to be validated
     * @param xsd the XSD source used as the schema for the validator
     * @return <tt>true</tt> if the xml source is valid, <tt>false</tt> otherwise
     * @throws SAXException in case of an invalid xsd source
     */
    public boolean validate(Source xml, Source xsd) throws SAXException {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(xsd);

        Validator validator = schema.newValidator();
        String language = UserContext.getCurrentUser().getLanguage();
        // Apply a workaround for using the english translations as those are not stored in an extra "en" resource
        // bundle but in the root one instead.
        Locale locale = Strings.areEqual(language, "en") ? Locale.ROOT : Locale.forLanguageTag(language);
        validator.setProperty("http://apache.org/xml/properties/locale", locale);

        XMLValidatorErrorHandler errorHandler = new XMLValidatorErrorHandler(process);
        validator.setErrorHandler(errorHandler);

        try {
            validator.validate(xml);
        } catch (SAXParseException exception) {
            // Cause of the exception is already logged via validator's error handler.
            Exceptions.ignore(exception);
        } catch (IOException exception) {
            process.log(ProcessLog.error().withMessage(exception.getMessage()));
            return false;
        }

        return !errorHandler.errorOccurred();
    }

    /**
     * Logs errors to a {@link ProcessContext} and keeps track if any error occurred during validation.
     */
    private static class XMLValidatorErrorHandler implements ErrorHandler {

        private final ProcessContext process;

        private final Monoflop error = Monoflop.create();

        /**
         * Creates an error handler that logs exceptions to the provided {@link ProcessContext}.
         *
         * @param process the context to log exceptions into
         */
        private XMLValidatorErrorHandler(ProcessContext process) {
            this.process = process;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            log(exception, ProcessLog.warn());
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            error.toggle();
            log(exception, ProcessLog.error());
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            error(exception);
        }

        private void log(SAXParseException exception, ProcessLog processLog) {
            process.log(processLog.withNLSKey("XMLValidatorErrorHandler.error")
                                  .withContext("line", exception.getLineNumber())
                                  .withContext("message", exception.getMessage())
                                  .withLimitedMessageType("$XMLValidatorErrorHandler.error.messageType",
                                                          ProcessLog.MESSAGE_TYPE_COUNT_MEDIUM));
        }

        /**
         * Returns <tt>true</tt> if any (fatal) error occurred, <tt>false</tt> otherwise.
         *
         * @return <tt>true</tt> if any (fatal) error occurred, <tt>false</tt> otherwise
         */
        public boolean errorOccurred() {
            return error.isToggled();
        }
    }
}
