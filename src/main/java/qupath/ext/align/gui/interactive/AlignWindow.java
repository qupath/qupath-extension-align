package qupath.ext.align.gui.interactive;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.controlsfx.control.CheckListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.align.gui.Utils;
import qupath.ext.align.core.AutoAligner;
import qupath.ext.align.core.ImageTransform;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.roi.GeometryTools;

import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A window allowing to align images on top of others by using an {@link AlignOverlay}.
 */
public class AlignWindow extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(AlignWindow.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final double DEFAULT_OPACITY = 1;
    private static final int DEFAULT_ROTATION_INCREMENT = 1;
    private static final double DEFAULT_PIXEL_SIZE_MICRONS = 20;
    private final Map<ImageDataViewer, ImageTransform> imageDataAndViewerToTransform;
    private final ObjectProperty<ImageTransform> selectedImageTransform;
    private final QuPathGUI quPath;
    private AlignOverlay currentOverlay;
    private record ImageDataViewer(ImageData<BufferedImage> imageData, QuPathViewer viewer) {}
    @FXML
    private CheckListView<ImageData<BufferedImage>> images;
    @FXML
    private Button chooseImages;
    @FXML
    private Slider opacity;
    @FXML
    private Label opacityLabel;
    @FXML
    private TextField rotationIncrement;
    @FXML
    private Button rotateLeft;
    @FXML
    private Button rotateRight;
    @FXML
    private ComboBox<AutoAligner.TransformationTypes> transformationTypes;
    @FXML
    private Tooltip transformationTypesDescription;
    @FXML
    private ComboBox<AutoAligner.AlignmentType> alignmentType;
    @FXML
    private Tooltip alignmentTypeDescription;
    @FXML
    private TextField pixelSize;
    @FXML
    private Button estimateTransform;
    @FXML
    private TextArea affineTransformation;
    @FXML
    private Button update;
    @FXML
    private Button invert;
    @FXML
    private Button reset;
    @FXML
    private Button copy;
    @FXML
    private Button propagate;

    /**
     * Create the window. This will not show it.
     *
     * @param quPath the QuPath GUI that should own this window and contain the images, projects, and viewers to use
     * @throws IOException if an error occurs while loading the window FXML file
     * @throws NullPointerException if the provided parameter is null
     */
    public AlignWindow(QuPathGUI quPath) throws IOException {
        logger.debug("Creating image alignment window for {}", quPath);
        this.quPath = Objects.requireNonNull(quPath);

        Utils.loadFXML(this, AlignWindow.class.getResource("image_alignment_window.fxml"));

        imageDataAndViewerToTransform = Utils.createKeyMappedObservableMap(
                createImageDataViewerList(),
                imageDataViewer -> {
                    try {
                        return new ImageTransform(imageDataViewer.imageData(), imageDataViewer.viewer());
                    } catch (Exception e) {
                        logger.error("Error while getting image server of {}. Cannot create image transform", imageDataViewer.imageData(), e);
                        return null;
                    }
                }
        );

        selectedImageTransform = new SimpleObjectProperty<>();
        selectedImageTransform.bind(Bindings.createObjectBinding(
                () -> imageDataAndViewerToTransform.get(new ImageDataViewer(
                        images.getSelectionModel().selectedItemProperty().get(),
                        quPath.viewerProperty().get()
                )),
                images.getSelectionModel().selectedItemProperty(),
                quPath.viewerProperty()
        ));

        BooleanProperty inactiveOverlayImageOrViewerImage = new SimpleBooleanProperty(true);
        selectedImageTransform.addListener((p, o, n) ->
                inactiveOverlayImageOrViewerImage.set(n == null || quPath.viewerProperty().flatMap(QuPathViewer::imageDataProperty).getValue() == null)
        );
        quPath.viewerProperty().flatMap(QuPathViewer::imageDataProperty).addListener((p, o, n) ->
                inactiveOverlayImageOrViewerImage.set(selectedImageTransform.get() == null || n == null)
        );

        images.setCellFactory(c -> new ImageEntryCell(quPath.projectProperty(), quPath.viewerProperty()));
        chooseImages.disableProperty().bind(quPath.projectProperty().isNull());
        opacityLabel.disableProperty().bind(inactiveOverlayImageOrViewerImage);
        opacity.setValue(DEFAULT_OPACITY);
        opacity.disableProperty().bind(inactiveOverlayImageOrViewerImage);

        rotationIncrement.setText(String.valueOf(DEFAULT_ROTATION_INCREMENT));
        rotationIncrement.setTextFormatter(Utils.createFloatFormatter());

        rotateLeft.disableProperty().bind(inactiveOverlayImageOrViewerImage);
        rotateRight.disableProperty().bind(inactiveOverlayImageOrViewerImage);

        transformationTypes.getItems().setAll(AutoAligner.TransformationTypes.values());
        transformationTypes.setConverter(new StringConverter<>() {
            @Override
            public String toString(AutoAligner.TransformationTypes object) {
                return switch (object) {
                    case AFFINE -> resources.getString("ImageOverlayAlignmentWindow.affineTransform");
                    case RIGID -> resources.getString("ImageOverlayAlignmentWindow.rigidTransform");
                };
            }

            @Override
            public AutoAligner.TransformationTypes fromString(String string) {
                return null;
            }
        });
        transformationTypes.getSelectionModel().select(AutoAligner.TransformationTypes.AFFINE);
        transformationTypesDescription.textProperty().bind(Bindings.createStringBinding(
                () -> resources.getString(switch (transformationTypes.getSelectionModel().selectedItemProperty().get()) {
                    case AFFINE -> "ImageOverlayAlignmentWindow.affineDescription";
                    case RIGID -> switch (alignmentType.getSelectionModel().selectedItemProperty().get()) {
                        case INTENSITY, AREA_ANNOTATIONS -> "ImageOverlayAlignmentWindow.rigidDescription";
                        case POINT_ANNOTATIONS -> "ImageOverlayAlignmentWindow.rigidDescriptionWithScaling";
                    };
                }),
                transformationTypes.getSelectionModel().selectedItemProperty(),
                alignmentType.getSelectionModel().selectedItemProperty()
        ));
        alignmentType.getItems().setAll(AutoAligner.AlignmentType.values());
        alignmentType.setConverter(new StringConverter<>() {
            @Override
            public String toString(AutoAligner.AlignmentType object) {
                return switch (object) {
                    case INTENSITY -> resources.getString("ImageOverlayAlignmentWindow.imageIntensity");
                    case AREA_ANNOTATIONS -> resources.getString("ImageOverlayAlignmentWindow.areaAnnotations");
                    case POINT_ANNOTATIONS -> resources.getString("ImageOverlayAlignmentWindow.pointAnnotations");
                };
            }

            @Override
            public AutoAligner.AlignmentType fromString(String string) {
                return null;
            }
        });
        alignmentType.getSelectionModel().select(AutoAligner.AlignmentType.INTENSITY);
        alignmentTypeDescription.textProperty().bind(Bindings.createStringBinding(
                () -> resources.getString(switch (alignmentType.getSelectionModel().selectedItemProperty().get()) {
                    case INTENSITY -> "ImageOverlayAlignmentWindow.autoAlignmentByLookingAtPixelValues";
                    case AREA_ANNOTATIONS -> "ImageOverlayAlignmentWindow.autoAlignmentByLookingAtAreaAnnotations";
                    case POINT_ANNOTATIONS -> "ImageOverlayAlignmentWindow.autoAlignmentByLookingAtPointAnnotations";
                }),
                alignmentType.getSelectionModel().selectedItemProperty()
        ));
        pixelSize.setText(String.valueOf(DEFAULT_PIXEL_SIZE_MICRONS));
        pixelSize.setTextFormatter(Utils.createFloatFormatter());
        estimateTransform.disableProperty().bind(inactiveOverlayImageOrViewerImage);

        affineTransformation.editableProperty().bind(inactiveOverlayImageOrViewerImage.not());
        affineTransformation.setText(resources.getString("ImageOverlayAlignmentWindow.noOverlaySelected"));
        selectedImageTransform.flatMap(ImageTransform::getTransform).addListener((p, o, n) -> {
            logger.trace("Transform of selected image transform updated to {}. Updating affine transformation text area", n);

            if (n == null) {
                affineTransformation.setText(resources.getString("ImageOverlayAlignmentWindow.noOverlaySelected"));
            } else {
                affineTransformation.setText(String.format(
                        "%.4f,\t%.4f,\t%.4f,\n%.4f,\t%.4f,\t%.4f",
                        n.getScaleX(),
                        n.getShearX(),
                        n.getTranslateX(),
                        n.getShearY(),
                        n.getScaleY(),
                        n.getTranslateY()
                ));
            }
        });
        for (Button button: List.of(update, invert, reset, copy)) {
            button.disableProperty().bind(inactiveOverlayImageOrViewerImage);
        }
        propagate.disableProperty().bind(inactiveOverlayImageOrViewerImage.or(quPath.projectProperty().isNull()));

        ImageTransformTranslater imageTransformTranslater = new ImageTransformTranslater(selectedImageTransform, quPath.viewerProperty());
        ChangeListener<? super QuPathViewer> viewerListener = (ChangeListener<QuPathViewer>) (p, o, n) -> {
            if (o != null) {
                o.getView().removeEventFilter(MouseEvent.ANY, imageTransformTranslater);

                if (currentOverlay != null) {
                    logger.debug("{} is not active anymore. Transform mouse handler removed and closing overlay", o);
                    currentOverlay.close();
                    currentOverlay = null;
                } else {
                    logger.debug("{} is not active anymore. Transform mouse handler removed", o);
                }
            }
            if (n != null) {
                logger.debug("Current viewer set to {}. Adding transform mouse handler and creating overlay", n);
                n.getView().addEventFilter(MouseEvent.ANY, imageTransformTranslater);

                currentOverlay = new AlignOverlay(n, selectedImageTransform, opacity.valueProperty());
            }
        };
        showingProperty().addListener((p, o, n) -> {
            if (n) {
                logger.debug("Image alignment window showed. Adding and calling viewer listener");

                quPath.viewerProperty().addListener(viewerListener);
                viewerListener.changed(quPath.viewerProperty(), null, quPath.viewerProperty().get());
            } else {
                logger.debug("Image alignment window hidden. Removing and calling viewer listener");

                quPath.viewerProperty().removeListener(viewerListener);
                viewerListener.changed(quPath.viewerProperty(), quPath.viewerProperty().get(), null);
            }
        });

        initOwner(quPath.getStage());
    }

    @FXML
    private void onChooseImagesClicked(ActionEvent ignored) {
        Project<BufferedImage> project = quPath.projectProperty().get();
        if (project == null) {
            logger.error("No project currently open. Cannot open images selector");
            return;
        }

        ImagesSelector imagesSelector;
        try {
            imagesSelector = new ImagesSelector(
                    project.getImageList(),
                    images.getItems().stream()
                            .map(project::getEntry)
                            .filter(Objects::nonNull)
                            .toList()
            );
        } catch (IOException e) {
            logger.error("Error while creating images selector pane", e);
            return;
        }
        logger.debug("Prompting user to select images from {}", project.getImageList());
        if (!Dialogs.showMessageDialog(resources.getString("ImageOverlayAlignmentWindow.selectImagesToInclude"), imagesSelector)) {
            return;
        }
        logger.debug(
                "User selected {} and unselected {} from {}. Updating list of image entries",
                imagesSelector.getSelectedImages(),
                imagesSelector.getUnselectedImages(),
                project.getImageList()
        );

        images.getItems().removeIf(imageData -> imagesSelector.getUnselectedImages().contains(project.getEntry(imageData)));
        images.getItems().addAll(imagesSelector.getSelectedImages().stream()
                .filter(entry -> images.getItems().stream()
                        .map(project::getEntry)
                        .noneMatch(entry::equals)
                )
                .map(entry -> {
                    logger.debug("Getting image data of {} to add it to the list of image entries", entry);

                    for (QuPathViewer viewer : quPath.getAllViewers()) {
                        ImageData<BufferedImage> imageData = viewer.getImageData();

                        if (imageData != null && entry.equals(project.getEntry(imageData))) {
                            logger.debug("Found viewer {} that contain {}. Using its image data", viewer, entry);
                            return imageData;
                        }
                    }

                    try {
                        if (entry.hasImageData()) {
                            logger.debug(
                                    "{} has already available image data. Using it and removing non annotations objects from its hierarchy to save memory",
                                    entry
                            );
                            ImageData<BufferedImage> imageData = entry.readImageData();

                            Collection<PathObject> pathObjects = imageData.getHierarchy().getObjects(null, null);
                            Set<PathObject> pathObjectsToRemove = pathObjects.stream().filter(p -> !p.isAnnotation()).collect(Collectors.toSet());
                            imageData.getHierarchy().removeObjects(pathObjectsToRemove, true);

                            return imageData;
                        } else {
                            logger.debug("{} has no available image data. Creating one", entry);
                            return entry.readImageData();
                        }
                    } catch (IOException e) {
                        logger.error("Unable to read ImageData for {}. Cannot add it to the image list", entry, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(imageData -> {
                    try {
                        return imageData.getServer() != null;
                    } catch (Exception e) {
                        logger.debug("Cannot create server of {}. Skipping it", imageData, e);
                        return false;
                    }
                })
                .toList()
        );
        logger.debug("List of image entries updated to {}", images.getItems());
    }

    @FXML
    private void onRotateLeftClicked(ActionEvent ignored) {
        rotate(1);
    }

    @FXML
    private void onRotateRightClicked(ActionEvent ignored) {
        rotate(-1);
    }

    @FXML
    private void onEstimateTransformClicked(ActionEvent ignored) {
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot estimate transform");
            return;
        }

        double pixelSizeMicrons;
        try {
            pixelSizeMicrons = Double.parseDouble(pixelSize.getText());
        } catch (NumberFormatException e) {
            logger.error("Cannot parse pixel size {} to a double", pixelSize.getText(), e);

            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.alignmentError"),
                    MessageFormat.format(
                            resources.getString("ImageOverlayAlignmentWindow.pixelSizeCannotBeConvertedToNumber"),
                            pixelSize.getText()
                    )
            );
            return;
        }

        ImageData<BufferedImage> baseImageData = quPath.getViewer().getImageData();
        if (baseImageData == null) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.alignmentError"),
                    resources.getString("ImageOverlayAlignmentWindow.noImageAvailable")
            );
            return;
        }
        ImageData<BufferedImage> imageDataToAlign = images.getSelectionModel().selectedItemProperty().get();
        if (imageDataToAlign == null) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.alignmentError"),
                    resources.getString("ImageOverlayAlignmentWindow.noOverlaySelected")
            );
            return;
        }
        if (baseImageData == imageDataToAlign) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.alignmentError"),
                    resources.getString("ImageOverlayAlignmentWindow.selectImageOverlay")
            );
            return;
        }

        double downsample;
        if (pixelSizeMicrons > 0) {
            if (baseImageData.getServerMetadata().getPixelCalibration().hasPixelSizeMicrons()) {
                downsample = pixelSizeMicrons / baseImageData.getServerMetadata().getPixelCalibration().getAveragedPixelSizeMicrons();
            } else {
                Dialogs.showErrorMessage(
                        resources.getString("ImageOverlayAlignmentWindow.alignmentError"),
                        resources.getString("ImageOverlayAlignmentWindow.setPixelSize")
                );
                return;
            }
        } else {
            downsample = 1;
        }

        try {
            imageTransform.alignTransform(
                    baseImageData,
                    imageDataToAlign,
                    alignmentType.getValue(),
                    transformationTypes.getValue(),
                    downsample
            );
        } catch (Exception e) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.alignmentError"),
                    MessageFormat.format(
                            resources.getString("ImageOverlayAlignmentWindow.errorDuringAutoAlign"),
                            e.getLocalizedMessage()
                    )
            );
            logger.error("Error when auto aligning {} to {}", imageDataToAlign, baseImageData, e);
            return;
        }

        Dialogs.showInfoNotification(
                resources.getString("ImageOverlayAlignmentWindow.autoAlignment"),
                resources.getString("ImageOverlayAlignmentWindow.autoAlignmentCompleted")
        );
    }

    @FXML
    private void onUpdateClicked(ActionEvent ignored) {
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot update transform from {}", affineTransformation.getText());
            return;
        }

        try {
            double[] values = GeometryTools.parseTransformMatrix(affineTransformation.getText()).getMatrixEntries();
            imageTransform.setTransform(values[0], values[3], values[1], values[4], values[2], values[5]);
        } catch (Exception e) {
            logger.error("Cannot parse transform {}. {} not updated", affineTransformation.getText(), imageTransform, e);

            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.parseAffineTransform"),
                    resources.getString("ImageOverlayAlignmentWindow.unableToParseAffineTransform")
            );
            return;
        }

        Dialogs.showInfoNotification(
                resources.getString("ImageOverlayAlignmentWindow.updateTransform"),
                resources.getString("ImageOverlayAlignmentWindow.transformUpdated")
        );
    }

    @FXML
    private void onInvertClicked(ActionEvent ignored) {
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot invert transform");
            return;
        }

        try {
            imageTransform.invertTransform();
        } catch (NoninvertibleTransformException e) {
            logger.error("Cannot invert {}", imageTransform, e);

            Dialogs.showErrorNotification(
                    resources.getString("ImageOverlayAlignmentWindow.invertTransform"),
                    resources.getString("ImageOverlayAlignmentWindow.transformNotInvertible")
            );
            return;
        }

        Dialogs.showInfoNotification(
                resources.getString("ImageOverlayAlignmentWindow.invertTransform"),
                resources.getString("ImageOverlayAlignmentWindow.transformInverted")
        );
    }

    @FXML
    private void onResetClicked(ActionEvent ignored) {
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot reset transform");
            return;
        }

        imageTransform.resetTransform();

        Dialogs.showInfoNotification(
                resources.getString("ImageOverlayAlignmentWindow.resetTransform"),
                resources.getString("ImageOverlayAlignmentWindow.transformReset")
        );
    }

    @FXML
    private void onCopyClicked(ActionEvent ignored) {
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot copy transform");
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(affineTransformation.getText());
        Clipboard.getSystemClipboard().setContent(content);

        Dialogs.showInfoNotification(
                resources.getString("ImageOverlayAlignmentWindow.copyTransform"),
                resources.getString("ImageOverlayAlignmentWindow.transformCopied")
        );
    }

    @FXML
    private void onPropagateClicked(ActionEvent ignored) {
        Project<BufferedImage> project = quPath.getProject();
        if (project == null) {
            logger.error("No current QuPath project. Cannot propagate annotations");
            return;
        }
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot propagate annotations");
            return;
        }

        ImageData<BufferedImage> baseImageData = quPath.getViewer().getImageData();
        if (baseImageData == null) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.propagateAnnotations"),
                    resources.getString("ImageOverlayAlignmentWindow.noImageAvailable")
            );
            return;
        }
        ImageData<BufferedImage> selectedImageData = images.getSelectionModel().selectedItemProperty().get();
        if (selectedImageData == null) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.propagateAnnotations"),
                    resources.getString("ImageOverlayAlignmentWindow.ensureImageOverlaySelected")
            );
            return;
        }
        if (baseImageData == selectedImageData) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.propagateAnnotations"),
                    resources.getString("ImageOverlayAlignmentWindow.selectImageOverlay")
            );
            return;
        }
        ProjectImageEntry<BufferedImage> imageEntrySelected = project.getEntry(selectedImageData);
        if (imageEntrySelected == null) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.propagateAnnotations"),
                    resources.getString("ImageOverlayAlignmentWindow.selectedImageNotPartOfCurrentProject")
            );
            return;
        }

        logger.debug("Transforming with {} and copying annotations of {} to {}", imageTransform, baseImageData, selectedImageData);
        List<PathObject> transformedAnnotations = baseImageData.getHierarchy().getAnnotationObjects().stream()
                .map(annotation -> {
                    PathObject newAnnotation = PathObjects.createAnnotationObject(
                            imageTransform.transformROI(annotation.getROI()),
                            annotation.getPathClass(),
                            annotation.getMeasurementList()
                    );
                    newAnnotation.setName(annotation.getName());
                    return newAnnotation;
                })
                .toList();
        selectedImageData.getHierarchy().addObjects(transformedAnnotations);
        logger.debug("{} added to {}", transformedAnnotations, selectedImageData);

        try {
            imageEntrySelected.saveImageData(selectedImageData);

            Dialogs.showInfoNotification(
                    resources.getString("ImageOverlayAlignmentWindow.propagateAnnotations"),
                    resources.getString("ImageOverlayAlignmentWindow.annotationsTransformedAndCopied")
            );
        } catch (IOException e) {
            logger.error("Cannot save image data {}. Annotations not propagated", selectedImageData, e);

            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.propagateAnnotations"),
                    resources.getString("ImageOverlayAlignmentWindow.cannotSaveImageData")
            );
        }
    }

    private ObservableList<ImageDataViewer> createImageDataViewerList() {
        ObservableList<ImageDataViewer> imageDataViewers = FXCollections.observableArrayList();

        for (ImageData<BufferedImage> imageData: images.getItems()) {
            for (QuPathViewer viewer: quPath.getAllViewers()) {
                imageDataViewers.add(new ImageDataViewer(imageData, viewer));
            }
        }

        images.getItems().addListener((ListChangeListener<? super ImageData<BufferedImage>>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (ImageData<BufferedImage> imageData: change.getAddedSubList()) {
                        for (QuPathViewer viewer: quPath.getAllViewers()) {
                            imageDataViewers.add(new ImageDataViewer(imageData, viewer));
                        }
                    }
                }
                if (change.wasRemoved()) {
                    for (ImageData<BufferedImage> imageData: change.getRemoved()) {
                        imageDataViewers.removeIf(imageDataViewer -> imageData.equals(imageDataViewer.imageData()));
                    }
                }
            }
            change.reset();
        });
        quPath.getAllViewers().addListener((ListChangeListener<? super QuPathViewer>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (QuPathViewer viewer: change.getAddedSubList()) {
                        for (ImageData<BufferedImage> imageData: images.getItems()) {
                            imageDataViewers.add(new ImageDataViewer(imageData, viewer));
                        }
                    }
                }
                if (change.wasRemoved()) {
                    for (QuPathViewer viewer: change.getRemoved()) {
                        imageDataViewers.removeIf(imageDataViewer -> imageDataViewer.viewer().equals(viewer));
                    }
                }
            }
            change.reset();
        });

        return imageDataViewers;
    }

    private void rotate(int sign) {
        ImageTransform imageTransform = selectedImageTransform.get();
        if (imageTransform == null) {
            logger.error("No current image transform. Cannot rotate transform");
            return;
        }

        double theta;
        try {
            theta = sign * Double.parseDouble(rotationIncrement.getText());
        } catch (NumberFormatException e) {
            logger.error("Cannot parse rotation increment {} to a double", rotationIncrement.getText(), e);

            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.rotateOverlay"),
                    MessageFormat.format(
                            resources.getString("ImageOverlayAlignmentWindow.rotationCannotBeConvertedToNumber"),
                            rotationIncrement.getText()
                    )
            );
            return;
        }

        QuPathViewer viewer = quPath.getViewer();
        if (viewer == null) {
            Dialogs.showErrorMessage(
                    resources.getString("ImageOverlayAlignmentWindow.rotateOverlay"),
                    resources.getString("ImageOverlayAlignmentWindow.noActiveViewer")
            );
            return;
        }

        imageTransform.rotateTransform(Math.toRadians(theta), viewer.getCenterPixelX(), viewer.getCenterPixelY());
    }
}
