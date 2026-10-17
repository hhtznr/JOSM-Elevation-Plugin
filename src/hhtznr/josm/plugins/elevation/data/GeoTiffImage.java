package hhtznr.josm.plugins.elevation.data;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.io.imageio.geotiff.GeoTiffIIOMetadataDecoder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.openstreetmap.josm.data.Bounds;

/**
 * This class implements a wrapper around the GeoTools objects and methods
 * representing GeoTIFF data.
 *
 * @author Harald Hetzner
 */
public class GeoTiffImage {
    private final File file;
    private final GeoTiffIIOMetadataDecoder metadata;
    private final GridCoverage2D coverage;
    private final ReferencedEnvelope referencedEnvelope;
    private final int width;
    private final int height;

    /**
     * Creates a new GeoTIFF image.
     *
     * @param file     A reference to the GeoTIFF file.
     * @param metadata The metadata of the GeoTIFF image.
     * @param coverage The grid coverage of the GeoTIFF.
     * @throws GeoTiffImageException If the GeoTIFF file cannot be displayed due to
     *                               missing coordinate reference system or
     *                               transformation issues.
     */
    public GeoTiffImage(File file, GeoTiffIIOMetadataDecoder metadata, GridCoverage2D coverage)
            throws GeoTiffImageException {
        this.file = file;
        this.metadata = metadata;
        this.coverage = coverage;
        ReferencedEnvelope referencedEnvelope = new ReferencedEnvelope(coverage.getEnvelope2D());
        CoordinateReferenceSystem sourceCrs = referencedEnvelope.getCoordinateReferenceSystem();
        if (sourceCrs == null) {
            String message = "GeoTIFF file '" + file.getAbsolutePath()
                    + "' cannot be displayed because it has no coordinate reference system";
            throw new GeoTiffImageException(message);
        }
        try {
            // For practical JOSM mapping, EPSG:4326 is equivalent to WGS84 used by JOSM
            CoordinateReferenceSystem geographicCrs = CRS.decode("EPSG:4326", true);
            this.referencedEnvelope = referencedEnvelope.transform(geographicCrs, true, 20);
        } catch (FactoryException | TransformException e) {
            String message = "Cannot transform GeoTIFF bounds to latitude/longitude for '" + file.getAbsolutePath()
                    + "' due to " + e;
            throw new GeoTiffImageException(message, e);
        }
        GridEnvelope2D range = coverage.getGridGeometry().getGridRange2D();
        width = range.width;
        height = range.height;
    }

    /**
     * Returns the file associated with this GeoTIFF image.
     *
     * @return The file associated with this GeoTIFF image.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns the GeoTIFF metadata exposed by GeoTools. This does not include every
     * TIFF metadata field that might be present on the file.
     *
     * @return The GeoTIFF metadata.
     */
    public GeoTiffIIOMetadataDecoder getMetadata() {
        return metadata;
    }

    /**
     * Returns the GeoTools coverage backing this image.
     *
     * @return the grid coverage
     */
    public GridCoverage2D getCoverage() {
        return coverage;
    }

    /**
     * Returns the raw image data of the GeoTIFF.
     *
     * @return The raw image data of the GeoTIFF.
     */
    public RenderedImage getRawImage() {
        return coverage.getRenderedImage();
    }

    /**
     * Returns the geographic bounds of the GeoTIFF image in GeoTool's
     * representation {@code ReferencedEnvelope}.
     *
     * @return The geographic bounds of the GeoTIFF image.
     */
    public ReferencedEnvelope getReferencedEnvelope() {
        return referencedEnvelope;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Returns the geographic bounds of the GeoTIFF image in JOSM's representation
     * {@code Bounds}.
     *
     * @return The geographic bounds of the GeoTIFF image.
     */
    public Bounds getBounds() {
        return new Bounds(referencedEnvelope.getMinY(), referencedEnvelope.getMinX(), referencedEnvelope.getMaxY(),
                referencedEnvelope.getMaxX());
    }

    /**
     * Returns the values representing "no data" in the GeoTIFF image.
     *
     * @return An array of values representing "no data" in the GeoTIFF image.
     */
    public double[] getNoDataValues() {
        return coverage.getSampleDimensions()[0].getNoDataValues();
    }

    /**
     * Returns a grayscale image representing this GeoTIFF image.
     *
     * @return A grayscale representation of this GeoTIFF image.
     */
    public BufferedImage getGrayScaleImage() {
        RenderedImage image = getRawImage();

        double[] noDataValues = getNoDataValues();
        if (image.getSampleModel().getNumBands() == 1) {
            try {
                return convertSingleBandToGrayscale(image, noDataValues);
            } catch (IllegalArgumentException e) {
                String message = "Error for GeoTIFF file " + file.getAbsolutePath() + ": " + e.toString();
                throw new IllegalArgumentException(message, e);
            }
        }

        BufferedImage bi;
        if (image instanceof BufferedImage) {
            bi = (BufferedImage) image;
        } else {
            bi = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.drawRenderedImage(image, new AffineTransform());
            g.dispose();
        }

        // Apply a grayscale filter matrix operation
        BufferedImage grayscaleImage = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(bi, grayscaleImage);
        return grayscaleImage;
    }

    private static BufferedImage convertSingleBandToGrayscale(RenderedImage image, double[] noDataValues) {
        Raster raster = image.getData();
        int minX = image.getMinX();
        int minY = image.getMinY();
        int width = image.getWidth();
        int height = image.getHeight();
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
            throw new IllegalArgumentException("GeoTIFF raster contains no valid single-band samples");
        }

        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                double value = raster.getSampleDouble(x, y, 0);
                int argb = 0;
                if (isValidSample(value, noDataValues)) {
                    int intensity = (min == max) ? 127
                            : (int) Math.max(0, Math.min(255, Math.round((value - min) * 255 / (max - min))));
                    argb = 0xff000000 | (intensity << 16) | (intensity << 8) | intensity;
                }
                gray.setRGB(x - minX, y - minY, argb);
            }
        }
        return gray;
    }

    private static boolean isValidSample(double value, double[] noDataValues) {
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
}
