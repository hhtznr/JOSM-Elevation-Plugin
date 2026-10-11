package hhtznr.josm.plugins.elevation.data;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;

import org.geotools.api.metadata.spatial.PixelOrientation;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.InvalidGridGeometryException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * An elevation grid backed by the first band of a GeoTIFF coverage. Grid rows
 * (x direction/longitude direction) are indexed from west to east and grid
 * columns (y direction/latitude direction) are indexed from south to north to
 * match the SRTM elevation grid convention.
 *
 * @author Harald Hetzner
 */
public class GeoTiffElevationGrid implements ElevationGridView {

    private final GeoTiffImage geoTiffImage;
    private final RenderedImage image;
    private final int minX;
    private final int minY;
    private final int width;
    private final int height;
    private final double[] noDataValues;
    private final double[] elevations;
    private final double lowestElevation;
    private final double highestElevation;
    private final MathTransform gridToCrs;
    private final MathTransform crsToGeographic;

    /**
     * Creates an in-memory elevation grid from a GeoTIFF image, caching its samples
     * and valid elevation range for repeated access.
     *
     * @param geoTiffImage source GeoTIFF image
     * @throws GeoTiffImageException if the coverage has no coordinate reference
     *                               system (CRS), its coordinate transforms cannot
     *                               be created, or its samples cannot be
     *                               represented by a Java array
     */
    public GeoTiffElevationGrid(GeoTiffImage geoTiffImage) throws GeoTiffImageException {
        this.geoTiffImage = geoTiffImage;
        GridCoverage2D coverage = geoTiffImage.getCoverage();
        this.image = coverage.getRenderedImage();
        this.minX = image.getMinX();
        this.minY = image.getMinY();
        this.width = image.getWidth();
        this.height = image.getHeight();
        double[] coverageNoDataValues = coverage.getSampleDimension(0).getNoDataValues();
        this.noDataValues = coverageNoDataValues == null ? null : coverageNoDataValues.clone();

        ReferencedEnvelope envelope = new ReferencedEnvelope(coverage.getEnvelope2D());
        CoordinateReferenceSystem sourceCrs = envelope.getCoordinateReferenceSystem();
        if (sourceCrs == null) {
            throw new GeoTiffImageException("GeoTIFF file '" + geoTiffImage.getFile().getAbsolutePath()
                    + "' has no coordinate reference system");
        }

        try {
            this.gridToCrs = coverage.getGridGeometry().getGridToCRS2D(PixelOrientation.CENTER);
            CoordinateReferenceSystem geographicCrs = CRS.decode("EPSG:4326", true);
            this.crsToGeographic = CRS.findMathTransform(sourceCrs, geographicCrs, true);
        } catch (FactoryException | InvalidGridGeometryException e) {
            throw new GeoTiffImageException("Cannot create coordinate transforms for GeoTIFF file '"
                    + geoTiffImage.getFile().getAbsolutePath() + "'", e);
        }

        int sampleCount;
        try {
            sampleCount = Math.multiplyExact(width, height);
        } catch (ArithmeticException e) {
            throw new GeoTiffImageException("GeoTIFF raster is too large to cache in memory: '"
                    + geoTiffImage.getFile().getAbsolutePath() + "'", e);
        }
        this.elevations = new double[sampleCount];
        double minElevation = Double.POSITIVE_INFINITY;
        double maxElevation = Double.NEGATIVE_INFINITY;
        Rectangle imageBounds = new Rectangle(minX, minY, width, height);
        int minTileX = image.getMinTileX();
        int minTileY = image.getMinTileY();
        for (int tileY = minTileY; tileY < minTileY + image.getNumYTiles(); tileY++) {
            for (int tileX = minTileX; tileX < minTileX + image.getNumXTiles(); tileX++) {
                Raster tile = image.getTile(tileX, tileY);
                Rectangle tileBounds = tile.getBounds().intersection(imageBounds);
                for (int y = tileBounds.y; y < tileBounds.y + tileBounds.height; y++) {
                    int latIndex = height - 1 - (y - minY);
                    for (int x = tileBounds.x; x < tileBounds.x + tileBounds.width; x++) {
                        int lonIndex = x - minX;
                        double elevation = tile.getSampleDouble(x, y, 0);
                        elevations[latIndex * width + lonIndex] = elevation;
                        if (isValidElevation(elevation)) {
                            minElevation = Math.min(minElevation, elevation);
                            maxElevation = Math.max(maxElevation, elevation);
                        }
                    }
                }
            }
        }
        this.lowestElevation = minElevation;
        this.highestElevation = maxElevation;
    }

