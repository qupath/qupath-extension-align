package qupath.ext.align.gui;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.projects.Project;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * A cell displaying an image name and thumbnail.
 */
class ImageEntryCell extends ListCell<ImageData<BufferedImage>> {

    private static final Logger logger = LoggerFactory.getLogger(ImageEntryCell.class);
    private static final int IMAGE_WIDTH = 80;
    private static final int IMAGE_HEIGHT = 60;
    private static final int THUMBNAIL_CACHE_SIZE = 64;
    private static final Cache<ImageData<BufferedImage>, BufferedImage> thumbnailCache = CacheBuilder.newBuilder()
            .maximumSize(THUMBNAIL_CACHE_SIZE)
            .softValues()
            .build();
    private final ImageView imageView = new ImageView();
    private final ObservableValue<Project<BufferedImage>> projectProperty;
    private final ObservableValue<ImageData<BufferedImage>> imageInCurrentViewerObservable;
    private ChangeListener<? super ImageData<BufferedImage>> imageInCurrentViewerListener = null;

    /**
     * Create the cell.
     *
     * @param projectProperty an observable value containing the current QuPath project. This is used to determine the image name
     *                        when updating this cell. It cannot be null but its value can be
     * @param viewerProperty an observable value containing the current viewer. If this cell represents the image contained in the
     *                       current viewer, its name will be displayed differently. This observable must be updated from the
     *                       JavaFX Application Thread. It cannot be null but its value can be
     * @throws NullPointerException if one of the provided parameters is null
     */
    public ImageEntryCell(ObservableValue<Project<BufferedImage>> projectProperty, ObservableValue<QuPathViewer> viewerProperty) {
        this.projectProperty = Objects.requireNonNull(projectProperty);
        this.imageInCurrentViewerObservable = viewerProperty.flatMap(QuPathViewer::imageDataProperty);

        imageView.setFitWidth(IMAGE_WIDTH);
        imageView.setFitWidth(IMAGE_HEIGHT);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("image-cell-thumbnail");
    }

    @Override
    protected void updateItem(ImageData<BufferedImage> item, boolean empty) {
        super.updateItem(item, empty);

        setText(null);
        setStyle(null);
        setGraphic(null);
        if (imageInCurrentViewerListener != null) {
            imageInCurrentViewerObservable.removeListener(imageInCurrentViewerListener);
            imageInCurrentViewerListener = null;
        }

        if (item == null || empty) {
            return;
        }

        if (projectProperty.getValue() != null && projectProperty.getValue().getEntry(item) != null) {
            setText(projectProperty.getValue().getEntry(item).getImageName());
        } else {
            setText(ServerTools.getDisplayableImageName(item.getServer()));
        }

        imageInCurrentViewerListener = (p, o, n) -> updateStyle(item, n);
        imageInCurrentViewerObservable.addListener(imageInCurrentViewerListener);
        imageInCurrentViewerListener.changed(imageInCurrentViewerObservable, null, imageInCurrentViewerObservable.getValue());

        try {
            BufferedImage thumbnail = thumbnailCache.get(
                    item,
                    () -> {
                        BufferedImage projectEntryThumbnail = null;     // project entry thumbnail is usually more representative than
                                                                        // image server thumbnail at z=0,t=0
                        if (projectProperty.getValue() != null && projectProperty.getValue().getEntry(item) != null) {
                            projectEntryThumbnail = projectProperty.getValue().getEntry(item).getThumbnail();
                        }
                        if (projectEntryThumbnail != null) {
                            return projectEntryThumbnail;
                        }

                        BufferedImage serverThumbnail = item.getServer().getDefaultThumbnail(0, 0);
                        if (serverThumbnail != null) {
                            return serverThumbnail;
                        }

                        throw new IllegalArgumentException(String.format(
                                "Could not retrieve thumbnail of %s",
                                item
                        ));
                    }
            );

            imageView.setImage(SwingFXUtils.toFXImage(thumbnail, null));
            setGraphic(imageView);
        } catch (Exception e) {
            logger.debug("Cannot retrieve thumbnail of {}, so cannot set thumbnail of {}", item, this, e);
        }
    }

    private void updateStyle(ImageData<BufferedImage> item, ImageData<BufferedImage> imageDataInCurrentViewer) {
        try {
            ImageServer<BufferedImage> server = item.getServer();
            ImageServer<BufferedImage> serverOfImageInCurrentViewer = imageDataInCurrentViewer == null ? null : imageDataInCurrentViewer.getServer();

            if (server != null && serverOfImageInCurrentViewer != null && server.getURIs().equals(serverOfImageInCurrentViewer.getURIs())) {
                getStyleClass().remove("not-current-viewer-cell");
                if (!getStyleClass().contains("current-viewer-cell")) {
                    getStyleClass().add("current-viewer-cell");
                }
                return;
            }
        } catch (Exception e) {
            logger.debug("Cannot get server of {}. Considering it is not in the current viewer", item, e);
        }

        getStyleClass().remove("current-viewer-cell");
        if (!getStyleClass().contains("not-current-viewer-cell")) {
            getStyleClass().add("not-current-viewer-cell");
        }
    }
}
