package qupath.ext.align.core;

import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_video;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatExpr;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.LabeledImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.tools.OpenCVTools;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A static class to auto align one image on top of another.
 */
public class AutoAligner {

    private static final Logger logger = LoggerFactory.getLogger(AutoAligner.class);
    private static final int ECC_MAX_COUNT = 100;
    private static final double ECC_EPSILON = 0.0001;
    /**
     * Define what combinations of transformation can be used when performing auto alignment.
     */
    public enum TransformationTypes {
        /**
         * The returned transformation will be a combination of rotation, translation, scale, and
         * shear.
         */
        AFFINE,
        /**
         * The returned transformation will be a combination of rotation, translation, and
         * (if the alignment type is {@link AlignmentType#POINT_ANNOTATIONS}) uniform scaling.
         */
        RIGID
    }
    /**
     * Define what to look on the images when performing auto alignment.
     */
    public enum AlignmentType {
        /**
         * Auto alignment is performed by looking at the pixel values of the images.
         */
        INTENSITY,
        /**
         * Auto alignment is performed by looking at area annotations of the images.
         */
        AREA_ANNOTATIONS,
        /**
         * Auto alignment is performed by looking at point annotations of the images. Both images
         * must have the same number of points and at least one point each.
         */
        POINT_ANNOTATIONS
    }

    private AutoAligner() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Attempt to find a transformation that would align an image on top of another.
     *
     * @param baseImageData the image to align to
     * @param imageDataToAlign the image to align
     * @param initialTransform an initial transformation from the base image to the image to align. This will
     *                         be used as a starting point in some auto alignment algorithm. This function is
     *                         more likely to succeed if the images have been already coarsely aligned with the provided
     *                         transform. Not used if the alignment type is {@link AlignmentType#POINT_ANNOTATIONS}
     * @param alignmentType what to look on the images when performing auto alignment. Take a look at the
     *                      enumeration documentation for more information
     * @param transformationTypes what combinations of transformation can be used when performing auto alignment.
     *                            Take a look at the enumeration documentation for more information
     * @param downsample the downsample at which the alignment should take place. Not used if the alignment type
     *                   is {@link AlignmentType#POINT_ANNOTATIONS}
     * @return the transformation that aligns the provided image to align on top of the base image
     * @throws NullPointerException if one of the provided parameter is used and null
     * @throws Exception if the results don't converge or if any other error occurs
     */
    public static AffineTransform getAlignTransformation(
            ImageData<BufferedImage> baseImageData,
            ImageData<BufferedImage> imageDataToAlign,
            AffineTransform initialTransform,
            AlignmentType alignmentType,
            TransformationTypes transformationTypes,
            double downsample
    ) throws Exception {
        return switch (alignmentType) {
            case INTENSITY -> {
                logger.debug("Image alignment of {} on {} using intensities", imageDataToAlign, baseImageData);

                yield alignWithEccCriterion(baseImageData.getServer(), imageDataToAlign.getServer(), transformationTypes, initialTransform, downsample);
            }
            case AREA_ANNOTATIONS -> {
                logger.debug("Image alignment of {} on {} using area annotations", imageDataToAlign, baseImageData);

                Map<PathClass, Integer> labels = new LinkedHashMap<>();
                int label = 1;
                labels.put(PathClass.NULL_CLASS, label++);
                for (var annotation : baseImageData.getHierarchy().getAnnotationObjects()) {
                    var pathClass = annotation.getPathClass();
                    if (pathClass != null && !labels.containsKey(pathClass)) {
                        labels.put(pathClass, label++);
                    }
                }
                for (var annotation : imageDataToAlign.getHierarchy().getAnnotationObjects()) {
                    var pathClass = annotation.getPathClass();
                    if (pathClass != null && !labels.containsKey(pathClass)) {
                        labels.put(pathClass, label++);
                    }
                }
                logger.debug(
                        "Labels {} created for {} and {}. Creating label image servers and aligning with ECC criterion",
                        label,
                        imageDataToAlign,
                        baseImageData
                );

                yield alignWithEccCriterion(
                        new LabeledImageServer.Builder(baseImageData)
                                .backgroundLabel(0)
                                .addLabels(labels)
                                .downsample(downsample)
                                .build(),
                        new LabeledImageServer.Builder(imageDataToAlign)
                                .backgroundLabel(0)
                                .addLabels(labels)
                                .downsample(downsample)
                                .build(),
                        transformationTypes,
                        initialTransform,
                        1
                );
            }
            case POINT_ANNOTATIONS -> {
                logger.debug("Image alignment of {} on {} using point annotations", imageDataToAlign, baseImageData);

                yield alignWithPoints(baseImageData, imageDataToAlign, transformationTypes);
            }
        };
    }

    private static AffineTransform alignWithEccCriterion(
            ImageServer<BufferedImage> baseServer,
            ImageServer<BufferedImage> serverToAlign,
            TransformationTypes transformationTypes,
            AffineTransform initialTransform,
            double downsample
    ) throws Exception {
        BufferedImage baseImage = ensureGrayScale(baseServer.readRegion(RegionRequest.createInstance(
                baseServer.getPath(),
                downsample,
                0,
                0,
                baseServer.getWidth(),
                baseServer.getHeight()
        )));
        BufferedImage imageToAlign = ensureGrayScale(serverToAlign.readRegion(RegionRequest.createInstance(
                serverToAlign.getPath(),
                downsample,
                0,
                0,
                serverToAlign.getWidth(),
                serverToAlign.getHeight()
        )));

        try (
                Mat baseMat = OpenCVTools.imageToMat(baseImage);
                Mat matToAlign = OpenCVTools.imageToMat(imageToAlign);
                MatExpr matExprTransform = Mat.eye(2, 3, opencv_core.CV_32F);
                Mat matTransform = matExprTransform.asMat();
                Indexer indexer = matTransform.createIndexer();
                TermCriteria termCriteria = new TermCriteria(TermCriteria.COUNT, ECC_MAX_COUNT, ECC_EPSILON)
        ) {
            transformToMat(initialTransform, indexer, downsample);

            logger.debug(
                    "Finding ECC transform from {} to {} with downsample {} and transformation types {}",
                    baseServer,
                    serverToAlign,
                    downsample,
                    transformationTypes
            );
            double result = opencv_video.findTransformECC(
                    baseMat,
                    matToAlign,
                    matTransform,
                    switch (transformationTypes) {
                        case AFFINE -> opencv_video.MOTION_AFFINE;
                        case RIGID -> opencv_video.MOTION_EUCLIDEAN;
                    },
                    termCriteria,
                    null
            );
            logger.debug("Transformation result of aligning {} to {}: {}", serverToAlign, baseServer, result);

            return matToTransform(indexer, downsample);
        }
    }

    private static AffineTransform alignWithPoints(
            ImageData<BufferedImage> baseImageData,
            ImageData<BufferedImage> imageDataToAlign,
            TransformationTypes transformationTypes
    ) {
        List<Point2> basePoints = getPointsOfNonAreaRois(baseImageData.getHierarchy().getAnnotationObjects());
        List<Point2> pointsToAlign = getPointsOfNonAreaRois(imageDataToAlign.getHierarchy().getAnnotationObjects());
        if (basePoints.isEmpty() && pointsToAlign.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "No points found for either image %s or %s!",
                    baseImageData,
                    imageDataToAlign
            ));
        }
        if (basePoints.size() != pointsToAlign.size()) {
            throw new IllegalArgumentException(String.format(
                    "Images %s and %s have different numbers of annotated points (%d & %d)",
                    baseImageData,
                    imageDataToAlign,
                    basePoints.size(),
                    pointsToAlign.size()
            ));
        }

        logger.debug(
                "Aligning {} to {} with transformation types {}",
                basePoints,
                pointsToAlign,
                transformationTypes
        );
        try (
                Mat baseMat = pointsToMat(basePoints);
                Mat matToAlign = pointsToMat(pointsToAlign);
                Mat matTransform = switch (transformationTypes) {
                    case AFFINE -> opencv_calib3d.estimateAffine2D(baseMat, matToAlign);
                    case RIGID -> opencv_calib3d.estimateAffinePartial2D(baseMat, matToAlign);
                };
                Indexer indexer = matTransform.ptr() == null ? null : matTransform.createIndexer()
        ) {
            if (indexer == null) {
                throw new NullPointerException("Failed to estimate the transformation.");
            }
            return matToTransform(indexer, 1.0);
        }
    }

