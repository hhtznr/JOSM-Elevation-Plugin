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
    private final MathTransform gridToCrs;
    private final MathTransform crsToGeographic;

    /**
     * Creates an elevation grid from a GeoTIFF image.
     *
     * @param geoTiffImage source GeoTIFF image
     * @throws GeoTiffImageException if the coverage has no CRS or its coordinate
     *                               transforms cannot be created
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
        Raster tile = getTile(latIndex, lonIndex);
        return tile.getSampleDouble(imageX(lonIndex), imageY(latIndex), 0);
    }

    @Override
    public boolean isNoDataValue(int x, int y) {
        double ele = getElevation(x, y);
        return isNoDataValue(ele);
    }

    private boolean isNoDataValue(double value) {
        if (!Double.isFinite(value)) {
            return true;
        }
        if (noDataValues != null) {
            for (double noDataValue : noDataValues) {
                if (Double.isNaN(noDataValue) ? Double.isNaN(value) : value == noDataValue) {
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
        double minEle = Double.POSITIVE_INFINITY;
        double maxEle = Double.NEGATIVE_INFINITY;
        for (int latIndex = 0; latIndex < height; latIndex++) {
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                double ele = getElevation(latIndex, lonIndex);

                // Ignore data voids in the assessment of minimum elevation
                if (isNoDataValue(ele)) {
                    continue;
                }

                minEle = Math.min(minEle, ele);
                maxEle = Math.max(maxEle, ele);
            }
        }
        // If the area consists of data voids only,
        // minEle will still have its initial value
        if (minEle == Double.POSITIVE_INFINITY) {
            return new short[] {};
        }

        // Apply the lower cutoff elevation value, if it is greater than the minimum
        // elevation within the grid
        minEle = Math.max(minEle, lowerCutoffElevation);
        // Apply the upper cutoff elevation value, if it is smaller than the maximum
        // elevation within the grid
        maxEle = Math.min(maxEle, upperCutoffElevation);

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
        Rectangle imageBounds = new Rectangle(image.getMinX(), image.getMinY(), image.getWidth(), image.getHeight());
        double lowestElevation = Double.POSITIVE_INFINITY;
        double highestElevation = Double.NEGATIVE_INFINITY;

        int minTileX = image.getMinTileX();
        int minTileY = image.getMinTileY();
        for (int tileY = minTileY; tileY < minTileY + image.getNumYTiles(); tileY++) {
            for (int tileX = minTileX; tileX < minTileX + image.getNumXTiles(); tileX++) {
                Raster tile = image.getTile(tileX, tileY);
                Rectangle tileBounds = tile.getBounds().intersection(imageBounds);
                for (int y = tileBounds.y; y < tileBounds.y + tileBounds.height; y++) {
                    for (int x = tileBounds.x; x < tileBounds.x + tileBounds.width; x++) {
                        double value = tile.getSampleDouble(x, y, 0);
                        if (!isValidElevation(value)) {
                            continue;
                        }
                        lowestElevation = Math.min(lowestElevation, value);
                        highestElevation = Math.max(highestElevation, value);
                    }
                }
            }
        }

        if (lowestElevation == Double.POSITIVE_INFINITY) {
            return new double[] { Double.NaN, Double.NaN };
        }
        return new double[] { lowestElevation, highestElevation };
    }

    private Raster getTile(int latIndex, int lonIndex) {
        checkGridCoordinate(latIndex, lonIndex);
        int imageX = imageX(lonIndex);
        int imageY = imageY(latIndex);
        int tileX = Math.floorDiv(imageX - image.getTileGridXOffset(), image.getTileWidth());
        int tileY = Math.floorDiv(imageY - image.getTileGridYOffset(), image.getTileHeight());
        return image.getTile(tileX, tileY);
    }

    private int imageX(int lonIndex) {
        return minX + lonIndex;
    }

    private int imageY(int latIndex) {
        return minY + height - 1 - latIndex;
    }

    private void checkGridCoordinate(int latIndex, int lonIndex) {
        if (lonIndex < 0 || lonIndex >= width || latIndex < 0 || latIndex >= height) {
            throw new IndexOutOfBoundsException("Grid coordinate (latIndex, lonIndex) = (" + latIndex + ", " + lonIndex
                    + ") is outside " + height + " x " + width + " raster");
        }
    }
}
