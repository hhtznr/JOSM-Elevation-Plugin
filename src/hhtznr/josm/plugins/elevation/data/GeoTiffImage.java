package hhtznr.josm.plugins.elevation.data;

import java.awt.image.RenderedImage;
import java.io.File;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.openstreetmap.josm.data.Bounds;

/**
 * This class implements a wrapper around the GeoTools objects and methods
 * representing GeoTIFF data.
 *
 * @author Harald Hetzner
 */
public class GeoTiffImage implements ElevationGrid {
    private final File file;
    private final GridCoverage2D coverage;
    private final ReferencedEnvelope referencedEnvelope;
    private final int width;
    private final int height;

    /**
     * Creates a new GeoTIFF image.
     *
     * @param file     A reference to the GeoTIFF file.
     * @param coverage The grid coverage of the GeoTIFF.
     * @throws GeoTiffImageException If the GeoTIFF file cannot be displayed due to
     *                               missing coordinate reference system or
     *                               transformation issues.
     */
    public GeoTiffImage(File file, GridCoverage2D coverage) throws GeoTiffImageException {
        this.file = file;
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
     * representation {@code ReferencedEnvelope).
     *
     * @return The geographic bounds of the GeoTIFF image.
     */
    public ReferencedEnvelope getReferencedEnvelope() {
        return referencedEnvelope;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    /**
     * Returns the geographic bounds of the GeoTIFF image in JOSM's representation
     * {@code Bounds}.
     *
     * @return The geographic bounds of the GeoTIFF image.
     */
    @Override
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
}
