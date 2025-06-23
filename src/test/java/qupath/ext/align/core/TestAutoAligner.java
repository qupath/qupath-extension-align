package qupath.ext.align.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.ColorTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Collection;
import java.util.List;

public class TestAutoAligner {

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Intensity_Auto_Alignment_On_Same_Image(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 0;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 1;
        int[][] basePixels = createPixels(width, height);
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.INTENSITY,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0.001);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Intensity_Auto_Alignment_On_Translated_Image(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 3;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 1;
        int[][] basePixels = createPixels(width, height);
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.INTENSITY,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, .2);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Intensity_Auto_Alignment_On_Translated_Image_With_Adequate_Initial_Transform(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 20;
        AffineTransform initialTransform = new AffineTransform(1, 0, 0, 1, shift-1, shift-1);
        double downsample = 1;
        int[][] basePixels = createPixels(width, height);
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.INTENSITY,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, .2);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Intensity_Auto_Alignment_On_Translated_Image_With_Downsample(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 2000;
        int height = 2000;
        int shift = 1;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 2;
        int[][] basePixels = createPixels(width, height);
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, new PathObjectHierarchy(), ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.INTENSITY,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, .5);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Area_Annotations_Auto_Alignment_With_Same_Annotations(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 0;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 1;
        String className = "some class";
        int[][] basePixels = new int[height][width];
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        PathObjectHierarchy baseHierarchy = new PathObjectHierarchy();
        baseHierarchy.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4., height / 4., width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        PathObjectHierarchy hierarchyToAlign = new PathObjectHierarchy();
        hierarchyToAlign.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4. + shift, height / 4. + shift, width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, baseHierarchy, ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, hierarchyToAlign, ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.AREA_ANNOTATIONS,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0.001);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Area_Annotations_Auto_Alignment_With_Translated_Annotation(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 3;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 1;
        String className = "some class";
        int[][] basePixels = new int[height][width];
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        PathObjectHierarchy baseHierarchy = new PathObjectHierarchy();
        baseHierarchy.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4., height / 4., width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        PathObjectHierarchy hierarchyToAlign = new PathObjectHierarchy();
        hierarchyToAlign.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4. + shift, height / 4. + shift, width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, baseHierarchy, ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, hierarchyToAlign, ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.AREA_ANNOTATIONS,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0.1);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Area_Annotations_Auto_Alignment_With_Translated_Annotation_And_Adequate_Initial_Transform(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 20;
        AffineTransform initialTransform = new AffineTransform(1, 0, 0, 1, shift-1, shift-1);
        double downsample = 1;
        String className = "some class";
        int[][] basePixels = new int[height][width];
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        PathObjectHierarchy baseHierarchy = new PathObjectHierarchy();
        baseHierarchy.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4., height / 4., width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        PathObjectHierarchy hierarchyToAlign = new PathObjectHierarchy();
        hierarchyToAlign.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4. + shift, height / 4. + shift, width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, baseHierarchy, ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, hierarchyToAlign, ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.AREA_ANNOTATIONS,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0.1);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Area_Annotations_Auto_Alignment_With_Translated_Annotation_And_Downsample(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 2000;
        int height = 2000;
        int shift = 10;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 2;
        String className = "some class";
        int[][] basePixels = new int[height][width];
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        PathObjectHierarchy baseHierarchy = new PathObjectHierarchy();
        baseHierarchy.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4., height / 4., width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        PathObjectHierarchy hierarchyToAlign = new PathObjectHierarchy();
        hierarchyToAlign.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createRectangleROI(width / 4. + shift, height / 4. + shift, width / 2., height / 2.),
                PathClass.getInstance(className)
        ));
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, baseHierarchy, ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, hierarchyToAlign, ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.AREA_ANNOTATIONS,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0.1);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Point_Annotations_Auto_Alignment_With_Same_Points(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 0;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 1;
        int[][] basePixels = new int[height][width];
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        PathObjectHierarchy baseHierarchy = new PathObjectHierarchy();
        baseHierarchy.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createPointsROI(List.of(
                        new Point2(3.5, 6.78),
                        new Point2(10, 0.1),
                        new Point2(46, 8.4),
                        new Point2(78, 80)
                ))
        ));
        PathObjectHierarchy hierarchyToAlign = new PathObjectHierarchy();
        hierarchyToAlign.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createPointsROI(List.of(
                        new Point2(3.5 + shift, 6.78 + shift),
                        new Point2(10 + shift, 0.1 + shift),
                        new Point2(46 + shift, 8.4 + shift),
                        new Point2(78 + shift, 80 + shift)
                ))
        ));
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, baseHierarchy, ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, hierarchyToAlign, ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.POINT_ANNOTATIONS,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    @ParameterizedTest
    @EnumSource(AutoAligner.TransformationTypes.class)
    void Check_Point_Annotations_Auto_Alignment_With_Translated_Points(AutoAligner.TransformationTypes transformationTypes) throws Exception {
        int width = 500;
        int height = 500;
        int shift = 20;
        AffineTransform initialTransform = new AffineTransform();
        double downsample = 1;
        int[][] basePixels = new int[height][width];
        int[][] pixelsToAlign = copyArray(basePixels, shift);
        ImageServer<BufferedImage> baseServer = new SampleImageServer(basePixels);
        ImageServer<BufferedImage> serverToAlign = new SampleImageServer(pixelsToAlign);
        PathObjectHierarchy baseHierarchy = new PathObjectHierarchy();
        baseHierarchy.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createPointsROI(List.of(
                        new Point2(3.5, 6.78),
                        new Point2(10, 0.1),
                        new Point2(46, 8.4),
                        new Point2(78, 80)
                ))
        ));
        PathObjectHierarchy hierarchyToAlign = new PathObjectHierarchy();
        hierarchyToAlign.getRootObject().addChildObject(PathObjects.createAnnotationObject(
                ROIs.createPointsROI(List.of(
                        new Point2(3.5 + shift, 6.78 + shift),
                        new Point2(10 + shift, 0.1 + shift),
                        new Point2(46 + shift, 8.4 + shift),
                        new Point2(78 + shift, 80 + shift)
                ))
        ));
        ImageData<BufferedImage> baseImageData = new ImageData<>(baseServer, baseHierarchy, ImageData.ImageType.UNSET);
        ImageData<BufferedImage> imageDataToAlign = new ImageData<>(serverToAlign, hierarchyToAlign, ImageData.ImageType.UNSET);
        AffineTransform expectedTransform = new AffineTransform(1, 0, 0, 1, shift, shift);

        AffineTransform transform = AutoAligner.getAlignTransformation(
                baseImageData,
                imageDataToAlign,
                initialTransform,
                AutoAligner.AlignmentType.POINT_ANNOTATIONS,
                transformationTypes,
                downsample
        );

        assertAffineAlmostEquals(expectedTransform, transform, 0.001);

        baseImageData.close();
        baseServer.close();
        imageDataToAlign.close();
        serverToAlign.close();
    }

    private static int[][] createPixels(int width, int height) {
        int[][] pixels = new int[height][width];

        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                pixels[y][x] = 20 + 10 * x + 5 * y + (x * y) % 15;
            }
        }

        return pixels;
    }

    private static int[][] copyArray(int[][] arrayToCopy, int shift) {
        int[][] res = new int[arrayToCopy.length][arrayToCopy[0].length];

        for (int i=shift; i<arrayToCopy.length; i++) {
            System.arraycopy(arrayToCopy[i - shift], 0, res[i], shift, arrayToCopy[0].length - shift);
        }

        return res;
    }

    private static class SampleImageServer extends AbstractImageServer<BufferedImage> {

        private final int[][] pixels;
        private final ImageServerMetadata metadata;

        private SampleImageServer(int[][] pixels) {
            super(BufferedImage.class);

            this.pixels = pixels;
            this.metadata = new ImageServerMetadata.Builder()
                    .width(pixels[0].length)
                    .height(pixels.length)
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
            BufferedImage image = new BufferedImage(request.getWidth(), request.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int x=0; x<request.getWidth(); x++) {
                for (int y=0; y<request.getHeight(); y++) {
                    int value = pixels[y + request.getY()][x + request.getX()];
                    image.setRGB(x, y, ColorTools.packRGB(value, value, value));
                }
            }
            return BufferedImageTools.resize(
                    image,
                    (int) (image.getWidth() / request.getDownsample()),
                    (int) (image.getHeight() / request.getDownsample()),
                    true
            );
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

    private static void assertAffineAlmostEquals(AffineTransform expectedTransform, AffineTransform transform, double delta) {
        Assertions.assertArrayEquals(
                new double[] {
                        expectedTransform.getTranslateX(),
                        expectedTransform.getTranslateY(),
                        expectedTransform.getScaleX(),
                        expectedTransform.getScaleY(),
                        expectedTransform.getShearX(),
                        expectedTransform.getShearY()
                },
                new double[] {
                        transform.getTranslateX(),
                        transform.getTranslateY(),
                        transform.getScaleX(),
                        transform.getScaleY(),
                        transform.getShearX(),
                        transform.getShearY()
                },
                delta
        );
    }
}
