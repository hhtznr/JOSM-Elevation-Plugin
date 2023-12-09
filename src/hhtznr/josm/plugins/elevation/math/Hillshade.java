package hhtznr.josm.plugins.elevation.math;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * This class implements hill shading.
 *
 * For further information on the algorithm, see <a href=
 * "https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm">ArcGis
 * Pro - How Hillshade works</a>.
 *
 * @author Harald Hetzner
 */
public class Hillshade {

    /**
     * Gray scale value "white".
     */
    public static final int NO_HILLSHADE = 255;

    /**
     * Gray scale value "black".
     */
    public static final int MAX_HILLSHADE = 0;

    private final short[][] eleValues;
    private final LatLon southWest;
    private final LatLon northEast;
    private double zenithRad;
    private double sinZenithRad;
    private double cosZenithRad;
    private double azimuthRad;

    private static final ExecutorService executor;
    static {
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.min(1, cores - 1);
        executor = Executors.newFixedThreadPool(threads);
    }

    /**
     * Creates a new hillshade instance.
     *
     * @param eleValues   The elevation values for which to compute the hillshade.
     * @param southWest   The south west corner of the elevation raster.
     * @param northEast   The north east corner of the elevation raster.
     * @param altitudeDeg The altitude is the angle of the illumination source above
     *                    the horizon. The units are in degrees, from 0 (on the
     *                    horizon) to 90 (overhead).
     * @param azimuthDeg  The azimuth is the angular direction of the sun, measured
     *                    from north in clockwise degrees from 0 to 360.
     */
    public Hillshade(short[][] eleValues, LatLon southWest, LatLon northEast, double altitudeDeg, double azimuthDeg) {
        this.eleValues = eleValues;
        this.southWest = southWest;
        this.northEast = northEast;
        zenithRad = getZenithRad(altitudeDeg);
        // Compute sinus and cosinus of zenith in order not to recompute it
        sinZenithRad = Math.sin(zenithRad);
        cosZenithRad = Math.cos(zenithRad);
        azimuthRad = getAzimuthRad(azimuthDeg);
    }

