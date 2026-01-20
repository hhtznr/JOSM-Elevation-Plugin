package hhtznr.josm.plugins.elevation.gui;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.SRTMTileGridView;
import hhtznr.josm.plugins.elevation.math.Hillshade;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * Class implementing a hillshade tile which is an image with the hillshade
 * color values and the latitude-longitude coordinates of its edges.
 *
 * @author Harald Hetzner
 */
public class HillshadeImageTile extends AbstractSRTMTileGridPaintable {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator("Hillshade image tile");

    public static final int BOUNDS_SCALE_RASTER_STEP = 3;

    private final BufferedImage image;

    /**
     * Reference to the hillshade bounds, which - for conceptual reasons - are
     * slightly offset with regard to the SRTM tile grid bounds.
     */
    private final Bounds bounds;

    /**
     * Creates a new hillshade image tile.
     *
     * @param tileGridView  The SRTM tile grid view from which elevation data should
     *                      be obtained.
     * @param altitudeDeg   The altitude is the angle of the illumination source
     *                      above the horizon. The units are in degrees, from 0 (on
     *                      the horizon) to 90 (overhead).
     * @param azimuthDeg    The azimuth is the angular direction of the sun,
     *                      measured from north in clockwise degrees from 0 to 360.
     * @param withPerimeter If {@code} true, the a first and last row as well as the
     *                      a first and last column without computed values will be
     *                      added such that the size of the 2D array corresponds to
     *                      that of the input data. If {@code false}, these rows and
     *                      columns will be omitted.
     */
    public HillshadeImageTile(SRTMTileGridView tileGridView, double altitudeDeg, double azimuthDeg,
            boolean withPerimeter) {
        super(namer.nextName(), tileGridView);
        Hillshade hillshade = new Hillshade(tileGridView, altitudeDeg, azimuthDeg);
        image = hillshade.getHillshadeImage(withPerimeter);
        bounds = hillshade.getBounds();
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

    @Override
    public Bounds getBounds() {
        return bounds;
    }
}
