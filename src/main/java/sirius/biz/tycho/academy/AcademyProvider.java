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
import sirius.kernel.settings.Settings;

import java.util.List;
import java.util.function.Consumer;

public interface AcademyProvider extends Named {

    void fetchVideos(String academy, Extension config, Consumer<AcademyVideoData> videoConsumer) throws Exception;

}
