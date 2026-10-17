package hhtznr.josm.plugins.elevation.data;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.OptionalDouble;

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
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * An elevation grid backed by the first band of a GeoTIFF coverage. Grid rows
 * (x direction/longitude direction) are indexed from west to east and grid
 * columns (y direction/latitude direction) are indexed from south to north to
 * match the SRTM elevation grid convention.
 *
 * @author Harald Hetzner
 */
public class GeoTiffElevationGrid implements ElevationGrid {

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
    private final MathTransform geographicToCrs;
    private final MathTransform crsToGrid;

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
            this.crsToGrid = gridToCrs.inverse();
            CoordinateReferenceSystem geographicCrs = CRS.decode("EPSG:4326", true);
            this.crsToGeographic = CRS.findMathTransform(sourceCrs, geographicCrs, true);
            this.geographicToCrs = CRS.findMathTransform(geographicCrs, sourceCrs, true);
        } catch (FactoryException | InvalidGridGeometryException | TransformException e) {
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

    /**
     * Returns whether the provided value is a valid elevation value of this GeoTIFF
     * elevation grid.
     *
     * @param value The value to check.
     * @return {@code true} if the value is finite and the underlying GeoTIFF image
     *         does not list it as no-data value, {@code false} otherwise.
     */
    public boolean isValidElevation(double value) {
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
        return toLatLon((double) latIndex, (double) lonIndex);
    }

    /**
     * Returns the geographic coordinate corresponding to the grid location
     * designated by the specified indices. <br>
     * Note: GeoTIFF raster indices designate pixel centers. For pixel edges, we
     * have to use <code>pixelIndex + 0.5</code> and <code>pixelIndex - 0.5</code>.
     *
     * @param latIndex The latitude index as a {@code double}. It does not need to
     *                 correspond to an integer, but may also be a value between two
     *                 integer indices.
     * @param lonIndex The longitude index as a {@code double}. It does not need to
     *                 correspond to an integer, but may also be a value between two
     *                 integer indices.
     * @return The latitude and longitude corresponding to the specified indices.
     */
    public LatLon toLatLon(double latIndex, double lonIndex) {
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
    public LatLonEle toLatLonEle(int latIndex, int lonIndex) {
        LatLon latLon = toLatLon(latIndex, lonIndex);
        double ele = getElevation(latIndex, lonIndex);
        return new LatLonEle(latLon.lat(), latLon.lon(), ele);
    }

    @Override
    public Bounds getBounds() {
        // Determine enclosing bounds
        // Outline of a GeoTIFF envelope is not necessarily a rectangle
        // Edges are not necessarily straight
        // GeoTIFF raster can rotated or skewed relative to latitude and longitude
        LatLon southWest = toLatLon(0, 0);
        LatLon southEast = toLatLon(0, width - 1);
        LatLon northEast = toLatLon(height - 1, width - 1);
        LatLon northWest = toLatLon(height - 1, 0);
        double minLat = Math.min(Math.min(southWest.lat(), southEast.lat()),
                Math.min(northEast.lat(), northWest.lat()));
        double maxLat = Math.max(Math.max(southWest.lat(), southEast.lat()),
                Math.max(northEast.lat(), northWest.lat()));
        double minLon = Math.min(Math.min(southWest.lon(), southEast.lon()),
                Math.min(northEast.lon(), northWest.lon()));
        double maxLon = Math.max(Math.max(southWest.lon(), southEast.lon()),
                Math.max(northEast.lon(), northWest.lon()));
        return new Bounds(minLat, minLon, maxLat, maxLon);
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

    /**
     * Returns the elevation value at the grid point which is the closest to the
     * provided coordinate. An empty optional double is returned if the grid has no
     * valid elevation value at the coordinate or the coordinate is outside of the
     * grid bounds.
     *
     * @param coordinate The location for which to determine the elevation.
     * @return The optional double with the elevation value at the grid point
     *         closest to the coordinate or an empty optional double if no valid
     *         elevation could be determined.
     * @throws TransformException if the transformation of the coordinate to the
     *                            grid fails, e.g. because its format is invalid.
     */
    public OptionalDouble getElevationAt(ILatLon coordinate) throws TransformException {
        Point indices = getGridIndices(coordinate);
        if (indices == null) {
            return OptionalDouble.empty();
        }
        int latIndex = indices.y;
        int lonIndex = indices.x;
        double elevation = getElevation(latIndex, lonIndex);
        return isNoDataValue(elevation) ? OptionalDouble.empty() : OptionalDouble.of(elevation);
    }

    private Point getGridIndices(ILatLon coordinate) throws TransformException {
        // EPSG:4326 was decoded with longitude-first axis order.
        double[] geographic = { coordinate.lon(), coordinate.lat() };
        double[] source = new double[2];
        double[] grid = new double[2];

        geographicToCrs.transform(geographic, 0, source, 0, 1);
        crsToGrid.transform(source, 0, grid, 0, 1);

        if (!Double.isFinite(grid[0]) || !Double.isFinite(grid[1])) {
            return null;
        }

        // gridToCrs uses PixelOrientation.CENTER, so integer coordinates identify
        // pixel centers. Choose the nearest pixel center.
        long imageX = Math.round(grid[0]);
        long imageY = Math.round(grid[1]);
        long lonIndex = imageX - minX;
        long imageRow = imageY - minY;

        if (lonIndex < 0 || lonIndex >= width || imageRow < 0 || imageRow >= height) {
            return null;
        }

        int latIndex = height - 1 - (int) imageRow;
        return new Point((int) lonIndex, latIndex);
    }

    /**
     * The number of segments to use for each edge of the JOSM bounds when
     * determining the transformed envelope in pixel-center coordinates. A higher
     * number of segments increases the accuracy of the envelope but also increases
     * the computational cost. The default value of 64 provides a good balance
     * between accuracy and performance for most use cases.
     */
    private static final int MAP_EDGE_SEGMENTS = 64;

    /**
     * The number of pixels to expand the intersection envelope when determining the
     * raster cells that may intersect the map bounds. This margin is used to ensure
     * that all relevant raster cells are included in the view, even if they are
     * only partially covered by the map bounds. The default value of 1 pixel is
     * sufficient for most use cases.
     */
    private static final int INTERSECTION_MARGIN_PIXELS = 1;

    /**
     * Returns a view covering the raster cells that may intersect the map bounds.
     *
     * The transformed map-boundary envelope is conservative: the returned view may
     * include cells outside the exact map bounds. Clip rendered contours to the map
     * view, and filter points against the map bounds if they must be strictly
     * within the view.
     *
     * @param bounds The bounds, e.g. the bounds of the map view.
     * @return a candidate raster view, or {@code null} if there is no intersection
     *         or the bounds cross the 180th meridian
     * @throws
     */
    @Override
    public GeoTiffElevationGridView getView(Bounds bounds) throws ElevationGridException {
        if (bounds.crosses180thMeridian()) {
            return null;
        }

        // Envelope of the transformed map boundary in pixel-center coordinates:
        // [minX, maxX, minY, maxY].
        double[] gridEnvelope = { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY };
        // For repeated transformations, reuse temporary arrays to avoid repeated
        // allocation.
        double[] source = new double[2];
        double[] grid = new double[2];

        double minLon = bounds.getMinLon();
        double maxLon = bounds.getMaxLon();
        double minLat = bounds.getMinLat();
        double maxLat = bounds.getMaxLat();

        for (int i = 0; i <= MAP_EDGE_SEGMENTS; i++) {
            double t = (double) i / MAP_EDGE_SEGMENTS;
            double lon = minLon + t * (maxLon - minLon);
            double lat = minLat + t * (maxLat - minLat);

            // Temporary array to store current coordinate in case that exception handling
            // is needed
            double[] latLon = new double[2];
            try {
                // South and north edges.
                latLon[0] = minLat;
                latLon[1] = lon;
                includeGridCoordinateInEnvelope(latLon[1], latLon[0], source, grid, gridEnvelope);
                latLon[0] = maxLat;
                latLon[1] = lon;
                includeGridCoordinateInEnvelope(latLon[1], latLon[0], source, grid, gridEnvelope);

                // West and east edges.
                latLon[0] = lat;
                latLon[1] = minLon;
                includeGridCoordinateInEnvelope(latLon[1], latLon[0], source, grid, gridEnvelope);
                latLon[0] = lat;
                latLon[1] = maxLon;
                includeGridCoordinateInEnvelope(latLon[1], latLon[0], source, grid, gridEnvelope);
            } catch (TransformException e) {
                // thrown if a map-bound coordinate cannot be transformed
                throw new ElevationGridException(
                        "Could not transform coordinate " + new LatLon(latLon[0], latLon[1]) + " to GeoTIFF envelope",
                        e);
            }

        }

        // Pixel centers are integer grid coordinates, so the raster's outer edges
        // are half a pixel beyond its first and last centers.
        double rasterMinX = minX - 0.5;
        double rasterMaxX = minX + width - 0.5;
        double rasterMinY = minY - 0.5;
        double rasterMaxY = minY + height - 0.5;

        if (gridEnvelope[1] < rasterMinX || gridEnvelope[0] > rasterMaxX || gridEnvelope[3] < rasterMinY
                || gridEnvelope[2] > rasterMaxY) {
            return null;
        }

        // Find pixel cells overlapping the transformed envelope. Expand by a
        // pixel for contour calculations that need neighboring samples.
        int colWest = (int) Math.max(0,
                Math.min(width - 1, Math.ceil(gridEnvelope[0] - minX - 0.5) - INTERSECTION_MARGIN_PIXELS));
        int colEast = (int) Math.max(0,
                Math.min(width - 1, Math.floor(gridEnvelope[1] - minX + 0.5) + INTERSECTION_MARGIN_PIXELS));
        int rowNorth = (int) Math.max(0,
                Math.min(height - 1, Math.ceil(gridEnvelope[2] - minY - 0.5) - INTERSECTION_MARGIN_PIXELS));
        int rowSouth = (int) Math.max(0,
                Math.min(height - 1, Math.floor(gridEnvelope[3] - minY + 0.5) + INTERSECTION_MARGIN_PIXELS));

        int latIndexSouth = height - 1 - rowSouth;
        int latIndexNorth = height - 1 - rowNorth;

        return new GeoTiffElevationGridView(this, latIndexSouth, latIndexNorth, colWest, colEast, bounds);
    }

    /**
     * Includes the grid coordinate of the provided geographic coordinate in the
     * provided grid envelope if the coordinate is within the grid bounds and it
     * expands the envelope. The grid envelope is updated in place.
     *
     * @param lon          The longitude of the geographic coordinate.
     * @param lat          The latitude of the geographic coordinate.
     * @param source       An array of length 2 to be used for the grid coordinate.
     *                     Provided to avoid repeated allocation of temporary
     *                     arrays.
     * @param grid         An array of length 2 to be used for the grid coordinate.
     *                     Provided to avoid repeated allocation of temporary
     *                     arrays.
     * @param gridEnvelope The grid envelope to update.
     * @throws TransformException if the transformation of the coordinate to the
     *                            grid fails, e.g. because its format is invalid.
     */
    private void includeGridCoordinateInEnvelope(double lon, double lat, double[] source, double[] grid,
            double[] gridEnvelope) throws TransformException {
        // EPSG:4326 was decoded longitude-first.
        double[] geographic = { lon, lat };
        geographicToCrs.transform(geographic, 0, source, 0, 1);
        crsToGrid.transform(source, 0, grid, 0, 1);

        if (!Double.isFinite(grid[0]) || !Double.isFinite(grid[1])) {
            throw new TransformException("Map bounds produced a non-finite GeoTIFF grid coordinate");
        }

        gridEnvelope[0] = Math.min(gridEnvelope[0], grid[0]);
        gridEnvelope[1] = Math.max(gridEnvelope[1], grid[0]);
        gridEnvelope[2] = Math.min(gridEnvelope[2], grid[1]);
        gridEnvelope[3] = Math.max(gridEnvelope[3], grid[1]);
    }
}
