/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer3.VirtualFile;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.biz.util.UnzipHelper;
import sirius.kernel.commons.Files;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Provides a job able to extract archives from the {@link VirtualFileSystem}. The following file extensions are supported: {@link UnzipHelper#getSupportedFileExtensions()}.
 */
public class VirtualFileExtractionJob extends BatchJob {

    @Part
    private static VirtualFileSystem vfs;

    @Part
    private static UnzipHelper unzipHelper;

    VirtualFileExtractionJob(ProcessContext process) {
        super(process);
    }

    @Override
    public void execute() throws Exception {
        VirtualFile sourceFile = process.require(VirtualFileExtractionJobFactory.SOURCE_PARAMETER);
        Optional<VirtualFile> destinationDirectory =
                VirtualFileExtractionJobFactory.DESTINATION_PARAMETER.get(process.getContext());
        boolean shouldOverwriteExisting =
                process.require(VirtualFileExtractionJobFactory.OVERWRITE_EXISTING_FILES_PARAMETER);

        // by default we'll use the files directory to extract to
        final VirtualFile targetDirectory =
                destinationDirectory.orElseGet(() -> vfs.resolve(sourceFile.parent().path()));

        sourceFile.tryDownload().ifPresent(fileHandle -> {
            File tempFile = fileHandle.getFile();
            try {
                UnzipHelper.unzip(tempFile,
                                  null,
                                  (status, data, filePath, filesProcessedSoFar, bytesProcessedSoFar, totalBytes) -> {
                                      VirtualFile targetFile =
                                              vfs.resolve(vfs.makePath(targetDirectory.name(), filePath));
                                      if (targetFile.exists() && !shouldOverwriteExisting) {
                                          process.log(ProcessLog.info()
                                                                .withMessage(NLS.fmtr(
                                                                        "VirtualFileExtractionJob.skippingOverwrite")
                                                                                .set("targetPath", targetFile.path())
                                                                                .format()));
                                          return false;
                                      }

                                      try {
                                          if (targetFile.exists() && shouldOverwriteExisting) {
                                              process.log(ProcessLog.info()
                                                                    .withMessage(NLS.fmtr(
                                                                            "VirtualFileExtractionJob.extractingFile")
                                                                                    .set("filePath", filePath)
                                                                                    .set("targetPath",
                                                                                         targetFile.path())
                                                                                    .set("fileSize",
                                                                                         NLS.formatSize(data.size()))
                                                                                    .format()));
                                          } else {
                                              process.log(ProcessLog.info()
                                                                    .withMessage(NLS.fmtr(
                                                                            "VirtualFileExtractionJob.overwritingFile")
                                                                                    .set("filePath", filePath)
                                                                                    .set("targetPath",
                                                                                         targetFile.path())
                                                                                    .set("fileSize",
                                                                                         NLS.formatSize(data.size()))
                                                                                    .format()));
                                          }

                                          targetFile.consumeStream(data.openStream(), data.size());
                                      } catch (IOException e) {
                                          process.handle(e);
                                      }

                                      process.log(ProcessLog.info()
                                                            .withMessage(NLS.fmtr("VirtualFileExtractionJob.progress")
                                                                            .set("status", status)
                                                                            .set("filesProcessedSoFar",
                                                                                 filesProcessedSoFar)
                                                                            .set("dataProcessedSoFar",
                                                                                 NLS.formatSize(bytesProcessedSoFar))
                                                                            .set("sizeTotal",
                                                                                 NLS.formatSize(totalBytes))
                                                                            .format()));
                                      return true;
                                  });
            } catch (IOException e) {
                process.handle(e);
            } finally {
                Files.delete(tempFile);
            }
        });
    }
}