package qupath.ext.align.core;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.display.ImageDisplay;
import qupath.lib.geom.Point2;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * A class that represents an affine transformation happening on an image and on a specific QuPath viewer.
 * <p>
 * This class is not thread-safe.
 */
public class ImageTransform {

    private static final Logger logger = LoggerFactory.getLogger(ImageTransform.class);
    private final ObjectProperty<AffineTransform> transform = new SimpleObjectProperty<>(new AffineTransform());
    private final ImageData<BufferedImage> imageData;
    private final QuPathViewer viewer;
    private AffineTransform inverseTransform = new AffineTransform();
    private ImageDisplay imageDisplay;

    /**
     * Create the image transform.
     * <p>
     * The transform will be initialized as the identity matrix. If the provided image data
     * and the provided viewer have a defined pixel size in microns, the transform will be scaled to
     * the pixel size of the provided viewer divided by the pixel size of the provided image.
     * <p>
     * It is expected that the image server associated with the provided image data (see {@link ImageData#getServer()}
     * is already created.
     *
     * @param imageData the image data the transform should be applied to
     * @param viewer the viewer the image should be transformed to
     * @throws NullPointerException if one of the provided parameters is null
     */
    public ImageTransform(ImageData<BufferedImage> imageData, QuPathViewer viewer) {
        this.imageData = Objects.requireNonNull(imageData);
        this.viewer = Objects.requireNonNull(viewer);

        resetTransform();
    }

    @Override
    public String toString() {
        return String.format(
                "Image transform of %s and %s with transform %s and inverse transform %s",
                imageData,
                viewer,
                transform.get(),
                inverseTransform
        );
    }

    /**
     * Get an observable value representing the transform of this object. The observable value will be updated
     * each time the transform is modified. Note that you shouldn't modify the returned transform; use functions of
     * this class (e.g. {@link #setTransform(double, double, double, double, double, double)} or {@link #invertTransform()})
     *
     * @return an observable value representing the transform of this object
     */
    public ObservableValue<AffineTransform> getTransform() {
        return transform;
    }

