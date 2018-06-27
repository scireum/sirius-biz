/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.reports;

import sirius.kernel.di.std.Register;
import sirius.web.controller.BasicController;
import sirius.web.controller.Controller;

@Register(classes = Controller.class)
public class ReportsController extends BasicController {
//
//    @Part
//    private Mongo mongo;
//
//    @Part
//    private Storage storage;
//
//    @Permission(AccountUserManager.PERMISSION_HAS_COMPANY)
//    @DefaultRoute
//    @Routed("/reports")
//    public void reports(WebContext ctx) {
//        Page<Document> page = new Page<>();
//        page.withStart(1);
//        page.withPageSize(15);
//        page.bindToRequest(ctx);
//
//        Finder finder = mongo.find().where(Report.COMPANY, getUser().getTenantId());
//        //TODO filter by user is necessarrrrrry
//
//        if (Strings.isFilled(page.getQuery())) {
//            finder.where(Filter.regex(Report.NAME, ".*" + page.getQuery() + ".*", "i"));
//        }
//
//        page.withItems(Lists.newArrayList());
//        finder.limit(page.getStart() - 1, page.getPageSize() + 1);
//        finder.orderByDesc(Report.UPDATED);
//        finder.allIn(Report.COLLECTION, report -> {
//            if (page.getItems().size() < page.getPageSize()) {
//                page.getItems().add(report);
//            } else {
//                page.withHasMore(true);
//            }
//        });
//        ctx.respondWith().template("templates/settings/reports/reports.html.pasta", page);
//    }
//
//    private Document findReport(String reportId) {
//        Finder finder = mongo.find().where(Report.ID, reportId).where(Report.COMPANY, getUser().getTenantId());
//        //TODO
//
//        return finder.singleIn(Report.COLLECTION)
//                     .orElseThrow(() -> Exceptions.createHandled()
//                                                  .withNLSKey("ReportsController.unknownReport"/*TODO*/)
//                                                  .handle());
//    }
//
//    @Permission(AccountUserManager.PERMISSION_HAS_COMPANY)
//    @Routed("/report/:1/download")
//    public void reportData(WebContext ctx, String reportId) {
//        Document report = findReport(reportId);
//        ctx.respondWith()
//           .download(report.getString(Report.FILENAME))
//           .tunnel(storage.createReportDeliveryURL(String.valueOf(report.get(Report.CREATED)
//                                                                        .asLocalDate(LocalDate.MIN)
//                                                                        .getYear()), reportId));
//    }
}
