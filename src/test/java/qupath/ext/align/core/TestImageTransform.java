package qupath.ext.align.core;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import qupath.fx.utils.FXUtils;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestImageTransform {

    private static ImageServer<BufferedImage> sampleImageServer;
    private static ImageData<BufferedImage> imageData;
    private static QuPathViewer viewer;

    @BeforeAll
    static void initJavaFxRuntime() {
        Assumptions.assumeTrue(
                () -> {
                    try {
                        Platform.startup(() -> {});     // this is needed to create QuPath viewers
                        return true;
                    } catch (Throwable e) {
                        return false;
                    }
                },
                "JavaFX initialization failed. Skipping image transform tests"
        );

        sampleImageServer = new SampleImageServer(5.78, 8);
        imageData = new ImageData<>(sampleImageServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        viewer = new QuPathViewer(ImageRegionStoreFactory.createImageRegionStore(), OverlayOptions.getSharedInstance());
    }

    @AfterAll
    static void closeFields() throws Exception {
        if (imageData != null) {
            imageData.close();
        }
        if (sampleImageServer != null) {
            sampleImageServer.close();
        }
    }

    @Test
    void Check_Image_Transform_Creation_With_Null_Image_Data() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new ImageTransform(null, viewer)
        );
    }

    @Test
    void Check_Image_Transform_Creation_With_Null_Viewer() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new ImageTransform(imageData, null)
        );
    }

    @Test
    void Check_Initial_Transform() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        AffineTransform expectedTransform = new AffineTransform();

        AffineTransform transform = imageTransform.getTransform().getValue();

        Assertions.assertEquals(expectedTransform, transform);
    }

    @Test
    void Check_Initial_Transform_When_Image_And_Viewer_Have_Pixel_Size() throws Exception {
        ImageServer<BufferedImage> viewerImageServer = new SampleImageServer(1.23, 2.34);
        ImageData<BufferedImage> viewerImageData = new ImageData<>(viewerImageServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        FXUtils.runOnApplicationThread(QuPathGUI::createHiddenInstance);        // this is needed for viewer.setImageData to work
        viewer.setImageData(viewerImageData);
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        AffineTransform expectedTransform = new AffineTransform(1.23 / 5.78, 0, 0, 2.34 / 8, 0, 0);

        AffineTransform transform = imageTransform.getTransform().getValue();

        Assertions.assertEquals(expectedTransform, transform);

        viewer.resetImageData();
        viewerImageData.close();
        viewerImageServer.close();
    }

    @Test
    void Check_Transform_Property_Updated_After_Modified() throws InterruptedException {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        CountDownLatch latch = new CountDownLatch(1);

        imageTransform.getTransform().addListener((p, o, n) -> latch.countDown());
        imageTransform.translateTransform(1, 2);
        boolean transformUpdated = latch.await(5, TimeUnit.SECONDS);

        Assertions.assertTrue(transformUpdated);
    }

    @Test
    void Check_Transform_After_Set() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        double m00 = 1.23;
        double m10 = 5;
        double m01 = 234.432;
        double m11 = -234.3;
        double m02 = 0;
        double m12 = -9;
        AffineTransform expectedTransform = new AffineTransform(m00, m10, m01, m11, m02, m12);

        imageTransform.setTransform(m00, m10, m01, m11, m02, m12);

        Assertions.assertEquals(expectedTransform, imageTransform.getTransform().getValue());
    }

    @Test
    void Check_Transform_After_Translation() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        double x = 1.23;
        double y = 5;
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, x, y);

        imageTransform.translateTransform(x, y);

        Assertions.assertEquals(expectedTransform, imageTransform.getTransform().getValue());
    }

    @Test
    void Check_Transform_After_Rotation() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        double theta = Math.toRadians(109);
        AffineTransform expectedTransform = new AffineTransform(
                Math.cos(theta),
                Math.sin(theta),
                -Math.sin(theta),
                Math.cos(theta),
                0,
                0
        );

        imageTransform.rotateTransform(theta, 0, 0);

        Assertions.assertEquals(expectedTransform, imageTransform.getTransform().getValue());
    }

    @Test
    void Check_Transform_After_Inverted_When_Non_Invertible() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.setTransform(1, 0, 0, 0, 0, 0);

        Assertions.assertThrows(
                NoninvertibleTransformException.class,
                imageTransform::invertTransform
        );
    }

    @Test
    void Check_Transform_After_Inverted_When_Invertible() throws NoninvertibleTransformException {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.setTransform(1, 0, 1, 1, 0, 0);
        AffineTransform expectedTransform = new AffineTransform(1, 0, -1, 1, 0, 0);

        imageTransform.invertTransform();

        Assertions.assertEquals(expectedTransform, imageTransform.getTransform().getValue());
    }

    @Test
    void Check_Transform_After_Reset() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.setTransform(1, 1, 1, 1, 1, 1);
        AffineTransform expectedTransform = new AffineTransform();

        imageTransform.resetTransform();

        Assertions.assertEquals(expectedTransform, imageTransform.getTransform().getValue());
    }

    @Test
    void Check_Transform_After_Reset_When_Image_And_Viewer_Have_Pixel_Size() throws Exception {
        ImageServer<BufferedImage> viewerImageServer = new SampleImageServer(1.23, 2.34);
        ImageData<BufferedImage> viewerImageData = new ImageData<>(viewerImageServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        FXUtils.runOnApplicationThread(QuPathGUI::createHiddenInstance);        // this is needed for viewer.setImageData to work
        viewer.setImageData(viewerImageData);
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.setTransform(1, 1, 1, 1, 1, 1);
        AffineTransform expectedTransform = new AffineTransform(1.23 / 5.78, 0, 0, 2.34 / 8, 0, 0);

        imageTransform.resetTransform();

        Assertions.assertEquals(expectedTransform, imageTransform.getTransform().getValue());

        viewer.resetImageData();
        viewerImageData.close();
        viewerImageServer.close();
    }

    @Test
    void Check_Inverse_Transform() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.setTransform(1, 0, 1, 1, 0, 0);
        AffineTransform expectedInverseTransform = new AffineTransform(1, 0, -1, 1, 0, 0);

        AffineTransform inverseTransform = imageTransform.getInverseTransform();

        Assertions.assertEquals(expectedInverseTransform, inverseTransform);
    }

    @Test
    void Check_Inverse_Transform_When_Transform_Not_Invertible() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.setTransform(1, 0, 1, 1, 0, 0);
        imageTransform.setTransform(1, 0, 0, 0, 0, 0);
        AffineTransform expectedInverseTransform = new AffineTransform(1, 0, -1, 1, 0, 0);

        AffineTransform inverseTransform = imageTransform.getInverseTransform();

        Assertions.assertEquals(expectedInverseTransform, inverseTransform);
    }

    @Test
    void Check_Transformed_Points_With_Identity() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        ROI roi = ROIs.createPointsROI(List.of(new Point2(2, 4.45), new Point2(-4.54, 0)));

        ROI transformedRoi = imageTransform.transformROI(roi);

        Assertions.assertEquals(roi, transformedRoi);
    }

    @Test
    void Check_Transformed_Points_With_Translation() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.translateTransform(4.54, -6);
        ROI roi = ROIs.createPointsROI(List.of(new Point2(2, 7.45), new Point2(-14.54, 0)));
        ROI expectedTransformedRoi = ROIs.createPointsROI(List.of(
                new Point2(2 + 4.54, 7.45 - 6),
                new Point2(-14.54 + 4.54, -6)
        ));

        ROI transformedRoi = imageTransform.transformROI(roi);

        Assertions.assertEquals(expectedTransformedRoi, transformedRoi);
    }

    @Test
    void Check_Transformed_Line_With_Identity() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        ROI roi = ROIs.createLineROI(4.3, -23, 5, 50);

        ROI transformedRoi = imageTransform.transformROI(roi);

        Assertions.assertEquals(roi, transformedRoi);
    }

    @Test
    void Check_Transformed_Line_With_Translation() {
        ImageTransform imageTransform = new ImageTransform(imageData, viewer);
        imageTransform.translateTransform(4.54, -6);
        ROI roi = ROIs.createLineROI(4.3, -23, 5, 50);
        ROI expectedTransformedRoi = ROIs.createLineROI(4.3 + 4.54, -23 - 6, 5 + 4.54, 50 - 6);

        ROI transformedRoi = imageTransform.transformROI(roi);

        Assertions.assertEquals(expectedTransformedRoi, transformedRoi);
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private final ImageServerMetadata metadata;

        private SampleImageServer(Number pixelWidthMicrons, Number pixelHeightMicrons) {
            super(BufferedImage.class);

            metadata = new ImageServerMetadata.Builder()
                    .pixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons)
                    .width(10)
                    .height(10)
                    .rgb(true)
                    .build();
        }

        @Override
        protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
            return null;
        }

        @Override
        protected String createID() {
            return "";
        }

        @Override
        public Collection<URI> getURIs() {
            return List.of();
        }

        @Override
        public BufferedImage readRegion(RegionRequest request) {
            return new BufferedImage(request.getWidth(), request.getHeight(), BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public String getServerType() {
            return "";
        }

        @Override
        public ImageServerMetadata getOriginalMetadata() {
            return metadata;
        }
    }
}
