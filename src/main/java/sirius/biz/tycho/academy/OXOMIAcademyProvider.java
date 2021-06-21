/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.kernel.commons.URLBuilder;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;
import sirius.kernel.xml.StructuredNode;
import sirius.kernel.xml.XMLCall;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Register
public class OXOMIAcademyProvider implements AcademyProvider {

    @Override
    public void fetchVideos(String academy, Extension config, Consumer<AcademyVideoData> videoConsumer)
            throws Exception {
        XMLCall call = XMLCall.to(new URLBuilder("http://oxomi.test.scireum.com/service/xml/academy/videos").addParameter("portal", "HYPE")
                                                                                  .addParameter("user", "_showcase")
                                                                                  .addParameter("accessToken",
                                                                                                "d697f9a372cb4bb8835e881b30d93b01")
                                                                                  .addParameter("academyId",
                                                                                                "F3N6EED54NIARS42AINB2SG1K8")
                                                                                  .asURL());
        for (StructuredNode node : call.getInput().getNode(".").queryNodeList("list/entry")) {
            AcademyVideoData video = new AcademyVideoData();
            video.setVideoId(node.queryString("id"));
            video.setTitle(node.queryString("title"));
            video.setDescription(node.queryString("description"));
            video.setMandatory(node.queryValue("mandatory").asBoolean());
            video.setPriority(node.queryValue("priority").asInt(100));
            video.setDuration(node.queryValue("duration").asInt(0));
            video.setRequiredPermission(node.queryString("requiredRole"));
            video.setRequiredFeature(node.queryString("requiredFeature"));
            video.setTrackId(node.queryString("trackId"));
            video.setTrackName(node.queryString("track"));
            video.setPreviewUrl(node.queryString("previewImage"));
            videoConsumer.accept(video);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "oxomi";
    }
}