    public GeoTiffImage getGeoTiffImage() {
        return geoTiffImage;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public double getElevation(int latIndex, int lonIndex) {
        return elevations[linearIndex(latIndex, lonIndex)];
    }

    @Override
    public boolean isNoDataValue(int latIndex, int lonIndex) {
        return isNoDataValue(elevations[linearIndex(latIndex, lonIndex)]);
    }

    @Override
    public boolean isNoDataValue(double ele) {
        if (!Double.isFinite(ele)) {
            return true;
        }
        if (noDataValues != null) {
            for (double noDataValue : noDataValues) {
                if (Double.isNaN(noDataValue) ? Double.isNaN(ele) : ele == noDataValue) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the provided value is a valid elevation value of this GeoTIFF
     * elevation grid.
     *
     * @param value The value to check.
     * @return {@code true} if the value is finite and the underlying GeoTIFF image
     *         does not list it as no-data value, {@code false} otherwise.
     */
    private boolean isValidElevation(double value) {
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
    public LatLon toLatLon(int latIndex, int lonIndex) {
        double[] gridPoint = { minX + lonIndex, minY + height - 1 - latIndex };
        double[] crsPoint = new double[2];
        double[] geographicPoint = new double[2];
        try {
            gridToCrs.transform(gridPoint, 0, crsPoint, 0, 1);
            crsToGeographic.transform(crsPoint, 0, geographicPoint, 0, 1);
        } catch (TransformException e) {
            throw new IllegalStateException("Cannot transform GeoTIFF grid coordinate to latitude/longitude", e);
        }
        return new LatLon(geographicPoint[1], geographicPoint[0]);
    }

    @Override
    public Bounds getBounds() {
        LatLon minLatLon = toLatLon(0, 0);
        LatLon maxLatLon = toLatLon(height - 1, width - 1);
        return new Bounds(minLatLon, maxLatLon);
    }

    @Override
    public short[] getIsovalues(int isostep, int lowerCutoffElevation, int upperCutoffElevation) {
        // If the area consists of data voids only,
        // minEle will still have its initial value
        if (lowestElevation == Double.POSITIVE_INFINITY) {
            return new short[] {};
        }

        // Apply the lower cutoff elevation value, if it is greater than the minimum
        // elevation within the grid
        double minEle = Math.max(lowestElevation, lowerCutoffElevation);
        // Apply the upper cutoff elevation value, if it is smaller than the maximum
        // elevation within the grid
        double maxEle = Math.min(highestElevation, upperCutoffElevation);

        // Determine the list of isovalues, i.e. the elevation levels for which contour
        // lines should be computed
        short minIsovalue;
        short maxIsovalue;
        if ((short) minEle % isostep == 0) {
            minIsovalue = (short) minEle;
        } else {
            minIsovalue = (short) (((short) minEle / isostep) * isostep);
        }
        if ((short) maxEle % isostep == 0) {
            maxIsovalue = (short) maxEle;
        } else {
            maxIsovalue = (short) (((short) maxEle / isostep) * isostep);
        }
        if (maxIsovalue < minIsovalue) {
            return new short[] {};
        }
        int nSteps = (maxIsovalue - minIsovalue) / isostep + 1;
        short[] isovalues = new short[nSteps];
        for (int i = 0; i < nSteps; i++) {
            isovalues[i] = (short) (minIsovalue + i * isostep);
        }

        return isovalues;
    }

    /**
     * Returns the minimum and maximum valid values in the first coverage band.
     *
     * @return an array containing the minimum at index {@code 0} and maximum at
     *         index {@code 1}, or {@code {Double.NaN, Double.NaN}} if the raster
     *         contains no valid samples
     */
    public double[] getLowestAndHighestElevation() {
        if (lowestElevation == Double.POSITIVE_INFINITY) {
            return new double[] { Double.NaN, Double.NaN };
        }
        return new double[] { lowestElevation, highestElevation };
    }

    private int linearIndex(int latIndex, int lonIndex) {
        checkGridCoordinate(latIndex, lonIndex);
        return latIndex * width + lonIndex;
    }

    private void checkGridCoordinate(int latIndex, int lonIndex) {
        if (lonIndex < 0 || lonIndex >= width || latIndex < 0 || latIndex >= height) {
            throw new IndexOutOfBoundsException("Grid coordinate (latIndex, lonIndex) = (" + latIndex + ", " + lonIndex
                    + ") is outside " + height + " x " + width + " raster");
        }
    }
}
