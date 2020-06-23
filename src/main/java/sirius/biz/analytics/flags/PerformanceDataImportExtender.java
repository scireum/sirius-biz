/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.biz.importer.BaseImportHandler;
import sirius.biz.importer.EntityImportHandlerExtender;
import sirius.biz.importer.ImporterContext;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.di.std.Register;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adds an import extender to export all toggled performance flags.
 */
@Register
public class PerformanceDataImportExtender implements EntityImportHandlerExtender {

    @Nullable
    @Override
    public <E extends BaseEntity<?>> Function<E, Object> createExtractor(BaseImportHandler<E> handler,
                                                                         EntityDescriptor descriptor,
                                                                         ImporterContext context,
                                                                         String fieldToExport) {
        if (!PerformanceFlagged.class.isAssignableFrom(descriptor.getType())) {
            return null;
        }
        if (PerformanceFlagged.PERFORMANCE_DATA.inner(PerformanceData.FLAGS).toString().equals(fieldToExport)) {
            return this::extractFlags;
        }

        return null;
    }

    private String extractFlags(BaseEntity<?> entity) {
        return ((PerformanceFlagged) entity).getPerformanceData()
                                            .activeFlags()
                                            .filter(PerformanceFlag::isVisible)
                                            .map(PerformanceFlag::getName)
                                            .collect(Collectors.joining(","));
    }
}
