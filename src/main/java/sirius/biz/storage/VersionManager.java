/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import com.google.common.io.ByteStreams;
import sirius.db.KeyGenerator;
import sirius.db.mixing.OMA;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Exec;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.Formatter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for managing and creating resized versions of objects.
 * <p>
 * This class also maintans the <tt>logicalToPhysicalCache</tt> which contains the physical keys for virtual objects.
 * This is placed here, as the {@link DownloadBuilder} uses this class to fetch the physical keys for an object version
 * (even for the main one).
 */
@Register(classes = VersionManager.class)
public class VersionManager {

    @Part
    private OMA oma;

    @Part
    private Tasks tasks;

    @Part
    private KeyGenerator keyGen;

    @Part
    private Storage storage;

    @ConfigValue("storage.conversionCommand")
    private String conversionCommand;
    private Boolean commandPresent;

    private Cache<String, Tuple<VirtualObject, Map<String, String>>> logicalToPhysicalCache =
            CacheManager.createCache("storage-object-metadata");

    /**
     * Returns the targeted {@link VirtualObject} and its known physical objects for versions.
     *
     * @param downloadBuilder the buider which specifies the key and bucket of the virtual object to resolve
     * @return a tuple containing the virtual object and a map with all loaded physical keys for versions (not
     * necessarily all that are available - as these are lazy loaded)
     */
    protected Tuple<VirtualObject, Map<String, String>> fetchPhysicalObjects(DownloadBuilder downloadBuilder) {
        String cacheKey = downloadBuilder.getBucket() + "-" + downloadBuilder.getObjectKey();
        Tuple<VirtualObject, Map<String, String>> physicalObjects = logicalToPhysicalCache.get(cacheKey);
        if (physicalObjects == null) {
            VirtualObject obj = oma.select(VirtualObject.class)
                                   .eq(VirtualObject.BUCKET, downloadBuilder.getBucket())
                                   .eq(VirtualObject.OBJECT_KEY, downloadBuilder.getObjectKey())
                                   .queryFirst();
            if (obj != null) {
                physicalObjects = Tuple.create(obj, null);
                logicalToPhysicalCache.put(cacheKey, physicalObjects);
            }
        }
        return physicalObjects;
    }

    /**
     * Fetches the pyhsical key for a version from the tuple retrieved via {@link #fetchPhysicalObjects(DownloadBuilder)}
     *
     * @param physicalObjects the map of already resolved keys
     * @param version         the version to resolve
     * @return the physical key. If no version exists, a new one will be comouted and the main version will be used in
     * the mean time
     */
    protected String fetchVersion(Tuple<VirtualObject, Map<String, String>> physicalObjects, String version) {
        if (physicalObjects.getSecond() != null) {
            String result = physicalObjects.getSecond().get(version);
            if (Strings.isFilled(result)) {
                return result;
            }
        }

        if (!physicalObjects.getFirst().isImage()) {
            return null;
        }

        if (physicalObjects.getSecond() == null) {
            physicalObjects.setSecond(new ConcurrentHashMap<>());
        }

        VirtualObjectVersion objectVersion = oma.select(VirtualObjectVersion.class)
                                                .eq(VirtualObjectVersion.VIRTUAL_OBJECT, physicalObjects.getFirst())
                                                .eq(VirtualObjectVersion.VERSION_KEY, version)
                                                .queryFirst();
        if (objectVersion != null) {
            if (Strings.isFilled(objectVersion.getPhysicalKey())) {
                physicalObjects.getSecond().put(version, objectVersion.getPhysicalKey());
                return objectVersion.getPhysicalKey();
            } else {
                return null;
            }
        }

        computeVersion(physicalObjects.getFirst(), version);
        return null;
    }

    private void computeVersion(VirtualObject object, String version) {
        VirtualObjectVersion objectVersion = new VirtualObjectVersion();
        objectVersion.setBucket(object.getBucket());
        objectVersion.getVirtualObject().setValue(object);
        objectVersion.setVersionKey(version);
        oma.update(objectVersion);

        tasks.executor("storage-versions")
             .dropOnOverload(() -> oma.delete(objectVersion))
             .start(() -> performVersionComputation(objectVersion));
    }

    private void performVersionComputation(VirtualObjectVersion objectVersion) {
        VirtualObject object = objectVersion.getVirtualObject().getValue();
        try {
            Tuple<String, String> widthAndHeight = Strings.split(objectVersion.getVersionKey(), "x");
            int width = Integer.parseInt(widthAndHeight.getFirst());
            int height = Integer.parseInt(widthAndHeight.getSecond());

            convertAndStore(objectVersion, object, width, height);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(Storage.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to convert %s (%s): %s (%s)",
                                              object.getObjectKey(),
                                              object.getPath())
                      .handle();
            oma.delete(objectVersion);
        }
    }

    private void convertAndStore(VirtualObjectVersion objectVersion, VirtualObject object, int width, int height)
            throws IOException {
        File resultingFile = convert(object, width, height);
        try {
            if (resultingFile == null || resultingFile.length() == 0) {
                Storage.LOG.WARN("Converting %s (%s) to %sx%s resulted in an empty file.",
                                 object.getObjectKey(),
                                 object.getPath(),
                                 width,
                                 height);
                oma.delete(objectVersion);
            } else {
                objectVersion.setPhysicalKey(keyGen.generateId());
                objectVersion.setFileSize(resultingFile.length());
                objectVersion.setMd5(storage.calculateMd5(resultingFile));
                oma.override(objectVersion);
                try (InputStream in = new FileInputStream(resultingFile)) {
                    storage.getStorageEngine(object.getBucket())
                           .storePhysicalObject(object.getBucket(),
                                                objectVersion.getPhysicalKey(),
                                                in,
                                                null,
                                                resultingFile.length());
                }
            }
        } finally {
            if (resultingFile != null) {
                if (!resultingFile.delete()) {
                    Exceptions.handle()
                              .to(Storage.LOG)
                              .withSystemErrorMessage("Cannot delete: %s", resultingFile.getAbsolutePath())
                              .handle();
                }
            }
        }
    }

