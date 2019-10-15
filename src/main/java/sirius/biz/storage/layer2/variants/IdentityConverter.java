/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer2.variants;

import sirius.biz.storage.layer1.FileHandle;
import sirius.biz.storage.layer2.Blob;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;

import javax.annotation.Nonnull;

/**
 * Provides a simple NOOP converter.
 * <p>
 * This is mainly used for tests and default settings as this doesn't provide any real benefits.
 */
public class IdentityConverter implements Converter {

    /**
     * The factory used to create new instances of the converter.
     * <p>
     * Due to the simplicity of this class, no configuration is reuqired at all.
     */
    @Register
    public static class Factory implements ConverterFactory {

        @Override
        public Converter createConverter(Extension variantConfig, Extension converterConfig) {
            return new IdentityConverter();
        }

        @Nonnull
        @Override
        public String getName() {
            return "identity";
        }
    }

    @Override
    public FileHandle performConversion(Blob blob) throws Exception {
        return blob.download().orElseThrow(() -> new IllegalArgumentException("Blob does not contain any data."));
    }
}
