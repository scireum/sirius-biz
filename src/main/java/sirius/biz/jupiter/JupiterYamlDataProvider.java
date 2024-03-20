/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides a base class for {@link JupiterDataProvider data providers} which generate YAML files.
 */
public abstract class JupiterYamlDataProvider implements JupiterDataProvider {

    @Override
    public void execute(OutputStream outputStream) throws Exception {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            executeExport(yamlObject -> {
                try {
                    yaml.dump(yamlObject, writer);
                    writer.write("---\n");
                } catch (IOException exception) {
                    throw Exceptions.handle()
                                    .error(exception)
                                    .to(Jupiter.LOG)
                                    .withSystemErrorMessage("Failed to write %s (%s): %s (%s)",
                                                            getFilename(),
                                                            getName())
                                    .handle();
                }
            });
        }
    }

    /**
     * Performs the actual export by emitting YAML objects into the given consumer.
     *
     * @param objectConsumer the consumer which collects all YAML objects to persist
     */
    protected abstract void executeExport(Consumer<Map<String, Object>> objectConsumer);
}
