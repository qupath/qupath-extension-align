package qupath.ext.align.gui;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ListSelectionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.align.Utils;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * A pane that asks the user to select project entries.
 */
class ImagesSelector extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(ImagesSelector.class);
    private final List<ProjectImageEntry<BufferedImage>> allEntries;
    @FXML
    private ListSelectionView<ProjectImageEntry<BufferedImage>> images;

    /**
     * Create the pane.
     *
     * @param allEntries all project entries that can be selected or unselected
     * @param alreadySelectedEntries some project entries to show as already selected
     * @throws IOException if an error occurs while loading the FXML file containing this pane
     * @throws NullPointerException if one of the provided list is null or if they contain a null element
     */
    public ImagesSelector(List<ProjectImageEntry<BufferedImage>> allEntries, List<ProjectImageEntry<BufferedImage>> alreadySelectedEntries) throws IOException {
        this.allEntries = allEntries;

        Utils.loadFXML(this, ImagesSelector.class.getResource("image_selector.fxml"));

        images.getSourceItems().setAll(allEntries.stream()
                .filter(entry -> !alreadySelectedEntries.contains(entry))
                .toList()
        );
        images.getTargetItems().setAll(alreadySelectedEntries);
        images.setCellFactory(l -> createListCell());

        TextField filter = new TextField();
        filter.textProperty().addListener((v, o, n) -> {
            String text = n.trim().toLowerCase();

            images.getSourceItems().setAll(allEntries.stream()
                    .filter(entry ->
                            !images.getTargetItems().contains(entry) && entry.getImageName().toLowerCase().contains(text)
                    )
                    .toList()
            );
        });
        images.setSourceFooter(filter);
    }

    /**
     * @return all entries that were not selected by the user
     */
    public List<ProjectImageEntry<BufferedImage>> getUnselectedImages() {
        return allEntries.stream()
                .filter(entry -> !images.getTargetItems().contains(entry))
                .toList();
    }

    /**
     * @return all entries that were selected by the user
     */
    public List<ProjectImageEntry<BufferedImage>> getSelectedImages() {
        return images.getTargetItems();
    }

    private ListCell<ProjectImageEntry<BufferedImage>> createListCell() {
        return new ListCell<>() {
            private static final int THUMBNAIL_SIZE = 250;

            @Override
            protected void updateItem(ProjectImageEntry<BufferedImage> item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                    setTooltip(null);
                    return;
                }

                setText(item.getImageName());

                Tooltip tooltip = new Tooltip(item.getSummary());
                try {
                    BufferedImage thumbnail = item.getThumbnail();
                    if (thumbnail == null) {
                        logger.debug("Thumbnail of {} was not set. Cannot display it on tooltip", item);
                    } else {
                        ImageView imageView = new ImageView();
                        imageView.setFitWidth(THUMBNAIL_SIZE);
                        imageView.setFitHeight(THUMBNAIL_SIZE);
                        imageView.setPreserveRatio(true);
                        imageView.setImage(SwingFXUtils.toFXImage(thumbnail, null));
                        tooltip.setGraphic(imageView);
                    }
                } catch (IOException e) {
                    logger.debug("Unable to read thumbnail of {}. Cannot display it on tooltip", item, e);
                }
                setTooltip(tooltip);
            }
        };
    }
}
