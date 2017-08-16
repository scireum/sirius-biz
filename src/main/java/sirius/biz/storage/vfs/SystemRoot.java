/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.vfs;

import com.google.common.base.Charsets;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.metrics.Metric;
import sirius.kernel.health.metrics.Metrics;
import sirius.kernel.info.Module;
import sirius.kernel.info.Product;
import sirius.web.security.UserContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Provides some health and debugging data for sys admins.
 */
@Register
public class SystemRoot implements VFSRoot {

    @Part
    private Metrics metrics;

    @Override
    public void collectRootFolders(VirtualFile parent, Consumer<VirtualFile> fileCollector) {
        if (!UserContext.getCurrentUser().hasPermission(TenantUserManager.PERMISSION_SYSTEM_TENANT)) {
            return;
        }

        fileCollector.accept(new VirtualFile(parent, "system").withChildren((system, collector) -> {
            collector.accept(createStatsFile(parent));
            collector.accept(createVersionsFile(parent));
            VirtualFile logsDirectory = createLogsDirectory(parent);
            if (logsDirectory != null) {
                collector.accept(logsDirectory);
            }
        }));
    }

    private VirtualFile createLogsDirectory(VirtualFile parent) {
        File logsDir = new File("logs");
        if (!logsDir.exists() || !logsDir.isDirectory()) {
            return null;
        }

        return new VirtualFile(parent, "logs").withChildren((logsFile, consumer) -> {
            for (File child : logsDir.listFiles()) {
                if (child.isFile()) {
                    consumer.accept(wrapLogFile(logsFile, child));
                }
            }
        });
    }

    private VirtualFile wrapLogFile(VirtualFile logsFile, File child) {
        VirtualFile logFile = new VirtualFile(logsFile, child.getName());
        if (!"application.log".equals(logFile.getName())) {
            logFile.withDeleteHandler(child::delete);
        }

        logFile.withInputStreamSupplier(() -> {
            try {
                return new FileInputStream(child);
            } catch (IOException e) {
                Exceptions.handle(e);
                return null;
            }
        });

        return logFile;
    }

    private VirtualFile createVersionsFile(VirtualFile parent) {
        return new VirtualFile(parent, "versions").withInputStreamSupplier(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(Product.getProduct().toString()).append("\n");
            for (Module module : Product.getModules()) {
                sb.append(module.toString()).append("\n");
            }

            return new ByteArrayInputStream(sb.toString().getBytes(Charsets.UTF_8));
        });
    }

    private VirtualFile createStatsFile(VirtualFile parent) {
        return new VirtualFile(parent, "stats").withInputStreamSupplier(() -> {
            StringBuilder sb = new StringBuilder();
            for (Metric metric : metrics.getMetrics()) {
                sb.append(metric.getName());
                sb.append(": ");
                sb.append(metric.getValueAsString());
                sb.append("\n");
            }

            return new ByteArrayInputStream(sb.toString().getBytes(Charsets.UTF_8));
        });
    }
}