    /**
     * Call {@link AutoAligner#getAlignTransformation(ImageData, ImageData, AffineTransform, AutoAligner.AlignmentType, AutoAligner.TransformationTypes, double)}
     * with the current transform and update the transform with the result.
     */
    public void alignTransform(
            ImageData<BufferedImage> baseImageData,
            ImageData<BufferedImage> imageDataToAlign,
            AutoAligner.AlignmentType alignmentType,
            AutoAligner.TransformationTypes transformationTypes,
            double downsample
    ) throws Exception {
        updateTransform(AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                this.transform.get(),
                alignmentType,
                transformationTypes,
                downsample
        ));
    }

    /**
     * Update the transform with the provided matrix value.
     *
     * @param m00 the X coordinate scaling element of the 3x3 matrix
     * @param m10 the Y coordinate shearing element of the 3x3 matrix
     * @param m01 the X coordinate shearing element of the 3x3 matrix
     * @param m11 the Y coordinate scaling element of the 3x3 matrix
     * @param m02 the X coordinate translation element of the 3x3 matrix
     * @param m12 the Y coordinate translation element of the 3x3 matrix
     */
    public void setTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        AffineTransform transform = new AffineTransform(this.transform.get());
        transform.setTransform(m00, m10, m01, m11, m02, m12);
        updateTransform(transform);
    }

    /**
     * Concatenates the transform with a translation.
     *
     * @param x the distance by which coordinates are translated in the X axis direction
     * @param y the distance by which coordinates are translated in the Y axis direction
     */
    public void translateTransform(double x, double y) {
        AffineTransform transform = new AffineTransform(this.transform.get());
        transform.translate(x, y);
        updateTransform(transform);
    }

    /**
     * Concatenates the transform with a transform that rotates coordinates around an anchor point.
     *
     * @param theta the angle of rotation (in radians)
     * @param anchorX the X coordinate of the rotation anchor point
     * @param anchorY the Y coordinate of the rotation anchor point
     */
    public void rotateTransform(double theta, double anchorX, double anchorY) {
        AffineTransform transform = new AffineTransform(this.transform.get());
        transform.rotate(theta, anchorX, anchorY);
        updateTransform(transform);
    }

    /**
     * Attempt to set the transform to the inverse of itself.
     *
     * @throws NoninvertibleTransformException if the matrix cannot be inverted
     */
    public void invertTransform() throws NoninvertibleTransformException {
        AffineTransform transform = new AffineTransform(this.transform.get());
        transform.invert();
        updateTransform(transform);
    }

    /**
     * Reset the transform with the value described in {@link #ImageTransform(ImageData, QuPathViewer)}.
     */
    public void resetTransform() {
        AffineTransform transform = new AffineTransform();

        if (viewer.getImageData() != null) {
            PixelCalibration viewerImageCalibration = viewer.getImageData().getServer().getPixelCalibration();
            PixelCalibration overlayImageCalibration = imageData.getServer().getPixelCalibration();

            if (viewerImageCalibration.hasPixelSizeMicrons() && overlayImageCalibration.hasPixelSizeMicrons()) {
                transform.scale(
                        viewerImageCalibration.getPixelWidthMicrons() / overlayImageCalibration.getPixelWidthMicrons(),
                        viewerImageCalibration.getPixelHeightMicrons() / overlayImageCalibration.getPixelHeightMicrons()
                );
            }
        }

        updateTransform(transform);
    }

    /**
     * Get the inverse of the transform. Note that if the transform is currently not invertible, the returned value of this
     * function might not be the inverse of the transform, but rather the inverse of the last value of the transform
     * when it was still invertible.
     *
     * @return the inverse of the transform
     */
    public AffineTransform getInverseTransform() {
        return inverseTransform;
    }

    /**
     * Transform the provided ROI with the current transform.
     *
     * @param roi the ROI to transform
     * @return a new ROI that represents the provided ROI transformed with the current transform
     * @throws NullPointerException if the provided parameter is null
     */
    public ROI transformROI(ROI roi) {
        logger.debug("Transforming {} with {}", roi, this);

        if (roi.isPoint()) {
            int nPoints = roi.getAllPoints().size();
            Point2D[] transformedPoints = new Point2D[nPoints];

            transform.get().transform(
                    roi.getAllPoints().stream()
                            .map(point -> new Point2D.Double(point.getX(), point.getY()))
                            .toArray(Point2D[]::new),
                    0,
                    transformedPoints,
                    0,
                    nPoints
            );
            logger.debug("{} is a ROI of points. Its points were transformed from {} to {}", roi, roi.getAllPoints(), Arrays.toString(transformedPoints));

            return ROIs.createPointsROI(
                    Arrays.stream(transformedPoints)
                            .map(point -> new Point2(point.getX(), point.getY()))
                            .toList(),
                    roi.getImagePlane()
            );
        } else {
            Shape transformedShape = transform.get().createTransformedShape(roi.getShape());
            logger.debug("{} is not a ROI of points. Its shape was transformed from {} to {}", roi, roi.getShape(), transformedShape);

            return RoiTools.getShapeROI(transformedShape, roi.getImagePlane(), 0.5);
        }
    }

    /**
     * @return the image data to which the transform should be applied
     */
    public ImageData<BufferedImage> getImageData() {
        return imageData;
    }

    /**
     * Get an image display that can be used when painting the image returned by {@link #getImageData()}.
     * The first call to this function may take some time as it will generate the image display. Further calls
     * will use the previously generated image display so will be faster.
     *
     * @return an image display that can be used when painting the image
     * @throws IOException if an error occurs while creating the image display
     */
    public ImageDisplay getImageDisplay() throws IOException {
        if (imageDisplay == null) {
            imageDisplay = ImageDisplay.create(imageData);
        }
        return imageDisplay;
    }

    private void updateTransform(AffineTransform transform) {
        this.transform.set(transform);      // this is used to trigger any listener to this.transform

        try {
            inverseTransform = transform.createInverse();
            logger.trace("Transform updated to {} and inverse transform updated to {}. Repainting {}", transform, inverseTransform, viewer);
            viewer.repaint();
        } catch (NoninvertibleTransformException e) {
            logger.warn(
                    "Cannot create inverse transform of {}. Inverse transform not updated and still set to {}. {} not repainted",
                    transform,
                    inverseTransform,
                    viewer,
                    e
            );
        }
    }
}
