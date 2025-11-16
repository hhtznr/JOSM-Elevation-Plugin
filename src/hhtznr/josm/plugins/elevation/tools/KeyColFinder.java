package hhtznr.josm.plugins.elevation.tools;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

/**
 * The {@code KeyColFinder} class provides functionality to determine the key
 * col (saddle point) between two selected peaks on a the map.
 * <p>
 * <strong>Important:</strong> This tool can be CPU- and memory-intensive,
 * especially for peaks that are far apart. In extreme cases, it may cause an
 * out-of-memory error. It is strongly recommended to save or upload current map
 * work before using this tool.
 * </p>
 * <p>
 * The key col is computed as the lowest point along the highest path connecting
 * two provided nodes. The computed col corresponds to the true
 * <a href="https://en.wikipedia.org/wiki/Key_col">key col</a> only if the user
 * has selected the correct
 * <a href="https://en.wikipedia.org/wiki/Line_parent">line parent</a> as the
 * reference point, or a node along the path toward it beyond the key col.
 * </p>
 * <p>
 * If there is uncertainty about the true line parent, it is recommended to
 * compute the key col for multiple candidate reference points and select the
 * lowest col among them.
 * </p>
 * <p>
 * The key col search does not work across the poles and the 180th meridian. The
 * search bounds will be clipped to prevent this.
 * </p>
 *
 * @author Harald Hetzner
 */
public class KeyColFinder extends AbstractElevationTool {
    private int width, height;
    private int[] parent; // union-find parent array
    private boolean[] active; // true = this cell is "land" (above water)
    private boolean[] hasPeakA;
    private boolean[] hasPeakB;

