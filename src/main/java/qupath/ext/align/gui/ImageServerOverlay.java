/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.ext.align.gui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.transform.Affine;
import javafx.scene.transform.TransformChangedEvent;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.ImageRegion;

import qupath.lib.roi.RoiTools;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.interfaces.ROI;


import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import qupath.lib.geom.Point2;

/**
 * A {@link PathOverlay} implementation capable of painting one image on top of another, 
 * including an optional affine transformation.
 * 
 * @author Pete Bankhead
 */
public class ImageServerOverlay extends AbstractOverlay {
	
	private static Logger logger = LoggerFactory.getLogger(ImageServerOverlay.class);
	
	private DefaultImageRegionStore store;
	private ImageServer<BufferedImage> server;
	
	private ImageRenderer renderer;
	
	private Affine affine = new Affine();
	
	private AffineTransform transform;
	private AffineTransform transformInverse;

	private PixelCalibration viewerImageCalibration;
	private PixelCalibration overlayImageCalibration;
	
	/**
	 * Constructor.
	 * @param viewer viewer to which the overlay should be added
	 * @param server ImageServer that should be displayed on the overlay
	 */
	public ImageServerOverlay(final QuPathViewer viewer, final ImageServer<BufferedImage> server) {
		this(viewer, server, new Affine());
	}
	
	/**
	 * Constructor.
	 * @param viewer viewer to which the overlay should be added
	 * @param server ImageServer that should be displayed on the overlay
	 * @param affine Affine transform to apply to the overlaid server
	 */
	public ImageServerOverlay(final QuPathViewer viewer, final ImageServer<BufferedImage> server, final Affine affine) {
		super(viewer.getOverlayOptions());
		this.store = viewer.getImageRegionStore();
		this.server = server;
		this.transform = new AffineTransform();
		this.transformInverse = null;//transform.createInverse();
		
		this.affine = affine;
		// Access the PixelCalibration from the viewer and server and
		// reset the affine transform to a scaled identity
		this.viewerImageCalibration = viewer.getImageData().getServer().getPixelCalibration();
		this.overlayImageCalibration = server.getPixelCalibration();
		resetAffine();
		
		// Request repaint any time the transform changes
		this.affine.addEventHandler(TransformChangedEvent.ANY, e ->  {
			updateTransform();
			viewer.repaintEntireImage();
		});
		updateTransform();
	}
	
	/**
	 * Get the current renderer.
	 * @return
	 */
	public ImageRenderer getRenderer() {
		return renderer;
	}
	
	/**
	 * Set the rendered, which controls conversion of the image to RGB.
	 * @param renderer
	 */
	public void setRenderer(ImageRenderer renderer) {
		this.renderer = renderer;
	}
	
	/**
	 * Get the affine transform applied to the overlay image.
	 * Making changes here will trigger repaints in the viewer.
	 * @return
	 */
	public Affine getAffine() {
		return affine;
	}
	
	/**
	 * Get the affine transform applied to the overlay image.
	 * Making changes here will trigger repaints in the viewer.
	 * @return
	 */
	public AffineTransform getTransform() {
		return transform;
	}


	/**
	 * Reset the affine transform to its pixel-correct scaled identity
	 */
	public void resetAffine() {
		// The scaling factors's defaults
		double mxx = 1;
		double myy = 1;

		if (this.affine == null)
			return;

        // Calculate the affine 'a' and 'y' scaling factor parameters - Defaults to 1 and 1 if no pixel size micron available.
		if (this.viewerImageCalibration.hasPixelSizeMicrons() && this.overlayImageCalibration.hasPixelSizeMicrons()) {
			mxx = this.viewerImageCalibration.getPixelWidthMicrons() / overlayImageCalibration.getPixelWidthMicrons();
			myy = this.viewerImageCalibration.getPixelHeightMicrons() / overlayImageCalibration.getPixelHeightMicrons();
		}

		this.affine.setToTransform(mxx, 0, 0, 0, myy, 0);
	}
	
	private void updateTransform() {
		transform.setTransform(
			affine.getMxx(),
			affine.getMyx(),
			affine.getMxy(),
			affine.getMyy(),
			affine.getTx(),
			affine.getTy()
			);
		try {
			transformInverse = transform.createInverse();
		} catch (NoninvertibleTransformException e) {
			logger.warn("Unable to invert transform", e);
		}
	}

	@Override
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageData<BufferedImage> imageData, boolean paintCompletely) {

		BufferedImage imgThumbnail = null;//store.getThumbnail(server, imageRegion.getZ(), imageRegion.getT(), true);
			
		// Paint the image
		Graphics2D gCopy = (Graphics2D)g2d.create();
		if (transformInverse != null) {
			AffineTransform transformOld = gCopy.getTransform();
			transformOld.concatenate(transformInverse);
			gCopy.setTransform(transformOld);
		} else {
			logger.debug("Inverse affine transform is null!");
		}
		var composite = getAlphaComposite();
		if (composite != null)
			gCopy.setComposite(composite);
		if (PathPrefs.viewerInterpolateBilinearProperty().get())
			gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		else
			gCopy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		store.paintRegion(server, gCopy, gCopy.getClip(), imageRegion.getZ(), imageRegion.getT(), downsampleFactor, imgThumbnail, null, renderer);
		gCopy.dispose();
				
	}

	/**
	 * Transform object, recursively transforming all child objects
	 *
	 * @param pathObject
	 * @return
	 */
	public PathObject transformObject(PathObject pathObject) {
		// Create a new object with the converted ROI
		var roi = pathObject.getROI();
		var roi2 = this.transformROI(roi, transform);

		PathObject newObject = null;

		newObject = PathObjects.createAnnotationObject(roi2, pathObject.getPathClass(), pathObject.getMeasurementList());
		newObject.setName(pathObject.getName());

		return newObject;
	}

	/**
	 * Transform ROI (via conversion to Java AWT shape)
	 *
	 * @param roi
	 * @param transform
	 * @return
	 */
	private ROI transformROI(ROI roi, AffineTransform transform) {
		if (roi.getRoiType() == ROI.RoiType.POINT) {
			List<Point2> points = roi.getAllPoints();
			var nPoints = points.size();

			// Convert List<Point2> to Point2D[]
			Point2D[] pointsArray = points.stream()
                              .map(point -> new Point2D.Double(point.getX(), point.getY()))
                              .toArray(Point2D[]::new);

			Point2D[] points2 = new Point2D[nPoints];
			transform.transform(pointsArray,0,points2,0,nPoints);

			// Create a list to store Point2 objects
			List<Point2> pointsList = new ArrayList<>(nPoints);

			// Add Point2 objects to the list
			for (int i = 0; i < nPoints; i++) {
				Point2D point = points2[i];
				// Create a new Point2 object and add it to the list
				pointsList.add(new Point2(point.getX(), point.getY()));
			}
			return ROIs.createPointsROI(pointsList, roi.getImagePlane());
		} else {
			var shape = RoiTools.getShape(roi); // Should be able to use roi.getShape() - but there's currently a bug in it for rectangles/ellipses!
			var shape2 = transform.createTransformedShape(shape);
			var roi2 = RoiTools.getShapeROI(shape2, roi.getImagePlane(), 0.5);
			return roi2;
		}
	}
}
