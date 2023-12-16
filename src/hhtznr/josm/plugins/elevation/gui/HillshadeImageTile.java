package hhtznr.josm.plugins.elevation.gui;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

import org.openstreetmap.josm.data.Bounds;

/**
 * Class implementing a hillshade tile which is an image with the hillshade
 * color values and the latitude-longitude coordinates of its edges.
 *
 * @author Harald Hetzner
 */
public class HillshadeImageTile extends AbstractSRTMTileGridPaintable {
    private final BufferedImage image;

    /**
     * Creates a new hillshade image tile.
     *
     * @param image         The image with the hillshade alpha values.
     * @param nominalBounds The nominal bounds in latitude-longitude coordinate
     *                      space.
     * @param actualBounds  The actual bounds in latitude-longitude coordinate
     *                      space.
     */
    public HillshadeImageTile(BufferedImage image, Bounds nominalBounds, Bounds actualBounds) {
        super(nominalBounds, actualBounds);
        this.image = image;
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
        // Scale the hillshade image to screen dimensions
        // https://stackoverflow.com/questions/4216123/how-to-scale-a-bufferedimage
        double sx = Double.valueOf(width) / Double.valueOf(image.getWidth());
        double sy = Double.valueOf(height) / Double.valueOf(image.getHeight());
        AffineTransform at = AffineTransform.getScaleInstance(sx, sy);
        AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        return scaleOp.filter(image, null);
    }
}
