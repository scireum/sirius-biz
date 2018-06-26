/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.reports;

import sirius.kernel.di.std.Register;

/**
 * Provides helper methods to create reports.
 */
@Register(classes = Reports.class)
public class Reports {
//
//    @Part
//    private Storage storage;
//
//    @Part
//    private Mongo mongo;
//
//    @Part
//    private KeyGenerator keyGen;
//
//    public static final Log LOG = Log.get("reports");
//
//    public boolean hasReport(String companyId, String technicalName) {
//        return mongo.find()
//                    .where(Report.COMPANY, companyId)
//                    .where(Report.TECHNICAL_NAME, technicalName)
//                    .countIn(Report.COLLECTION) > 0;
//    }
//
//    public ReportBuilder createReport(String forCompany, String name) {
//        return new ReportBuilder(forCompany, name);
//    }
//
//    public static Consumer<File> generateCSV(Consumer<CSVWriter> writerConsumer) {
//        return file -> {
//            try (FileOutputStream outputStream = new FileOutputStream(file);
//                 CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream, Charsets.UTF_8))) {
//                writerConsumer.accept(writer);
//            } catch (Exception e) {
//                Exceptions.handle(LOG, e);
//            }
//        };
//    }
//
//    public static Consumer<File> generateExcel(Consumer<ExcelExport> exportConsumer) {
//        return file -> {
//            ExcelExport excelExport = new ExcelExport();
//            exportConsumer.accept(excelExport);
//            try (FileOutputStream outputStream = new FileOutputStream(file)) {
//                excelExport.writeToStream(outputStream);
//            } catch (Exception e) {
//                Exceptions.handle(LOG, e);
//            }
//        };
//    }
//
//    public class ReportBuilder {
//
//        private String name;
//        private String description;
//        private String technicalName;
//        private String filename;
//        private String forCompany;
//        private String forAccount;
//        private boolean overwrite;
//
//        private ReportBuilder(String forCompany, String name) {
//            this.forCompany = forCompany;
//            this.name = name;
//        }
//
//        public ReportBuilder withDescription(String description) {
//            this.description = description;
//            return this;
//        }
//
//        public ReportBuilder overwrite(String technicalName) {
//            this.technicalName = technicalName;
//            this.overwrite = true;
//            return this;
//        }
//
//        public ReportBuilder ifNotPresent(String technicalName) {
//            this.technicalName = technicalName;
//            this.overwrite = false;
//            return this;
//        }
//
//        public ReportBuilder forAccount(String forAccount) {
//            this.forAccount = forAccount;
//            return this;
//        }
//
//        public ReportBuilder filename(String filename) {
//            this.filename = filename;
//            return this;
//        }
//
//        public void generateReport(Consumer<File> reportBuilder) {
//            Document report = null;
//
//            if (Strings.isEmpty(filename)) {
//                throw new IllegalArgumentException("filename must not be empty.");
//            }
//
//            if (Strings.isFilled(technicalName)) {
//                report = mongo.find()
//                              .where(Report.COMPANY, forCompany)
//                              .where(Report.TECHNICAL_NAME, technicalName)
//                              .singleIn(Report.COLLECTION)
//                              .orElse(null);
//                if (report != null && !overwrite) {
//                    return;
//                }
//            }
//
//            report = createOrUpdateReport(report);
//
//            fillReport(reportBuilder, report);
//        }
//
//        private void fillReport(Consumer<File> reportBuilder, Document report) {
//            try {
//                File tmp = File.createTempFile("report", "data");
//                try {
//                    reportBuilder.accept(tmp);
//
//                    storage.saveReport(String.valueOf(report.get(Report.CREATED).asLocalDate(null).getYear()),
//                                       report.getString(Report.ID),
//                                       tmp);
//                    mongo.update()
//                         .where(Report.ID, report.getString(Report.ID))
//                         .set(Report.COMPUTING, false)
//                         .set(Report.SIZE, tmp.length())
//                         .executeFor(Report.COLLECTION);
//                } finally {
//                    if (!tmp.delete()) {
//                        Exceptions.handle()
//                                  .to(LOG)
//                                  .withSystemErrorMessage("Cannot delete temporary file: %s", tmp.getAbsolutePath())
//                                  .handle();
//                    }
//                }
//            } catch (Exception ex) {
//                Exceptions.handle()
//                          .error(ex)
//                          .to(LOG)
//                          .withSystemErrorMessage("Failed to create the report: %s - %s (%s)", name)
//                          .handle();
//                mongo.delete().where(Report.ID, report.getString(Report.ID)).from(Report.COLLECTION);
//            }
//        }
//
//        private Document createOrUpdateReport(Document report) {
//            if (report == null) {
//                report = mongo.insert()
//                              .set(Report.ID, keyGen.generateId())
//                              .set(Report.NAME, name)
//                              .set(Report.DESCRIPTION, description)
//                              .set(Report.TECHNICAL_NAME, technicalName)
//                              .set(Report.COMPANY, forCompany)
//                              .set(Report.ACCOUNT, forAccount)
//                              .set(Report.FILENAME, filename)
//                              .set(Report.COMPUTING, true)
//                              .set(Report.CREATED, LocalDateTime.now())
//                              .set(Report.UPDATED, LocalDateTime.now())
//                              .into(Report.COLLECTION);
//            } else {
//                mongo.update()
//                     .where(Report.ID, report.getString(Report.ID))
//                     .set(Report.COMPUTING, true)
//                     .set(Report.UPDATED, LocalDateTime.now())
//                     .executeFor(Report.COLLECTION);
//            }
//            return report;
//        }
//    }
}
