package hhtznr.josm.plugins.elevation.data;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Path;

import org.geotools.api.data.DataSourceException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.io.imageio.geotiff.GeoTiffIIOMetadataDecoder;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.GeneralBounds;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.tools.Logging;

/**
 * This class implements a wrapper around the GeoTools objects and methods
 * representing GeoTIFF data.
 *
 * @author Harald Hetzner
 */
public class GeoTiffImage {

    /**
     * Identifier of the EPSG:4326 coordinate reference system. For practical JOSM
     * mapping, EPSG:4326 is equivalent to WGS84 used by JOSM.
     */
    public static final String CRS_EPSG_4326 = "EPSG:4326";

    private final Path path;
    private final GeoTiffIIOMetadataDecoder metadata;
    private final GridCoverage2D coverage;
    private final String crs;
    private final ReferencedEnvelope referencedEnvelope;
    private final int width;
    private final int height;

    /**
     * Creates a new GeoTIFF image using {@link #CRS_EPSG_4326} as coordinate
     * reference system.
     *
     * @param path     The path of the GeoTiff file.
     * @param metadata The metadata of the GeoTIFF image.
     * @param coverage The grid coverage of the GeoTIFF.
     * @throws GeoTiffImageException If the GeoTIFF file cannot be displayed due to
     *                               missing coordinate reference system or
     *                               transformation issues.
     */
    public GeoTiffImage(Path path, GeoTiffIIOMetadataDecoder metadata, GridCoverage2D coverage)
            throws GeoTiffImageException {
        this(path, metadata, coverage, CRS_EPSG_4326);
    }

    /**
     * Creates a new GeoTIFF image.
     *
     * @param path     The path of the GeoTiff file.
     * @param metadata The metadata of the GeoTIFF image.
     * @param coverage The grid coverage of the GeoTIFF.
     * @param crs      The identifier of the coordinate reference system in which
     *                 the coordinates of the GeoTIFF pixels should be represented.
     * @throws GeoTiffImageException If the GeoTIFF file cannot be displayed due to
     *                               missing coordinate reference system or
     *                               transformation issues.
     */
    public GeoTiffImage(Path path, GeoTiffIIOMetadataDecoder metadata, GridCoverage2D coverage, String crs)
            throws GeoTiffImageException {
        this.path = path.toAbsolutePath();
        this.metadata = metadata;
        this.coverage = coverage;
        this.crs = crs;
        ReferencedEnvelope referencedEnvelope = new ReferencedEnvelope(coverage.getEnvelope2D());
        CoordinateReferenceSystem sourceCrs = referencedEnvelope.getCoordinateReferenceSystem();
        if (sourceCrs == null) {
            String message = "GeoTIFF file '" + this.path.toString()
                    + "' cannot be displayed because it has no coordinate reference system";
            throw new GeoTiffImageException(message);
        }
        try {
            CoordinateReferenceSystem geographicCrs = CRS.decode(crs, true);
            this.referencedEnvelope = referencedEnvelope.transform(geographicCrs, true, 20);
        } catch (FactoryException | TransformException e) {
            String message = "Cannot transform GeoTIFF bounds to latitude/longitude for '" + this.path.toString()
                    + "' due to " + e;
            throw new GeoTiffImageException(message, e);
        }
        GridEnvelope2D range = coverage.getGridGeometry().getGridRange2D();
        width = range.width;
        height = range.height;
    }

    /**
     * Returns the absolute path of the file associated with this GeoTIFF image.
     *
     * @return The absolute path associated with this GeoTIFF image.
     */
    public Path getAbsolutePath() {
        return path;
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
     * Returns a string representation of the coordinate reference system used for
     * the geographical representation of this GeoTIFF image.
     *
     * @return The designation of the coordinate reference system.
     */
    public String getCoordinateReferenceSystem() {
        return crs;
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

    /**
     * Returns the width of the GeoTIFF image.
     *
     * @return The width of the image.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of the GeoTIFF image.
     *
     * @return The height of the image.
     */
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
                String message = "Error for GeoTIFF file " + path.toString() + ": " + e.toString();
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

    /**
     * Reads a GeoTIFF image from a GeoTIFF file.
     *
     * @param path The path from which to read the GeoTIFF image.
     * @return The GeoTIFF image or {@code null} if an error occurs reading the
     *         file.
     */
    public static GeoTiffImage readGeoTiffFile(Path path) {
        GeoTiffReader reader = null;
        try {
            reader = new GeoTiffReader(path.toFile());
        } catch (DataSourceException e) {
            Logging.error("Elevation: Cannot read GeoTIFF file '" + path.toAbsolutePath().toString() + " due to "
                    + e.toString());
            return null;
        }
        try {
            GeoTiffIIOMetadataDecoder metadata;
            GridCoverage2D coverage;
            try {
                metadata = reader.getMetadata();
                coverage = reader.read(null);
            } catch (IOException e) {
                Logging.error("Elevation: Cannot read GeoTIFF file '" + path.toAbsolutePath().toString() + " due to "
                        + e.toString());
                return null;
            }
            try {
                return new GeoTiffImage(path, metadata, coverage);
            } catch (GeoTiffImageException e) {
                Logging.error("Elevation: Cannot create GeoTiffImage for file '" + path.toAbsolutePath().toString()
                        + " due to " + e.toString() + " with cause: " + e.getCause());
                return null;
            }
        } finally {
            reader.dispose();
        }
    }

    /**
     * Reads the referenced envelope from a GeoTIFF file and transforms its to the
     * coordinate reference system {@link #CRS_EPSG_4326}. This file read operation
     * is more lightweight than reading further metadata and grid coverage.
     *
     * @param geoTiffFile The GeoTIFF file from with to read the referenced
     *                    envelope.
     * @return The referenced envelope of the GeoTIFF file.
     */
    public static ReferencedEnvelope readReferencedEnvelopeAsEPSG4326(Path geoTiffFile) {
        GeoTiffReader reader;
        try {
            // GeoTiffReader cannot handle Path, only File
            reader = new GeoTiffReader(geoTiffFile.toFile());
        } catch (DataSourceException e) {
            Logging.error("Elevation: Data source exception attempting to read GeoTIFF data from '"
                    + geoTiffFile.toAbsolutePath().toString() + "': " + e.toString());
            return null;
        }
        try {
            GeneralBounds source = reader.getOriginalEnvelope();
            CoordinateReferenceSystem sourceCrs = reader.getCoordinateReferenceSystem();

            ReferencedEnvelope envelope = new ReferencedEnvelope(source.getMinimum(0), source.getMaximum(0),
                    source.getMinimum(1), source.getMaximum(1), sourceCrs);

            CoordinateReferenceSystem wgs84 = CRS.decode(CRS_EPSG_4326, true);
            return envelope.transform(wgs84, true, 20);
        } catch (FactoryException | TransformException e) {
            Logging.error("Elevation: I/O exception attempting to read coverage from '"
                    + geoTiffFile.toAbsolutePath().toString() + "': " + e.toString());
            return null;
        } finally {
            reader.dispose();
        }
    }
}