    /**
     * Computes the illumination angle as used by the algorithm.
     *
     * @param altitudeDeg The illumination angle in degrees.
     * @return The illumination angle in radians.
     */
    private static double getZenithRad(double altitudeDeg) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        return Math.toRadians(90.0 - altitudeDeg);
    }

    /**
     * Computes the illumination direction as used by the algorithm.
     *
     * @param azimuthDeg The illumination direction in degrees.
     * @return The illumination direction in radians.
     */
    private static double getAzimuthRad(double azimuthDeg) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        double azimuthMath = 360.0 - azimuthDeg + 90.0;
        if (azimuthMath >= 360.0)
            azimuthMath -= 360.0;
        return Math.toRadians(azimuthMath);
    }

    /**
     * Computes slope and aspect in radians.
     *
     * @param ele3x3   3 x 3 grid of elevation values, where slope and aspect will
     *                 be computed for the value in the middle.
     * @param cellSize The size of a raster cell, i.e. the distance between two
     *                 neighboring elevation values in arc degrees.
     * @param zFactor  The z-factor, see {@link #getZFactor}.
     * @return A double array of length {@code 2}, where the value at index
     *         {@code 0} is the slope and the value at index {@code 1} is the
     *         aspect.
     */
    private double[] getSlopeAspectRad(short[][] ele3x3, double cellSize, double zFactor) {
        double changeRateInX = getChangeRateInX(ele3x3, cellSize);
        double changeRateInY = getChangeRateInY(ele3x3, cellSize);

        double slope = getSlopeRad(changeRateInX, changeRateInY, zFactor);
        double aspect = getAspectRad(changeRateInX, changeRateInY);

        return new double[] { slope, aspect };
    }

    /**
     * Computes the steepest downhill descent based on the change rates in x and y
     * direction.
     *
     * @param changeRateInX The change rate in x direction ({@code dz/dx}).
     * @param changeRateInY The change rate in y direction ({@code dz/dy}).
     * @param zFactor       The z-factor, see {@link #getZFactor}.
     * @return The steepest slope.
     */
    private static double getSlopeRad(double changeRateInX, double changeRateInY, double zFactor) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        return Math.atan(zFactor * Math.sqrt(changeRateInX * changeRateInX + changeRateInY * changeRateInY));
    }

    /**
     * Computes the direction the steepest downhill descent is facing.
     *
     * @return The direction the steepest slope is facing.
     */
    private double getAspectRad(double changeRateInX, double changeRateInY) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        // dz/dx != 0
        if (changeRateInX != 0.0) {
            double aspectRad = Math.atan2(changeRateInY, -changeRateInX);
            if (aspectRad < 0.0)
                return 2 * Math.PI + aspectRad;
            return aspectRad;
        }
        // dz/dx == 0
        else {
            if (changeRateInY > 0.0)
                return Math.PI / 2.0;
            else if (changeRateInY < 0.0)
                return 2 * Math.PI - Math.PI / 2;
            // We cannot compute the direction of the steepest downhill descent
            // if the slope in x direction and the slope in y direction are both zero
            else
                return azimuthRad;
        }
    }

    /**
     * Computes the change rate in x direction.
     *
     * @param ele3x3   3 x 3 grid of elevation values, where slope and aspect will
     *                 be computed for the value in the middle.
     * @param cellSize The size of a raster cell, i.e. the distance between two
     *                 neighboring elevation values in arc degrees.
     * @return The change rate in x direction ({@code dz/dx}).
     */
    private static double getChangeRateInX(short[][] ele3x3, double cellSize) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        /**
         * <pre>
         * a b c
         * d e f
         * g h i
         * </pre>
         */
        // [dz/dx] = ((c + 2f + i) - (a + 2d + g)) / (8 * cellsize)
        short c = ele3x3[0][2];
        short f = ele3x3[1][2];
        short i = ele3x3[2][2];

        short a = ele3x3[0][0];
        short d = ele3x3[1][0];
        short g = ele3x3[2][0];

        return Double.valueOf((c + 2 * f + i) - (a + 2 * d + g)) / (8 * cellSize);
    }

    /**
     * Computes the change rate in y direction.
     *
     * @param ele3x3   3 x 3 grid of elevation values, where slope and aspect will
     *                 be computed for the value in the middle.
     * @param cellSize The size of a raster cell, i.e. the distance between two
     *                 neighboring elevation values in arc degrees.
     * @return he change rate in y direction ({@code dz/dy}).
     */
    private static double getChangeRateInY(short[][] ele3x3, double cellSize) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        /**
         * <pre>
         * a b c
         * d e f
         * g h i
         * </pre>
         */
        // [dz/dy] = ((g + 2h + i) - (a + 2b + c)) / (8 * cellsize)
        short g = ele3x3[2][0];
        short h = ele3x3[2][1];
        short i = ele3x3[2][2];

        short a = ele3x3[0][0];
        short b = ele3x3[0][1];
        short c = ele3x3[0][2];

        return Double.valueOf((g + 2 * h + i) - (a + 2 * b + c)) / (8 * cellSize);
    }

    /**
     * Computes the z-factor which is applicable within the given bounds.
     *
     * @param southWest The south west corner of the bounds.
     * @param northEast The north east corner of the bounds.
     * @return The z-factor, i.e. the conversion factor from latitude-longitude
     *         (x-y) dimension of arc degrees to (z) elevation dimension of meters.
     */
    public static double getZFactor(ILatLon southWest, ILatLon northEast) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/applying-a-z-factor.htm
        double latSouth = southWest.lat();
        double latNorth = northEast.lat();
        double lonWest = southWest.lon();
        double lonEast = northEast.lon();
        if (lonWest == lonEast)
            throw new IllegalArgumentException(
                    "The z-factor cannot be computed if both longitude values are the same as this would result in a division by zero (longitudes: "
                            + lonWest + "°)");

        double meanLat = (latNorth + latSouth) / 2;
        double lonDistanceInDegrees;
        if (lonWest < lonEast)
            lonDistanceInDegrees = lonEast - lonWest;
        // Across 180th meridian
        else
            lonDistanceInDegrees = 180.0 - lonEast + lonWest + 180.0;

        ILatLon latLon1 = new LatLon(meanLat, lonWest);
        ILatLon latLon2 = new LatLon(meanLat, lonEast);
        double lonDistanceInMeters = latLon1.greatCircleDistance(latLon2);
        // z-factor: 1 meter = x degrees
        // Can be checked against
        // https://thenauticalalmanac.com/Bowditch-%20American%20Practical%20Navigator/TABLE%207-%20LENGTH%20OF%20A%20DEGREE%20OF%20LATITUDE%20AND%20LONGITUDE.pdf
        return lonDistanceInDegrees / lonDistanceInMeters;
    }

    /**
     * Creates a buffered image with the computed hillshade ARGB values for the
     * elevation values of the SRTM tile grid.
     *
     * <b>Note:</b> A hillshade value can only be computed for the central elevation
     * value within a 3 x 3 grid of elevation values. Therefore, if
     * {@code withPerimeter} is set to {@code false} the returned 2D array is
     * {@code 2} values shorter in each dimension than the 2D array of elevation
     * data it was computed for. In each direction, the hillshade values are offset
     * by {@code 1} with respect to the elevation values. Hillshades cannot be
     * computed for the first and last row as well as the first and last column of
     * elevation data. As an example, for the hillshade value located at
     * {@code hillshades[4][6]}, the corresponding elevation value is located at
     * {@code elevations[5][7]}.
     *
     * @param withPerimeter If {@code} true, the a first and last row as well as the
     *                      a first and last column without computed values will be
     *                      added such that the size of the 2D array corresponds to
     *                      that of the input data. If {@code false}, these rows and
     *                      columns will be omitted.
     * @return An image with the computed hillshade values or {@code null} if the
     *         SRTM tile grid cannot deliver elevation values or there are less than
     *         3 elevation values in one of the two dimensions.
     */
    public ImageTile getHillshadeImage(boolean withPerimeter) {
        if (eleValues == null)
            return null;

        int latLength = eleValues.length;
        int lonLength = eleValues[0].length;
        if (latLength < 3 || lonLength < 3)
            return null;

        // Determine the z-factor and the cell size
        final double zFactor = getZFactor(southWest, northEast);
        final double cellSize = (northEast.lat() - southWest.lat()) / (latLength - 1);

        final int perimeterOffset = withPerimeter ? 1 : 0;

        final int width = lonLength + 2 * perimeterOffset;
        int height = latLength + 2 * perimeterOffset;
        // Create a new image with alpha channel  which is black by default (RGB = [0, 0, 0])
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // List of tasks to be executed by the thread executor service
        // Each task will compute one row of the hillshade image
        ArrayList<Callable<BufferedImage>> hillshadeRowTasks = new ArrayList<>(latLength - 2);

        // Iterate over the grid of elevation data processing 3 x 3 subgrids
        // As we copy 3 values in each direction in each iteration, we have to stop 2
        // values before the ends
        for (int latIndex = 0; latIndex < latLength - 2; latIndex++) {
            // Final copy of the current latIndex for internal reference in the task
            int taskLatIndex = latIndex;
            // Create a task for computing each of the rows of the image
            Callable<BufferedImage> task = () -> {
                // Array to collect the alpha values of the pixel row
                int[] pixels = new int[width];
                for (int lonIndex = 0; lonIndex < lonLength - 2; lonIndex++) {
                    // Get 3 x 3 elevation values
                    short[][] ele3x3 = new short[3][3];
                    for (int lat = 0; lat < 3; lat++) {
                        short[] src = eleValues[taskLatIndex];
                        int srcPos = lonIndex;
                        short[] dest = ele3x3[lat];
                        int destPos = 0;
                        int length = 3;
                        System.arraycopy(src, srcPos, dest, destPos, length);
                    }
                    // Compute the hillshade value
                    int hillshade = getHillshadeValue(ele3x3, cellSize, zFactor);
                    // Convert to alpha value, which makes the black image more or less transparent
                    int alpha = 255 - hillshade;
                    // Add the alpha value to the pixel row
                    pixels[lonIndex] = alpha;
                }
                int x = perimeterOffset;
                int y = taskLatIndex + perimeterOffset;
                /*
                 * Write the row of alpha values directly to the image: The alpha values will
                 * leave the image black/opaque (alpha = 255), make it more or less
                 * gray/semi-transparent (alpha = 1...254) or fully transparent (alpha = 0)
                 */
                image.getAlphaRaster().setPixels(x, y, width, 1, pixels);
                // This is just because Callable needs to return some object
                return image;
            };
            // Add the task for the current row to the list of compute tasks
            hillshadeRowTasks.add(task);
        }

        // Submit all tasks to the thread executor and get a list of Futures to
        // synchronize on the tasks
        List<Future<BufferedImage>> futures;
        try {
            futures = executor.invokeAll(hillshadeRowTasks);
        } catch (InterruptedException | RejectedExecutionException e) {
            return null;
        }

        // Iterate over the Futures and wait for each task to complete if it has not
        // completed yet
        for (Future<BufferedImage> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                return null;
            }
        }

        // The shade bounds are 1/2 of the cell size wider in each direction than the
        // raster bounds
        double north;
        double south;
        double west;
        double east;
        if (withPerimeter) {
            // The hillshade bounds are 1/2 of the cell size wider in each direction than
            // the elevation raster bounds
            north = northEast.lat() + cellSize / 2;
            south = southWest.lat() - cellSize / 2;
            west = southWest.lon() - cellSize / 2;
            east = northEast.lon() + cellSize / 2;
            // Correct coordinates if outside of world map
            if (north > 90.0)
                north = 90.0;
            if (south < -90.0)
                south = -90.0;
            if (west < -180.0)
                west = west + 360.0;
            if (east > 180.0)
                east = east - 360.0;
        } else {
            // As above, but accommodate for the perimeter having been skipped
            north = northEast.lat() - cellSize / 2;
            south = southWest.lat() + cellSize / 2;
            west = southWest.lon() + cellSize / 2;
            east = northEast.lon() - cellSize / 2;
        }

        return new ImageTile(image, new LatLon(north, west), new LatLon(south, east));
    }

    /**
     * Computes the hillshade value for the middle value in a 3 x 3 grid of
     * elevation values.
     *
     * @param ele3x3   3 x 3 grid of elevation values, where slope and aspect will
     *                 be computed for the value in the middle.
     * @param cellSize The size of a raster cell, i.e. the distance between two
     *                 neighboring elevation values in arc degrees.
     * @param zFactor  The z-factor, see {@link #getZFactor}.
     * @return The hillshade value in the range {@code [0, 255]}.
     */
    private int getHillshadeValue(short[][] ele3x3, double cellSize, double zFactor) {
        double[] slopeAspect = getSlopeAspectRad(ele3x3, cellSize, zFactor);
        double slope = slopeAspect[0];
        double aspect = slopeAspect[1];
        return getHillshadeValue(slope, aspect);
    }

    /**
     * Computes the corresponding hillshade value for a given slope and aspect.
     *
     * @param slopeRad  The downhill slope.
     * @param aspectRad The direction of the downhill slope.
     * @return The hillshade value which is in the range {@code [0, 255]}. A value
     *         of {@code 0} corresponds to maximum hillshade A value of {@code 255}
     *         corresponds to no hillshade.
     */
    private int getHillshadeValue(double slopeRad, double aspectRad) {
        // https://pro.arcgis.com/en/pro-app/latest/tool-reference/3d-analyst/how-hillshade-works.htm
        double hillshade = cosZenithRad * Math.cos(slopeRad)
                + sinZenithRad * Math.sin(slopeRad) * Math.cos(azimuthRad - aspectRad);

        if (hillshade < 0.0)
            return MAX_HILLSHADE;

        return (int) Math.round(NO_HILLSHADE * hillshade);
    }

    /**
     * Class implementing a hillshade tile which is an image with the hillshade
     * color values and the latitude-longitude coordinates of its edges.
     */
    public static class ImageTile {

        private final BufferedImage image;
        private final LatLon northWest;
        private final LatLon southEast;

        /**
         * Creates a new hillshade image tile.
         *
         * @param image     The image with the hillshade color values.
         * @param northWest The north west corner of the tile in latitude-longitude
         *                  coordinates.
         * @param southEast The south east corner of the tile in latitude-longitude
         *                  coordinates.
         */
        public ImageTile(BufferedImage image, LatLon northWest, LatLon southEast) {
            this.image = image;
            this.northWest = northWest;
            this.southEast = southEast;
        }

        /**
         * Returns the image with the hillshade color values of this tile.
         *
         * @return The image with the hillshade color values.
         */
        public BufferedImage getImage() {
            return image;
        }

        /**
         * Returns the north west corner of the tile in latitude-longitude coordinates
         *
         * @return The north west corner of the tile in latitude-longitude coordinates
         */
        public LatLon getNorthWest() {
            return northWest;
        }

        /**
         * Returns the south east corner of the tile in latitude-longitude coordinates.
         *
         * @return The south east corner of the tile in latitude-longitude coordinates.
         */
        public LatLon getSouthEast() {
            return southEast;
        }
    }
}