    private static final int[][] DIRECTIONS_4 = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private static final int[][] DIRECTIONS_8 = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 },
            { -1, 1 }, { -1, -1 } };

    /**
     * The number of neighbors of an elevation raster cell to consider in
     * union-find.
     */
    public enum UnionFindNeighbors {
        /**
         * Four neighbors: North, south, east, west.
         */
        FOUR,

        /**
         * Eight neighbors: North, south, east, west, northeast, northwest, southeast,
         * southwest.
         */
        EIGHT;
    }

    /**
     * Create a new key col finder.
     *
     * @param elevationDataProvider The elevation data provider from which elevation
     *                              data can be obtained.
     */
    public KeyColFinder(ElevationDataProvider elevationDataProvider) {
        super(elevationDataProvider);
    }

    /**
     * (Re)-initializes width, height and the arrays used internally.
     *
     * @param width  The elevation raster width.
     * @param height The elevation raster height.
     */
    private void init(int width, int height) {
        int n = width * height;
        parent = new int[n];
        active = new boolean[n];
        hasPeakA = new boolean[n];
        hasPeakB = new boolean[n];
        this.width = width;
        this.height = height;
    }

    /*
     * Determines the index of the parent cell using union-find with path
     * compression.
     */
    private int findParent(int x) {
        int root = x;
        // Find the root
        while (parent[root] != root) {
            root = parent[root];
        }
        // Path compression
        while (x != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    /**
     * Unions two cells A and B into the same component.
     *
     * @param a Index of cell A
     * @param b Index of cell B.
     */
    private void union(int a, int b) {
        int rootA = findParent(a);
        int rootB = findParent(b);
        if (rootA == rootB)
            return;

        // Merge components
        parent[rootB] = rootA;
        hasPeakA[rootA] |= hasPeakA[rootB];
        hasPeakB[rootA] |= hasPeakB[rootB];
    }

    /**
     * Determines the key col between two peaks. Works regardless of which peak is
     * specified as peakA or peakB. Adjusts the bounds to include both, peakA and
     * peakB, in case that one or both should be out of bounds.
     *
     * @param peakA      The peak for which to determine the key col.
     * @param peakB      The (assumed) line parent of {@code peakA}.
     * @param bounds     The search bounds.
     * @param nNeighbors The number of neighbors of an elevation raster cell to
     *                   consider in union-find, {@link UnionFindNeighbors#FOUR} or
     *                   {@link UnionFindNeighbors#EIGHT}. Eight neighbors results
     *                   in higher accuracy of the location of the key col, but
     *                   means more computation effort.
     * @return The coordinates and elevation of the determined key col, or
     *         {@code null} if no key col could be determined.
     * @throws InterruptedException If the thread executing this method is
     *                              interrupted.
     */
    public LatLonEle findKeyCol(ILatLon peakA, ILatLon peakB, Bounds bounds, UnionFindNeighbors nNeighbors)
            throws InterruptedException {
        informListenersAboutStatus("Key col finder running");
        if (!bounds.contains(peakA) || !bounds.contains(peakB)) {
            double minLat = Math.min(bounds.getMinLat(), Math.min(peakA.lat(), peakB.lat()));
            double minLon = Math.min(bounds.getMinLon(), Math.min(peakA.lon(), peakB.lon()));
            double maxLat = Math.max(bounds.getMaxLat(), Math.max(peakA.lat(), peakB.lat()));
            double maxLon = Math.max(bounds.getMaxLon(), Math.max(peakA.lon(), peakB.lon()));
            double extraSpaceLat = (maxLat - minLat) * 0.2;
            double extraSpaceLon = (maxLon - minLon) * 0.2;
            minLat = Math.max(minLat - extraSpaceLat, -90);
            minLon = Math.max(minLon - extraSpaceLon, 0);
            maxLat = Math.min(maxLat + extraSpaceLat, 90);
            maxLon = Math.min(maxLon + extraSpaceLon, 180);
            bounds = new Bounds(minLat, minLon, maxLat, maxLon);
            Logging.info("Elevation: Key col finder adjusted bounds because it does not contain both peaks: "
                    + bounds.toString());
        }

        SRTMTileGrid tileGrid = new SRTMTileGrid(elevationDataProvider, bounds);
        tileGrid.waitForTilesCached();
        if (Thread.currentThread().isInterrupted()) {
            String message = "Interrupted while waiting for tiles to be cached";
            informListenersAboutStatus(message);
            Logging.info("Elevation: Key col finder: " + message);
            return null;
        }
        informListenersAboutStatus("All needed SRTM tiles cached");

        int width = tileGrid.getRasterWidth();
        int height = tileGrid.getRasterHeight();
        init(width, height);

        informListenersAboutStatus("Preparing cells");

        Cell[] cells = new Cell[width * height];
        IntStream.range(0, height).parallel().forEach(latIndex -> {
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                short ele = tileGrid.getElevation(latIndex, lonIndex);
                cells[latIndex * width + lonIndex] = new Cell(latIndex, lonIndex, ele);
            }
        });

        Arrays.parallelSort(cells, (a, b) -> Short.compare(b.ele, a.ele));

        informListenersAboutStatus("Initialize union-find structures");
        // init union-find
        for (int i = 0; i < parent.length; i++) {
            if (Thread.currentThread().isInterrupted()) {
                String message = "Interrupted while initalizing union-find";
                informListenersAboutStatus(message);
                Logging.info("Elevation: Key col finder: " + message);
                throw new InterruptedException(message);
            }
            parent[i] = i;
            active[i] = false;
            hasPeakA[i] = false;
            hasPeakB[i] = false;
        }

        int[] peakAIndices = tileGrid.getClosestGridRasterIndices(peakA);
        int[] peakBIndices = tileGrid.getClosestGridRasterIndices(peakB);

        hasPeakA[peakAIndices[0] * width + peakAIndices[1]] = true;
        hasPeakB[peakBIndices[0] * width + peakBIndices[1]] = true;

        int[][] directions;
        if (nNeighbors == UnionFindNeighbors.FOUR)
            directions = DIRECTIONS_4;
        else
            directions = DIRECTIONS_8;

        informListenersAboutStatus("Iterate over cells to find key col");
        // iterate from high to low
        for (Cell cell : cells) {
            if (Thread.currentThread().isInterrupted()) {
                String message = "Interrupted while iterating over cells";
                informListenersAboutStatus(message);
                Logging.info("Elevation: Key col finder: " + message);
                throw new InterruptedException(message);
            }
            int cellIndex = cell.latIndex * width + cell.lonIndex;
            active[cellIndex] = true;

            // union with active neighbors
            for (int[] direction : directions) {
                int neighborLatIndex = cell.latIndex + direction[0];
                int neighborLonIndex = cell.lonIndex + direction[1];
                if (inBounds(neighborLatIndex, neighborLonIndex)) {
                    int neighborIndex = neighborLatIndex * width + neighborLonIndex;
                    if (active[neighborIndex])
                        union(cellIndex, neighborIndex);
                }
            }

            int root = findParent(cellIndex);
            if (hasPeakA[root] && hasPeakB[root]) {
                // This elevation is the key col elevation
                LatLonEle keyColLatEle = tileGrid.getLatLonEle(cell.latIndex, cell.lonIndex);
                return keyColLatEle;
            }
        }

        // should never reach here unless disconnected
        return null;
    }

    /**
     * Determines whether the given indices are within the raster bounds.
     *
     * @param latIndex Latitude index.
     * @param lonIndex Longitude index.
     * @return {@code true} if the indices are within the bounds of the raster;
     *         {@code false} otherwise.
     */
    private boolean inBounds(int latIndex, int lonIndex) {
        return (latIndex >= 0 && lonIndex >= 0 && latIndex < height && lonIndex < width);
    }

    /**
     * Represents a single cell in the elevation raster.
     * <p>
     * Each cell stores its row and column indices in the raster grid and its
     * elevation. Used internally by {@link KeyColFinder} to track cells when
     * computing the key col using the union-find algorithm.
     * </p>
     */
    private static class Cell {

        /** The row index (latitude) of the cell in the raster. */
        public final int latIndex;

        /** The column index (longitude) of the cell in the raster. */
        public final int lonIndex;

        /** The elevation of the cell in meters. */
        public final short ele;

        /**
         * Creates a new cell with the specified indices and elevation.
         *
         * @param latIndex The row index (latitude) of the cell.
         * @param lonIndex The column index (longitude) of the cell.
         * @param ele      The elevation of the cell in meters.
         */
        Cell(int latIndex, int lonIndex, short ele) {
            this.latIndex = latIndex;
            this.lonIndex = lonIndex;
            this.ele = ele;
        }
    }
}