    private File convert(VirtualObject object, int width, int height) throws IOException {
        if (isCommandLineAvailable()) {
            return convertUsingCLI(object, width, height);
        }

        return convertUsingJava(object, width, height);
    }

    private boolean isCommandLineAvailable() {
        if (commandPresent == null) {
            checkForCommandLine();
        }

        return commandPresent;
    }

    private void checkForCommandLine() {
        commandPresent = Strings.isFilled(conversionCommand);
        if (!commandPresent) {
            Storage.LOG.WARN("No ImageMagick command is given in 'storage.conversionCommand'."
                             + " Using Java conversion as fallback."
                             + " Note that ImageMagick is faster and supports more file formats.");
        }
    }

    /**
     * Resizes an image with an external tool over the CLI.
     *
     * <p>
     * Because we can have different {@link PhysicalStorageEngine ways of storing the files}, we first need to create
     * two temporary files. The first one is the source image and the he second one is the destination image. The source
     * file needs to be filled with the data from the {@link Storage}.
     *
     * @param object the metadata for the virtual file
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the destination file
     * @throws IOException in case of an IO error
     */
    private File convertUsingCLI(VirtualObject object, int width, int height) throws IOException {
        File src = File.createTempFile("resize-in-", "." + object.getFileExtension());
        try (FileOutputStream out = new FileOutputStream(src)) {
            ByteStreams.copy(storage.getData(object), out);

            File dest = File.createTempFile("resize-out-", ".jpg");
            String command = Formatter.create(conversionCommand)
                                      .set("src", src.getAbsolutePath())
                                      .set("dest", dest.getAbsolutePath())
                                      .set("width", width)
                                      .set("height", height)
                                      .format();
            try {
                Exec.exec(command);
            } catch (Exec.ExecException e) {
                Exceptions.handle()
                          .to(Storage.LOG)
                          .error(e)
                          .withSystemErrorMessage("Failed to invoke: %s to resize %s (%s) to %sx%s",
                                                  command,
                                                  object.getObjectKey(),
                                                  object.getPath(),
                                                  width,
                                                  height)
                          .handle();
            }

            return dest;
        } finally {
            if (!src.delete()) {
                Exceptions.handle()
                          .to(Storage.LOG)
                          .withSystemErrorMessage("Cannot delete: %s", src.getAbsolutePath())
                          .handle();
            }
        }
    }

    private File convertUsingJava(VirtualObject object, int width, int height) throws IOException {
        BufferedImage src;
        try (InputStream input = storage.getData(object)) {
            src = ImageIO.read(input);
        }

        if (src == null) {
            return null;
        }

        BufferedImage dest = resize(src, width, height);
        return writeJPEG(dest, 0.9f);
    }

    /**
     * Resizes the given image into a new image with the given dimensions.
     */
    private BufferedImage resize(BufferedImage image, int requestedWidth, int requestedHeight) {
        double thumbRatio = (double) requestedWidth / requestedHeight;
        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);
        double aspectRatio = (double) imageWidth / imageHeight;

        int newWidth = requestedWidth;
        int newHeight = requestedHeight;
        if (thumbRatio < aspectRatio) {
            newHeight = (int) (newWidth / aspectRatio);
        } else {
            newWidth = (int) (newHeight * aspectRatio);
        }

        return getScaledInstance(image, newWidth, newHeight);
    }

    /**
     * Convenience method that returns a scaled instance of the provided {@code
     * BufferedImage}.
     * <p>
     * http://today.java.net/pub/a/today/2007/04/03/perils-of-image-
     * getscaledinstance.html
     *
     * @param img          the original image to be scaled
     * @param targetWidth  the desired width of the scaled instance, in pixels
     * @param targetHeight the desired height of the scaled instance, in pixels
     * @return a scaled version of the original {@code BufferedImage}
     * @author Chris Campbell
     */
    private BufferedImage getScaledInstance(BufferedImage img, int targetWidth, int targetHeight) {
        int type = (img.getTransparency() == Transparency.OPAQUE) ?
                   BufferedImage.TYPE_INT_RGB :
                   BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = img;
        int width = img.getWidth();
        int height = img.getHeight();
        do {
            if (width > targetWidth) {
                width /= 2;
            }
            if (width < targetWidth) {
                width = targetWidth;
            }
            if (height > targetHeight) {
                height /= 2;
            }
            if (height < targetHeight) {
                height = targetHeight;
            }

            BufferedImage tmp = new BufferedImage(width, height, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(ret, 0, 0, width, height, null);
            g2.dispose();
            ret = tmp;
        } while (width != targetWidth || height != targetHeight);

        return ret;
    }

    /**
     * Stores a buffered image into a JPEG file.
     */
    private File writeJPEG(BufferedImage img, float compressionQuality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();

        File result = File.createTempFile("resize-", ".jpg");

        // Prepare output file
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(result)) {
            writer.setOutput(ios);

            // Set the compression quality
            ImageWriteParam iwparam = new JPEGImageWriteParam(Locale.getDefault());
            iwparam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            iwparam.setCompressionQuality(compressionQuality);

            // Write the image
            writer.write(null, new IIOImage(img, null, null), iwparam);

            // Cleanup
            ios.flush();
            writer.dispose();
        }

        return result;
    }
}
