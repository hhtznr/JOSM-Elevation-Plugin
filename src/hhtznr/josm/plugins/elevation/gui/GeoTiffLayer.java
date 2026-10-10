package hhtznr.josm.plugins.elevation.gui;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;

import javax.swing.Action;
import javax.swing.Icon;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.ImageProvider;

import hhtznr.josm.plugins.elevation.data.GeoTiffImage;

/**
 * This class implements a JOSM layer for displaying GeoTIFF images. It converts
 * the image to grayscale and handles the rendering of the image on the map
 * view.
 *
 * @author Harald Hetzner
 */
public class GeoTiffLayer extends Layer {
    private final Icon icon;

    private final GeoTiffImage geoTiffImage;
    private final BufferedImage grayscaleImage;

    public GeoTiffLayer(String name, GeoTiffImage image) {
        super(name);
        this.icon = ImageProvider.get("", "elevation.svg");

        this.geoTiffImage = image;
        this.grayscaleImage = convertToGrayscale(image.getRawImage(), image.getNoDataValues());
    }

    private BufferedImage convertToGrayscale(RenderedImage img, double[] noDataValues) {
        if (img.getSampleModel().getNumBands() == 1) {
            return convertSingleBandToGrayscale(img, noDataValues);
        }

        BufferedImage bi;
        if (img instanceof BufferedImage) {
            bi = (BufferedImage) img;
        } else {
            // Buffer standard PlanarImage wrappers if returned by ImageIO/GeoTools
            bi = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.drawRenderedImage(img, new AffineTransform());
            g.dispose();
        }

        // Apply a Grayscale Filter matrix operation
        BufferedImage gray = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(bi, gray);
        return gray;
    }

    private BufferedImage convertSingleBandToGrayscale(RenderedImage img, double[] noDataValues) {
        Raster raster = img.getData();
        int minX = img.getMinX();
        int minY = img.getMinY();
        int width = img.getWidth();
        int height = img.getHeight();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                double value = raster.getSampleDouble(x, y, 0);
                if (isValidSample(value, noDataValues)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        if (min == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("Elevation: GeoTIFF raster of file '"
                    + geoTiffImage.getFile().getAbsolutePath() + "' contains no valid single-band samples");
        }

        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                double value = raster.getSampleDouble(x, y, 0);
                int intensity = 0;
                if (isValidSample(value, noDataValues)) {
                    intensity = (min == max) ? 127
                            : (int) Math.max(0, Math.min(255, Math.round((value - min) * 255 / (max - min))));
                }
                gray.getRaster().setSample(x - minX, y - minY, 0, intensity);
            }
        }
        return gray;
    }

    private boolean isValidSample(double value, double[] noDataValues) {
        if (!Double.isFinite(value)) {
            return false;
        }
        if (noDataValues != null) {
            for (double noDataValue : noDataValues) {
                if (Double.isNaN(noDataValue) ? Double.isNaN(value) : value == noDataValue) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void paint(Graphics2D g, MapView mapView, Bounds mapBounds) {
        ReferencedEnvelope geoBounds = geoTiffImage.getBounds();
        LatLon northWest = new LatLon(geoBounds.getMaxY(), geoBounds.getMinX());
        LatLon southEast = new LatLon(geoBounds.getMinY(), geoBounds.getMaxX());

        Point topLeftScreen = mapView.getPoint(northWest);
        Point bottomRightScreen = mapView.getPoint(southEast);

        int width = bottomRightScreen.x - topLeftScreen.x;
        int height = bottomRightScreen.y - topLeftScreen.y;
        if (width > 0 && height > 0) {
            g.drawImage(grayscaleImage, topLeftScreen.x, topLeftScreen.y, width, height, null);
        }
    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public String getToolTipText() {
        return null;
    }

    @Override
    public void mergeFrom(Layer from) {
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
        ReferencedEnvelope geoBounds = geoTiffImage.getBounds();
        v.visit(new Bounds(geoBounds.getMinY(), geoBounds.getMinX(), geoBounds.getMaxY(), geoBounds.getMaxX()));
    }

    @Override
    public Action[] getMenuEntries() {
        return null;
    }
}
