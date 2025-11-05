package hhtznr.josm.plugins.elevation.gui;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;
import hhtznr.josm.plugins.elevation.math.Hillshade;

/**
 * Class implementing a hillshade tile which is an image with the hillshade
 * color values and the latitude-longitude coordinates of its edges.
 *
 * @author Harald Hetzner
 */
public class HillshadeImageTile extends AbstractSRTMTileGridPaintable {

    public static final int BOUNDS_SCALE_RASTER_STEP = 3;

    private final BufferedImage image;

    /**
     * Creates a new hillshade image tile.
     *
     * @param tileGrid        The SRTM tile grid from which elevation data should be
     *                        obtained.
     * @param renderingBounds The bounds in latitude-longitude coordinate space,
     *                        within which this hillshade image tile can be
     *                        rendered. The very edges might show artifacts.
     * @param altitudeDeg     The altitude is the angle of the illumination source
     *                        above the horizon. The units are in degrees, from 0
     *                        (on the horizon) to 90 (overhead).
     * @param azimuthDeg      The azimuth is the angular direction of the sun,
     *                        measured from north in clockwise degrees from 0 to
     *                        360.
     */
    public HillshadeImageTile(SRTMTileGrid tileGrid, Bounds renderingBounds, double altitudeDeg, double azimuthDeg,
            boolean withPerimeter) {
        super(tileGrid, renderingBounds, renderingBounds);
        Hillshade hillshade = new Hillshade(tileGrid, this.renderingBounds, renderingRasterIndexBounds, altitudeDeg,
                azimuthDeg);
        image = hillshade.getHillshadeImage(withPerimeter);
        this.renderingBounds = hillshade.getRenderingBounds();
        this.viewBounds = tileGrid.getViewBoundsScaledByRasterStep(this.renderingBounds, BOUNDS_SCALE_RASTER_STEP);
    }

    /**
     * Returns the image with the hillshade alpha values of this tile.
     *
     * @return The image with the hillshade alpha values.
     */
    public BufferedImage getUnscaledImage() {
        return image;
    }

    /**
     * Creates a scaled instance of the image with the hillshade alpha values.
     *
     * @param width  The desired image width.
     * @param height The desired image height.
     * @return A hillshade image that was scaled to the desired dimensions using
     *         bilinear interpolation.
     */
    public BufferedImage getScaledImage(int width, int height) {
        if (image == null)
            return null;
        // Scale the hillshade image to screen dimensions
        // https://stackoverflow.com/questions/4216123/how-to-scale-a-bufferedimage
        double sx = (double) width / (double) image.getWidth();
        double sy = (double) height / (double) image.getHeight();
        AffineTransform at = AffineTransform.getScaleInstance(sx, sy);
        AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(image, null);
    }
}