    private static BufferedImage ensureGrayScale(BufferedImage image) {
        return switch (image.getType()) {
            case BufferedImage.TYPE_BYTE_GRAY -> image;
            case BufferedImage.TYPE_BYTE_INDEXED -> new BufferedImage(
                    new ComponentColorModel(
                            ColorSpace.getInstance(ColorSpace.CS_GRAY),
                            new int[]{8},
                            false,
                            true,
                            Transparency.OPAQUE,
                            DataBuffer.TYPE_BYTE
                    ),
                    image.getRaster(),
                    false,
                    null
            );
            default -> {
                BufferedImage imgGray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                Graphics2D g2d = imgGray.createGraphics();
                g2d.drawImage(image, 0, 0, null);
                g2d.dispose();
                yield imgGray;
            }
        };
    }

    private static void transformToMat(AffineTransform transform, Indexer indexer, double downsample) {
        indexer.putDouble(new long[]{0, 0}, transform.getScaleX());
        indexer.putDouble(new long[]{0, 1}, transform.getShearX());
        indexer.putDouble(new long[]{0, 2}, transform.getTranslateX() / downsample);
        indexer.putDouble(new long[]{1, 0}, transform.getShearY());
        indexer.putDouble(new long[]{1, 1}, transform.getScaleY());
        indexer.putDouble(new long[]{1, 2}, transform.getTranslateY() / downsample);
    }

    private static AffineTransform matToTransform(Indexer indexer, double downsample) {
        return new AffineTransform(
                indexer.getDouble(0, 0),
                indexer.getDouble(1, 0),
                indexer.getDouble(0, 1),
                indexer.getDouble(1, 1),
                indexer.getDouble(0, 2) * downsample,
                indexer.getDouble(1, 2) * downsample
        );
    }

    private static List<Point2> getPointsOfNonAreaRois(Collection<PathObject> pathObjects) {
        return pathObjects.stream()
                .map(PathObject::getROI)
                .filter(roi -> roi != null && !roi.isArea())
                .map(ROI::getAllPoints)
                .flatMap(List::stream)
                .toList();
    }

    private static Mat pointsToMat(List<Point2> points) {
        Mat mat = new Mat(points.size(), 2, opencv_core.CV_32FC1);
        try (Indexer indexer = mat.createIndexer()) {
            for (int i=0; i<points.size(); i++) {
                indexer.putDouble(new long[]{i, 0}, points.get(i).getX());
                indexer.putDouble(new long[]{i, 1}, points.get(i).getY());
            }
        }
        return mat;
    }
}
