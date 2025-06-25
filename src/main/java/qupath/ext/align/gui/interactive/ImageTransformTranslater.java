package qupath.ext.align.gui.interactive;

import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.align.core.AffineImageTransform;
import qupath.lib.gui.viewer.QuPathViewer;

import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * A mouse event handler that translates an {@link AffineImageTransform} when the mouse is dragged with the primary
 * button and the Shift modifier pressed.
 */
class ImageTransformTranslater implements EventHandler<MouseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ImageTransformTranslater.class);
    private final ObservableValue<AffineImageTransform> selectedImageTransform;
    private final ObservableValue<QuPathViewer> selectedQuPathViewer;
    private Point2D pDragging;

    /**
     * Create the mouse event handler.
     *
     * @param selectedImageTransform an observable value containing the {@link AffineImageTransform} to translate. It shouldn't be
     *                               null but its value can
     * @param selectedQuPathViewer an observable value containing the current QuPah viewer. It shouldn't be null but its value
     *                             can
     * @throws NullPointerException if one of the provided parameter is null
     */
    public ImageTransformTranslater(ObservableValue<AffineImageTransform> selectedImageTransform, ObservableValue<QuPathViewer> selectedQuPathViewer) {
        this.selectedImageTransform = Objects.requireNonNull(selectedImageTransform);
        this.selectedQuPathViewer = Objects.requireNonNull(selectedQuPathViewer);
    }

    @Override
    public void handle(MouseEvent event) {
        if (!event.isPrimaryButtonDown() || event.isConsumed()) {
            logger.trace("Primary button not pressed or mouse event {} already consumed. Not doing anything", event);
            return;
        }

        AffineImageTransform affineImageTransform = selectedImageTransform.getValue();
        if (affineImageTransform == null) {
            logger.trace("No current image transform. Not doing anything");
            return;
        }

        QuPathViewer viewer = selectedQuPathViewer.getValue();
        if (viewer == null) {
            logger.trace("No viewer currently active. Not doing anything");
            return;
        }

        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            pDragging = viewer.componentPointToImagePoint(event.getX(), event.getY(), pDragging, true);
            logger.trace("Mouse pressed. Setting dragging point to {}", pDragging);
        } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
            Point2D point = viewer.componentPointToImagePoint(event.getX(), event.getY(), null, true);
            if (event.isShiftDown() && pDragging != null) {
                double dx = pDragging.getX() - point.getX();
                double dy = pDragging.getY() - point.getY();
                affineImageTransform.translateTransform(dx, dy);
                event.consume();
                logger.trace("Mouse dragged and shift modifier down. {} dragged by [{}, {}] and {} consumed", affineImageTransform, dx, dy, event);
            } else {
                logger.trace("Mouse dragged. Setting dragging point to {}", pDragging);
            }
            pDragging = point;
        }
    }
}
