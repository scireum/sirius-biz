/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.kernel.di.std.Named;
import sirius.kernel.settings.Extension;

import java.util.function.Consumer;

/**
 * Represents a source of {@link AcademyVideo academy videos}.
 */
public interface AcademyProvider extends Named {

    /**
     * Invoked to enumerate all available videos for the given configuration.
     *
     * @param academy       the academy to fetch videos for
     * @param config        the configuration as present in the system config
     * @param videoConsumer the consumer which will persist the result into {@link AcademyVideo academy videos}
     * @throws Exception in case of an error while fetching videos
     */
    void fetchVideos(String academy, Extension config, Consumer<AcademyVideoData> videoConsumer) throws Exception;

    /**
     * Selects the template which actually renders the video.
     *
     * @return the name of the template which contains the actual video player
     */
    String getVideoTemplate();
}
