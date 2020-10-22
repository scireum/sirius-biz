/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.analytics.flags.ExecutionFlags;
import sirius.kernel.commons.Limit;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.xml.StructuredNode;
import sirius.kernel.xml.XMLCall;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Register(classes = UpdateManager.class)
public class UpdateManager {

    @Part
    private ExecutionFlags executionFlags;

    private List<UpdateInfo> updates = Collections.emptyList();
    private LocalDateTime lastFetch;

    public boolean hasUpdates(String reference) {
        if (getUpdates().isEmpty()) {
            return false;
        }

        Optional<LocalDateTime> lastView = executionFlags.readExecutionFlag(reference, "updates-last-view");
        if (!lastView.isPresent()) {
            return true;
        }

        LocalDateTime limit = LocalDateTime.now().minusMonths(3);

        return getUpdates().stream()
                           .filter(update -> update.getTimestamp().isAfter(limit))
                           .findFirst()
                           .filter(update -> update.getTimestamp().isAfter(lastView.get()))
                           .isPresent();
    }

    public List<UpdateInfo> getUpdates() {
        LocalDateTime limit = LocalDateTime.now().minusDays(1);
        if (lastFetch == null || lastFetch.isBefore(limit)) {
            fetchUpdates();
        }

        return Collections.unmodifiableList(updates);
    }

    private void fetchUpdates() {
        try {
            Limit limit = new Limit(0, 5);
            List<UpdateInfo> nextUpdates = new ArrayList<>();
            XMLCall call = XMLCall.to(new URL("https://blog.scireum.de/feed"));
            for (StructuredNode node : call.getInput().getNode("channel").queryNodeList("item")) {
                if (limit.nextRow()) {
                    UpdateInfo updateInfo =
                            new UpdateInfo(LocalDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(node.queryString(
                                    "pubDate"))),
                                           node.queryString("title"),
                                           node.queryString("description"),
                                           node.queryString("link"));
                    nextUpdates.add(updateInfo);
                }
            }
            updates = nextUpdates;
            lastFetch = LocalDateTime.now();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    public void markUpdatesAsShown(String reference) {
        executionFlags.storeExecutionFlag(reference, "updates-last-view", LocalDateTime.now(), Period.ofMonths(3));
    }
}
