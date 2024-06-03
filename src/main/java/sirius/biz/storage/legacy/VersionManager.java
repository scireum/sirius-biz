/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.legacy;

import com.google.common.io.ByteStreams;
import sirius.db.KeyGenerator;
import sirius.db.jdbc.OMA;
import sirius.kernel.async.Tasks;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Exec;
import sirius.kernel.commons.Files;
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
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 *
 * @deprecated use the new storage APIs
 */
@Deprecated
@Register(classes = VersionManager.class, framework = Storage.FRAMEWORK_STORAGE)
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

    @ConfigValue("storage.extendOption")
    private String extendOption;

    private Boolean commandPresent;

    private final Cache<String, Tuple<VirtualObject, Map<String, String>>> logicalToPhysicalCache =
            CacheManager.createCoherentCache("storage-object-metadata");

    private static final String PNG_IMAGE = "png";
    private static final String JPG_IMAGE = "jpg";

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
     * Clears the cache for a given {@link VirtualObject}.
     *
     * @param virtualObject the virtual object
     */
    protected void clearCacheForVirtualObject(VirtualObject virtualObject) {
        logicalToPhysicalCache.remove(virtualObject.getBucket() + "-" + virtualObject.getObjectKey());
    }

    /**
     * Fetches the pyhsical key for a version from the tuple retrieved via {@link #fetchPhysicalObjects(DownloadBuilder)}
     *
     * @param physicalObjects the map of already resolved keys
     * @param version         the version to resolve, see {@link DownloadBuilder#withVersion(String)} for possible
     *                        values
     * @return the physical key. If no version exists, a new one will be computed and the main version will be used in
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
        VirtualObject object = objectVersion.getVirtualObject().fetchValue();
        try {
            Tuple<Integer, Integer> size = Tuple.create(0, 0);
            Tuple<Integer, Integer> extendedSize = Tuple.create(0, 0);
            String imageFormat = JPG_IMAGE;

            for (String part : objectVersion.getVersionKey().split(",")) {
                Tuple<String, String> keyValuePair = Strings.split(part, ":");
                String key = keyValuePair.getFirst().toLowerCase().trim();

                if ("size".equals(key)) {
                    size = parseWidthAndHeight(keyValuePair.getSecond());
                }

                if ("min".equals(key)) {
                    extendedSize = parseWidthAndHeight(keyValuePair.getSecond());
                }

                if ("imageformat".equals(key)) {
                    imageFormat = keyValuePair.getSecond().toLowerCase().trim();
                }
            }

            convertAndStore(objectVersion,
                            object,
                            size.getFirst(),
                            size.getSecond(),
                            extendedSize.getFirst(),
                            extendedSize.getSecond(),
                            imageFormat);
        } catch (Exception exception) {
            Exceptions.handle()
                      .to(Storage.LOG)
                      .error(exception)
                      .withSystemErrorMessage("Failed to convert %s (%s): %s (%s)",
                                              object.getObjectKey(),
                                              object.getPath())
                      .handle();
            oma.delete(objectVersion);
        }
    }

    private Tuple<Integer, Integer> parseWidthAndHeight(String value) {
        Tuple<String, String> widthAndHeight = Strings.split(value, "x");

        return Tuple.create(Integer.parseInt(widthAndHeight.getFirst().trim()),
                            Integer.parseInt(widthAndHeight.getSecond().trim()));
    }

    private void convertAndStore(VirtualObjectVersion objectVersion,
                                 VirtualObject object,
                                 int width,
                                 int height,
                                 int extendWidth,
                                 int extendHeight,
                                 String imageFormat) throws IOException {
        File resultingFile = convert(object, width, height, extendWidth, extendHeight, imageFormat);
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
            Files.delete(resultingFile);
        }
    }

    private File convert(VirtualObject object,
                         int width,
                         int height,
                         int extendWidth,
                         int extendHeight,
                         String imageFormat) throws IOException {
        if (isCommandLineAvailable()) {
            return convertUsingCLI(object, width, height, extendWidth, extendHeight, imageFormat);
        }

        return convertUsingJava(object, width, height, extendWidth, extendHeight, imageFormat);
    }

    private boolean isCommandLineAvailable() {
        if (commandPresent == null) {
            checkForCommandLine();
        }

        return commandPresent;
    }

    private void checkForCommandLine() {
        commandPresent = Strings.isFilled(conversionCommand);
        if (!Boolean.TRUE.equals(commandPresent)) {
            Storage.LOG.WARN("No ImageMagick command is given in 'storage.conversionCommand'."
                             + " Using Java conversion as fallback."
                             + " Note that ImageMagick is faster and supports more file formats.");
        }
    }

    /**
     * Resizes an image with an external tool over the CLI.
     * <p>
     * Because we can have different {@link PhysicalStorageEngine ways of storing the files}, we first need to create
     * two temporary files. The first one is the source image and the he second one is the destination image. The source
     * file needs to be filled with the data from the {@link Storage}.
     *
     * @param object       the metadata for the virtual file
     * @param width        the width in pixels
     * @param height       the height in pixels
     * @param extendWidth  the minimum extended width in pixels
     * @param extendHeight the minimum extended height in pixels
     * @param imageFormat  the image format to use
     * @return the destination file
     * @throws IOException in case of an IO error
     */
    private File convertUsingCLI(VirtualObject object,
                                 int width,
                                 int height,
                                 int extendWidth,
                                 int extendHeight,
                                 String imageFormat) throws IOException {
        File src = File.createTempFile("resize-in-", "." + object.getFileExtension());
        try (FileOutputStream out = new FileOutputStream(src)) {
            ByteStreams.copy(storage.getData(object), out);

            File dest = File.createTempFile("resize-out-", "." + imageFormat);
            Formatter formatter = Formatter.create(conversionCommand)
                                           .set("src", src.getAbsolutePath())
                                           .set("dest", dest.getAbsolutePath())
                                           .set("width", width)
                                           .set("height", height)
                                           .set("imageFormat", imageFormat);

            if (extendWidth > 0 || extendHeight > 0) {
                formatter.set("extend",
                              Formatter.create(extendOption)
                                       .set("extendWidth", extendWidth)
                                       .set("extendHeight", extendHeight)
                                       .format());
            } else {
                formatter.set("extend", "");
            }

            String command = formatter.format();

            try {
                Exec.exec(command);
            } catch (Exec.ExecException exception) {
                Exceptions.handle()
                          .to(Storage.LOG)
                          .error(exception)
                          .withSystemErrorMessage("Failed to invoke: %s to resize %s (%s) to %sx%s in %s imageFormat",
                                                  command,
                                                  object.getObjectKey(),
                                                  object.getPath(),
                                                  width,
                                                  height,
                                                  imageFormat)
                          .handle();
            }

            return dest;
        } finally {
            Files.delete(src);
        }
    }

    private File convertUsingJava(VirtualObject object,
                                  int width,
                                  int height,
                                  int extendWidth,
                                  int extendHeight,
                                  String imageFormat) throws IOException {
        BufferedImage src;
        try (InputStream input = storage.getData(object)) {
            src = ImageIO.read(input);
        }

        if (src == null) {
            return null;
        }

        BufferedImage dest = resize(src, width, height, extendWidth, extendHeight, imageFormat);
        if (imageFormat.equals(PNG_IMAGE)) {
            return writePNG(dest);
        }
        if (imageFormat.equals(JPG_IMAGE)) {
            return writeJPEG(dest, 0.9f);
        }

        return null;
    }

    /**
     * Resizes the given {@code BufferedImage} into a new image with the given dimensions.
     * <p>
     * First the image is converted to RGB and then scaled down to be at most the requested size. In this step the
     * aspect ratio is not changed. The image is then extended to meet the extended size.
     *
     * @param image           the original image to be resized
     * @param requestedWidth  the requested maximum width, in pixels
     * @param requestedHeight the requested maximum height, in pixels
     * @param extendWidth     the minimum extended width, in pixels
     * @param extendHeight    the minimum extended height, in pixels
     * @param imageFormat     the image format to use
     * @return a resized version of the original {@code BufferedImage}
     */
    private BufferedImage resize(BufferedImage image,
                                 int requestedWidth,
                                 int requestedHeight,
                                 int extendWidth,
                                 int extendHeight,
                                 String imageFormat) {
        double thumbRatio = (double) requestedWidth / requestedHeight;
        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);
        double aspectRatio = (double) imageWidth / imageHeight;

        BufferedImage newImage = image;

        newImage = getConvertedInstance(newImage, imageFormat);

        if (requestedWidth < imageWidth || requestedHeight < imageHeight) {
            int newWidth = requestedWidth;
            int newHeight = requestedHeight;
            if (thumbRatio < aspectRatio) {
                newHeight = (int) (newWidth / aspectRatio);
            } else {
                newWidth = (int) (newHeight * aspectRatio);
            }

            newImage = getScaledInstance(newImage, newWidth, newHeight, imageFormat);
        }

        newImage = getExtendedImageInstance(newImage, extendWidth, extendHeight, imageFormat);

        return newImage;
    }

    /**
     * Returns a to the target format converted instance of the provided {@code BufferedImage} .
     *
     * @param img         the original image to be scaled
     * @param imageFormat the format to transform into
     * @return a converted version of the original {@code BufferedImage}
     */
    private BufferedImage getConvertedInstance(BufferedImage img, String imageFormat) {
        BufferedImage newImage = null;

        if (imageFormat.equals(PNG_IMAGE)) {
            newImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = newImage.createGraphics();
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(img, 0, 0, null);
            g2.dispose();
        }

        if (imageFormat.equals(JPG_IMAGE)) {
            newImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = newImage.createGraphics();
            g2.drawImage(img, 0, 0, Color.WHITE, null);
            g2.dispose();
        }

        return newImage;
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
     * @param imageFormat  the format of the image
     * @return a scaled version of the original {@code BufferedImage}
     */
    private BufferedImage getScaledInstance(BufferedImage img, int targetWidth, int targetHeight, String imageFormat) {
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

            if (imageFormat.equals(PNG_IMAGE)) {
                BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = tmp.createGraphics();
                g2.setComposite(AlphaComposite.Src);
                g2.drawImage(ret, 0, 0, width, height, null);
                g2.dispose();
                ret = tmp;
            }

            if (imageFormat.equals(JPG_IMAGE)) {
                BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = tmp.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(ret, 0, 0, width, height, Color.WHITE, null);
                g2.dispose();
                ret = tmp;
            }
        } while (width != targetWidth || height != targetHeight);

        return ret;
    }

    /**
     * Extends the provided {@code BufferedImage} to be at least the specified width and height by putting a white
     * border around the original image.
     *
     * @param image        the original image to be extended
     * @param extendWidth  the minimum width of the extended instance, in pixels
     * @param extendHeight the minimum height of the extended instance, in pixels
     * @param imageFormat  the format of the image
     * @return a extended version of the original {@code BufferedImage}
     */
    private BufferedImage getExtendedImageInstance(BufferedImage image,
                                                   int extendWidth,
                                                   int extendHeight,
                                                   String imageFormat) {
        BufferedImage newImage = image;
        int width = image.getWidth();
        int height = image.getHeight();

        if (width < extendWidth || height < extendHeight) {
            extendWidth = Math.max(extendWidth, width);
            extendHeight = Math.max(extendHeight, height);

            if (imageFormat.equals(PNG_IMAGE)) {
                newImage = new BufferedImage(extendWidth, extendHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = newImage.createGraphics();
                g2.setComposite(AlphaComposite.Clear);
                g2.fillRect(0, 0, extendWidth, extendHeight);
                g2.setComposite(AlphaComposite.Src);
                g2.drawImage(image, (extendWidth - width) / 2, (extendHeight - height) / 2, null);
                g2.dispose();
            }

            if (imageFormat.equals(JPG_IMAGE)) {
                newImage = new BufferedImage(extendWidth, extendHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = newImage.createGraphics();
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, extendWidth, extendHeight);
                g2.drawImage(image, (extendWidth - width) / 2, (extendHeight - height) / 2, Color.WHITE, null);
                g2.dispose();
            }
        }

        return newImage;
    }

    /**
     * Stores a buffered image into a JPEG file.
     */
    private File writeJPEG(BufferedImage img, float compressionQuality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(JPG_IMAGE).next();

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

    /**
     * Stores a buffered image into a PNG file.
     */
    private File writePNG(BufferedImage img) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(PNG_IMAGE).next();

        File result = File.createTempFile("resize-", ".png");

        // Prepare output file
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(result)) {
            writer.setOutput(ios);

            // Write the image
            writer.write(new IIOImage(img, null, null));

            // Cleanup
            ios.flush();
            writer.dispose();
        }

        return result;
    }
}
