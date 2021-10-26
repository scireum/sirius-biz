/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.storage.layer1.FileHandle;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * Provides a simple NOOP converter.
 * <p>
 * This is mainly used for tests and default settings as this doesn't provide any real benefits.
 */
public class IdentityConverter implements Converter {

    /**
     * The factory used to create new instances of the converter.
     * <p>
     * Due to the simplicity of this class, no configuration is required at all.
     */
    @Register
    public static class Factory implements ConverterFactory {

        @Override
        public Converter createConverter(Function<String, Value> configSupplier) {
            return new IdentityConverter();
        }

        @Nonnull
        @Override
        public String getName() {
            return "identity";
        }
    }

    @Override
    public void performConversion(ConversionProcess conversionProcess) throws Exception {
        FileHandle originalFile = conversionProcess.download(() -> conversionProcess.getBlobToConvert()
                                                                                    .download()
                                                                                    .orElseThrow(() -> new IllegalArgumentException(
                                                                                            "Blob does not contain any data.")));
        conversionProcess.withConversionResult(originalFile);
    }
}
