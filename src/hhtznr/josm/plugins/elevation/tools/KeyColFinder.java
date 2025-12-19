package hhtznr.josm.plugins.elevation.tools;

import java.util.BitSet;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
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
    private BitSet active; // true = this cell is "land" (above water)
    private BitSet hasPeakA;
    private BitSet hasPeakB;

    private static final int[][] FOUR_DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private static final int[][] EIGHT_DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 },
            { -1, 1 }, { -1, -1 } };

    /**
     * The number of neighbors of an elevation raster cell to consider in
     * union-find.
     */
    public enum UnionFindNeighbors {
        /**
         * Four neighbors: North, south, east, west.
         */
        FOUR("4 neighbors"),

        /**
         * Eight neighbors: North, south, east, west, northeast, northwest, southeast,
         * southwest.
         */
        EIGHT("8 neighbors");

        private final String name;

        UnionFindNeighbors(String name) {
            this.name = name;
        }

        /**
         * Returns the name associated with the enum item.
         *
         * @return The name.
         */
        @Override
        public String toString() {
            return name;
        }
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
        active = new BitSet(n);
        hasPeakA = new BitSet(n);
        hasPeakB = new BitSet(n);
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
        if (hasPeakA.get(rootB))
            hasPeakA.set(rootA);
        if (hasPeakB.get(rootB))
            hasPeakB.set(rootA);
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
        informListenersAboutStatus("Waiting for SRTM tiles to be cached");
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

        informListenersAboutStatus("Preparing elevation cells for key col search");

        short minElevation = Short.MAX_VALUE;
        short maxElevation = Short.MIN_VALUE;
        int validCount = 0;

        // 1. Find min/max elevation, skipping voids.
        for (int latIndex = 0; latIndex < height; latIndex++) {
            // Check thread interruption occasionally.
            if ((latIndex & 31) == 0 && Thread.currentThread().isInterrupted()) {
                String message = "Interrupted while scanning elevations";
                informListenersAboutStatus(message);
                Logging.info("Elevation: Key col finder: " + message);
                return null;
            }
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                short elevation = tileGrid.getElevation(latIndex, lonIndex);
                // Skip SRTM data voids
                if (elevation == SRTMTile.SRTM_DATA_VOID)
                    continue;
                validCount++;
                if (elevation < minElevation)
                    minElevation = elevation;
                if (elevation > maxElevation)
                    maxElevation = elevation;
            }
        }

        if (validCount == 0) {
            informListenersAboutStatus("No valid elevation data in bounds (all void)");
            Logging.info("Elevation: Key col finder: All elevation data in searc bounds represents data voids");
            return null;
        }
        informListenersAboutStatus("Minimum and maximum elevation determined: " + minElevation + " / " + maxElevation);

        // Compute range. After the earlier 'validCount == 0' check, maxE >= minE is
        // guaranteed so elevationRange must be >= 1. We only need a defensive check for
        // an
        // unexpectedly large range (corrupted tiles or upstream bug). Use 65536 as
        // an upper bound for 16-bit data.
        int elevationRange = (int) maxElevation - (int) minElevation + 1;

        // 2. Build histogram counts, skipping voids
        int[] histogramCounts = new int[elevationRange];
        for (int latIndex = 0; latIndex < height; latIndex++) {
            if ((latIndex & 31) == 0 && Thread.currentThread().isInterrupted()) {
                String message = "Interrupted while building elevation histogram";
                informListenersAboutStatus(message);
                Logging.info("Elevation: Key col finder: " + message);
                return null;
            }
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                short elevation = tileGrid.getElevation(latIndex, lonIndex);
                if (elevation == SRTMTile.SRTM_DATA_VOID)
                    continue;
                histogramCounts[elevation - minElevation]++;
            }
        }
        informListenersAboutStatus("Established " + elevationRange + " elevation histogram counts");

        // 3. Compute start positions for descending order (highest elevation first)
        int[] elevationRangeStartPositions = new int[elevationRange];
        int position = 0;
        for (int bucket = elevationRange - 1; bucket >= 0; bucket--) {
            elevationRangeStartPositions[bucket] = position;
            position += histogramCounts[bucket];
        }
        informListenersAboutStatus("Bucketing of elevation ranges completed");

        // 4. Fill sortedIndices with linear indices of valid cells in descending
        // elevation order
        // elevationRangeStartPositions initially holds bucket starts
        // we reuse it as a write-cursor and advance it while filling
        int[] sortedIndices = new int[position]; // position == validCount
        for (int latIndex = 0; latIndex < height; latIndex++) {
            int latOffset = latIndex * width;
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                short elevation = tileGrid.getElevation(latIndex, lonIndex);
                if (elevation == SRTMTile.SRTM_DATA_VOID)
                    continue;
                // Determine the bucket
                int bucket = elevation - minElevation;
                // Read current write pointer
                int writePosition = elevationRangeStartPositions[bucket];
                // Write the linear index value to the current write pointer
                sortedIndices[writePosition] = latOffset + lonIndex;
                // Advance the write pointer by 1 to obtain the correct position upon next
                // access to this bucket
                elevationRangeStartPositions[bucket] = writePosition + 1;
            }
        }
        informListenersAboutStatus("Cell indices sorted in descending elevation order");

        // 5. Initialize union-find parents
        for (int index = 0; index < parent.length; index++) {
            if ((index & 31) == 0 && Thread.currentThread().isInterrupted()) {
                String message = "Interrupted while initalizing union-find";
                informListenersAboutStatus(message);
                Logging.info("Elevation: Key col finder: " + message);
                throw new InterruptedException(message);
            }
            // Initially, all cells reference to themselves as parent
            parent[index] = index;
        }
        // clear flags
        active.clear();
        hasPeakA.clear();
        hasPeakB.clear();
        informListenersAboutStatus("Union-find structures initialized");

        // 6. Prepare peak indices and search directions
        int[] peakAIndices = tileGrid.getClosestGridRasterIndices(peakA);
        int[] peakBIndices = tileGrid.getClosestGridRasterIndices(peakB);

        // If peaks land on void cells, find nearest non-void cell as a fallback.
        int peakAIndexLinear = peakAIndices[0] * width + peakAIndices[1];
        int peakBIndexLinear = peakBIndices[0] * width + peakBIndices[1];
        if (tileGrid.getElevation(peakAIndices[0], peakAIndices[1]) == SRTMTile.SRTM_DATA_VOID)
            peakAIndexLinear = findNearestNonVoidIndex(tileGrid, peakAIndices[0], peakAIndices[1]);

        if (tileGrid.getElevation(peakBIndices[0], peakBIndices[1]) == SRTMTile.SRTM_DATA_VOID)
            peakBIndexLinear = findNearestNonVoidIndex(tileGrid, peakBIndices[0], peakBIndices[1]);

        if (peakAIndexLinear >= 0)
            hasPeakA.set(peakAIndexLinear);
        if (peakBIndexLinear >= 0)
            hasPeakB.set(peakBIndexLinear);

        // Number of union-find neighbors (4 or 8)
        int[][] directions;
        if (nNeighbors == UnionFindNeighbors.FOUR)
            directions = FOUR_DIRECTIONS;
        else
            directions = EIGHT_DIRECTIONS;

        // 7. Iterate over cells from high to low using sortedIndices (linear indexes)
        informListenersAboutStatus("Iterate over elevation cells to find key col");
        for (int linearIndex : sortedIndices) {
            if ((linearIndex & 31) == 0 && Thread.currentThread().isInterrupted()) {
                String message = "Interrupted while iterating over elevation cells";
                informListenersAboutStatus(message);
                Logging.info("Elevation: Key col finder: " + message);
                throw new InterruptedException(message);
            }
            int latIndex = linearIndex / width;
            int lonIndex = linearIndex - latIndex * width;
            int cellIndex = linearIndex;
            active.set(cellIndex);

            // union with active neighbors
            for (int[] direction : directions) {
                int neighborLatIndex = latIndex + direction[0];
                int neighborLonIndex = lonIndex + direction[1];
                if (inBounds(neighborLatIndex, neighborLonIndex)) {
                    int neighborIndex = neighborLatIndex * width + neighborLonIndex;
                    if (active.get(neighborIndex))
                        union(cellIndex, neighborIndex);
                }
            }

            int root = findParent(cellIndex);
            if (hasPeakA.get(root) && hasPeakB.get(root)) {
                // This elevation is the key col elevation
                LatLonEle keyColLatLonEle = tileGrid.getLatLonEle(latIndex, lonIndex);
                return keyColLatLonEle;
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
     * Find the nearest non-void cell to the given start position using an expanding
     * square-ring search. Returns linear index (row * width + col) or -1 if no
     * non-void cell is found within the raster.
     */
    private int findNearestNonVoidIndex(SRTMTileGrid tileGrid, int latIndex, int lonIndex) {
        if (latIndex < 0 || lonIndex < 0 || latIndex >= height || lonIndex >= width)
            return -1;
        if (tileGrid.getElevation(latIndex, lonIndex) != SRTMTile.SRTM_DATA_VOID)
            return latIndex * width + lonIndex;

        int maxRadius = Math.max(width, height);
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int deltaLat = -radius; deltaLat <= radius; deltaLat++) {
                int deltaLonAbs = radius - Math.abs(deltaLat);
                int candidateLatIndex = latIndex + deltaLat;

                // check the cell at lonIndex + deltaLonAbs
                int candidateLonIndex = lonIndex + deltaLonAbs;
                if (candidateLatIndex >= 0 && candidateLonIndex >= 0 && candidateLatIndex < height
                        && candidateLonIndex < width) {
                    if (tileGrid.getElevation(candidateLatIndex, candidateLonIndex) != SRTMTile.SRTM_DATA_VOID)
                        return candidateLatIndex * width + candidateLonIndex;
                }

                // if deltaLonAbs == 0 the opposite side is the same cell; only check when
                // deltaLonAbs != 0
                if (deltaLonAbs != 0) {
                    candidateLonIndex = lonIndex - deltaLonAbs;
                    if (candidateLatIndex >= 0 && candidateLonIndex >= 0 && candidateLatIndex < height
                            && candidateLonIndex < width) {
                        if (tileGrid.getElevation(candidateLatIndex, candidateLonIndex) != SRTMTile.SRTM_DATA_VOID)
                            return candidateLatIndex * width + candidateLonIndex;
                    }
                }
            }
        }
        return -1;
    }
}
