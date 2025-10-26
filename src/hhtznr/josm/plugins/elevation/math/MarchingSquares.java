package hhtznr.josm.plugins.elevation.math;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;
import hhtznr.josm.plugins.elevation.gui.ContourLines;

/**
 * Implementation of the Marching Squares algorithm to compute contour lines
 * from elevation raster data.
 *
 * @author Harald Hetzner
 */
public class MarchingSquares {

    private final SRTMTileGrid tileGrid;
    private final SRTMTileGrid.RasterIndexBounds rasterIndexBounds;
    private final Bounds bounds;
    private final short[] isovalues;

    private static final ExecutorService executor;
    static {
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.min(1, cores - 1);
        executor = Executors.newFixedThreadPool(threads);
    }

    /**
     * Creates a new instance of the Marching Squares algorithm dedicated to
     * computing contour lines from elevation raster data.
     *
     * @param tileGrid          The grid of SRTM tiles, where elevation values can
     *                          be obtained from its raster.
     * @param bounds            The bounds of the elevation raster data.
     * @param rasterIndexBounds The elevation raster indices of the tile grid, which
     *                          correspond to the bounds.
     * @param isovalues         An array of elevation values defining the isolevels,
     *                          i.e. the elevation values for which contour lines
     *                          should be generated, e.g. {@code { 650, 660, 670,
     *                          680 }}. The isovalues should be greater or equal to
     *                          the minimum value of the elevation raster and less
     *                          or equal to the maximum value of the elevation
     *                          raster. Otherwise, computation effort will be
     *                          wasted.
     */
    public MarchingSquares(SRTMTileGrid tileGrid, Bounds bounds, SRTMTileGrid.RasterIndexBounds rasterIndexBounds,
            short[] isovalues) {
        this.tileGrid = tileGrid;
        this.rasterIndexBounds = rasterIndexBounds;
        this.bounds = bounds;
        this.isovalues = isovalues;
    }

    /**
     * Computes the segments of all isolines for all isovalues based on the Marching
     * Squares algorithm. Starting and end points of the segments are provided in
     * latitude-longitude coordinate space.
     *
     * @return A list containing all computed isoline segments for all isovalues.
     *         The segments are ordered as computed. They are not sorted in a way
     *         that would enable to determine the particular isovalue for which they
     *         were computed or that they could easily be joined.
     */
    public List<ContourLines.IsolineSegments> getIsolineSegments() {
        ArrayList<Callable<ContourLines.IsolineSegments>> isolineTasks = new ArrayList<>(isovalues.length);
        for (int i = 0; i < isovalues.length; i++) {
            short isovalue = isovalues[i];
            Callable<ContourLines.IsolineSegments> task = () -> {
                return getIsolineSegments(isovalue);
            };
            isolineTasks.add(task);
        }

        List<Future<ContourLines.IsolineSegments>> isolineResultList;
        try {
            isolineResultList = executor.invokeAll(isolineTasks);
        } catch (InterruptedException | RejectedExecutionException e) {
            return new ArrayList<>(0);
        }

        ArrayList<ContourLines.IsolineSegments> isolineSegments = new ArrayList<>();
        for (Future<ContourLines.IsolineSegments> isolineResult : isolineResultList) {
            try {
                isolineSegments.add(isolineResult.get());
            } catch (InterruptedException | ExecutionException e) {
                return new ArrayList<>(0);
            }
        }

        return isolineSegments;
    }

    /**
     * Computes the segments of all isolines for the given isovalue based on the
     * Marching Squares algorithm. Starting and end points of the segments are
     * provided in latitude-longitude coordinate space.
     *
     * @param isovalue The isovalue for which to compute the isoline segments.
     * @return A list containing all computed isoline segments. The segments are
     *         ordered as computed. They are not sorted in a way that they could
     *         easily be joined.
     */
    private ContourLines.IsolineSegments getIsolineSegments(short isovalue) {
        ArrayList<LatLonLine> isolineSegments = new ArrayList<>();

        double latRange = bounds.getHeight();
        double lonRange = bounds.getWidth();
        double minLatSouth = bounds.getMinLat();
        double minLonWest = bounds.getMinLon();

        int rasterWidth = rasterIndexBounds.getWidth();
        int rasterHeight = rasterIndexBounds.getHeight();

        // Stores the currently processed row of cells so the northern edges can be
        // reused in the next iteration
        Cell[] previousCellRowToSouth = new Cell[rasterHeight];
        // Iterate through the grid, accessing the data as "square" 2 x 2 cells
        // Therefore, we stop to iterate at index "length - 2" in both dimensions
        for (int latIndex = 0; latIndex < rasterHeight - 1; latIndex++) {
            int gridRasterLatIndex = latIndex + rasterIndexBounds.latIndexSouth;
            double latNorth = minLatSouth + latRange * (double) (latIndex + 1) / (double) (rasterHeight - 1);
            double latSouth = minLatSouth + latRange * (double) latIndex / (double) (rasterHeight - 1);

            // The cell to the south of the currently processed cell
            Cell cellToSouth = previousCellRowToSouth[latIndex];
            // The cell to the west of the currently processed cell
            Cell cellToWest = null;
            for (int lonIndex = 0; lonIndex < rasterWidth - 1; lonIndex++) {
                int gridRasterLonIndex = lonIndex + rasterIndexBounds.lonIndexWest;
                // Get the elevation values and the edge longitudes
                short eleNorthWest;
                short eleNorthEast;
                short eleSouthEast;
                short eleSouthWest;
                double lonWest;
                double lonEast;

                // If there is no previous cell to the west,
                // compute these values from the elevation raster
                if (cellToWest == null) {
                    eleNorthWest = tileGrid.getElevation(gridRasterLatIndex + 1, gridRasterLonIndex);
                    eleSouthWest = tileGrid.getElevation(gridRasterLatIndex, gridRasterLonIndex);
                    lonWest = minLonWest + lonRange * (double) lonIndex / (double) (rasterWidth - 1);
                }
                // If there is a previous cell to the west,
                // copy values of shared edge and vertices instead
                else {
                    eleNorthWest = cellToWest.eleNorthEast;
                    eleSouthWest = cellToWest.eleSouthEast;
                    lonWest = cellToWest.lonEast;
                }

                /**
                 * The elevation value at the northeast corner can never be obtained from a
                 * previous cell.
                 *
                 * <pre>
                 *                           +---+---+ NE
                 * previous cell to the west |   | x |
                 *                           +---+---+---+---+---+---+---+---+---+
                 * previous row to the south |   |   |   |   |   |   |   |   |   |
                 *                           +---+---+---+---+---+---+---+---+---+
                 * </pre>
                 */
                eleNorthEast = tileGrid.getElevation(gridRasterLatIndex + 1, gridRasterLonIndex + 1);

                // Same under consideration of a previous cell to the south
                if (cellToSouth == null) {
                    eleSouthEast = tileGrid.getElevation(gridRasterLatIndex, gridRasterLonIndex + 1);
                    lonEast = minLonWest + lonRange * (double) (lonIndex + 1) / (double) (rasterWidth - 1);
                } else {
                    eleSouthEast = cellToSouth.eleNorthEast;
                    lonEast = cellToSouth.lonEast;
                }

                Cell currentCell = new Cell(latSouth, lonWest, latNorth, lonEast, eleNorthWest, eleNorthEast,
                        eleSouthEast, eleSouthWest);

                // Determine the Marching Squares cell case with respect to the given isoline
                int cellCase = currentCell.getCase(isovalue);

                switch (cellCase) {
                case 0:
                    /**
                     * 0-0-0-0 = 0
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->0 NE
                     *    x    |
                     *    x    v
                     * SW 0<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Cell not intersected by isoline (all vertices below isovalue)
                     */
                    break;
                case 1:
                    /**
                     * 0-0-0-1 = 1
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->0 NE
                     *    x    |
                     *    x    v
                     * SW 1<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from western to southern edge
                     */
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionSouth()));
                    break;
                case 2:
                    /**
                     * 0-0-1-0 = 2
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->0 NE
                     *    x    |
                     *    x    v
                     * SW 0<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from southern to eastern edge
                     */
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    currentCell.setIntersectionEast(isovalue);
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionEast()));
                    break;
                case 3:
                    /**
                     * 0-0-1-1 = 3
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->0 NE
                     *    x    |
                     *    x    v
                     * SW 1<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from western to eastern edge
                     */
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    currentCell.setIntersectionEast(isovalue);
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionEast()));
                    break;
                case 4:
                    /**
                     * 0-1-0-0 = 4
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->1 NE
                     *    x    |
                     *    x    v
                     * SW 0<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from eastern to northern edge
                     */
                    currentCell.setIntersectionEast(isovalue);
                    currentCell.setIntersectionNorth(isovalue);
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionNorth()));
                    break;
                case 5:
                    /**
                     * 0-1-0-1 = 5
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->1 NE
                     *    x    |
                     *    x    v
                     * SW 1<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Saddle point case, two isolines intersect
                     *
                     * Solution 1: 1st isoline intersects from western to northern edge, 2nd isoline
                     * intersects from eastern to southern edge if average cell elevation is above
                     * or equal isovalue
                     *
                     * Solution 2: 1st isoline intersects from southern to western edge, 2nd isoline
                     * intersects from northern to eastern edge if average cell elevation is below
                     * isovalue
                     */
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    currentCell.setIntersectionEast(isovalue);
                    currentCell.setIntersectionNorth(isovalue);
                    if (currentCell.isAverageElevationEqualOrAbove(isovalue)) {
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionNorth()));
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionSouth()));
                    } else {
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionWest()));
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionEast()));
                    }
                    break;
                case 6:
                    /**
                     * 0-1-1-0 = 6
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->1 NE
                     *    x    |
                     *    x    v
                     * SW 0<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from southern to northern edge
                     */
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    currentCell.setIntersectionNorth(isovalue);
                    isolineSegments.add(
                            new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionNorth()));
                    break;
                case 7:
                    /**
                     * 0-1-1-1 = 7
                     *
                     * <pre>
                     *    8    4
                     * NW 0--->1 NE
                     *    x    |
                     *    x    v
                     * SW 1<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from western to northern edge
                     */
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    currentCell.setIntersectionNorth(isovalue);
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionNorth()));
                    break;
                case 8:
                    /**
                     * 1-0-0-0 = 8
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->0 NE
                     *    x    |
                     *    x    v
                     * SW 0<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from northern to western edge
                     */
                    currentCell.setIntersectionNorth(isovalue);
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionWest()));
                    break;
                case 9:
                    /**
                     * 1-0-0-1 = 9
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->0 NE
                     *    x    |
                     *    x    v
                     * SW 1<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from northern to southern edge
                     */
                    currentCell.setIntersectionNorth(isovalue);
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    isolineSegments.add(
                            new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionSouth()));
                    break;
                case 10:
                    /**
                     * 1-0-1-0 = 10
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->0 NE
                     *    x    |
                     *    x    v
                     * SW 0<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Saddle point case, two isolines intersect
                     *
                     * Solution 1: 1st isoline intersects from southern to western edge, 2nd isoline
                     * intersects from northern to eastern edge if average cell elevation is above
                     * or equal isovalue
                     *
                     * Solution 2: 1st isoline intersects from western to northern edge, 2nd isoline
                     * intersects from eastern to southern edge if average cell elevation is below
                     * isovalue
                     */
                    currentCell.setIntersectionNorth(isovalue);
                    currentCell.setIntersectionEast(isovalue);
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    if (currentCell.isAverageElevationEqualOrAbove(isovalue)) {
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionWest()));
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionEast()));
                    } else {
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionNorth()));
                        isolineSegments.add(
                                new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionSouth()));
                    }
                    break;
                case 11:
                    /**
                     * 1-0-1-1 = 11
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->0 NE
                     *    x    |
                     *    x    v
                     * SW 1<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from northern to eastern edge
                     */
                    currentCell.setIntersectionNorth(isovalue);
                    currentCell.setIntersectionEast(isovalue);
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionEast()));
                    break;
                case 12:
                    /**
                     * 1-1-0-0 = 12
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->1 NE
                     *    x    |
                     *    x    v
                     * SW 0<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from eastern to western edge
                     */
                    currentCell.setIntersectionEast(isovalue);
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionWest()));
                    break;
                case 13:
                    /**
                     * 1-1-0-1 = 13
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->1 NE
                     *    x    |
                     *    x    v
                     * SW 1<---0 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from eastern to southern edge
                     */
                    currentCell.setIntersectionEast(isovalue);
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionSouth()));
                    break;
                case 14:
                    /**
                     * 1-1-1-0 = 14
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->1 NE
                     *    x    |
                     *    x    v
                     * SW 0<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Isoline intersects from southern to western edge
                     */
                    if (cellToSouth == null)
                        currentCell.setIntersectionSouth(isovalue);
                    else
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    isolineSegments
                            .add(new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionWest()));
                    break;
                case 15:
                    /**
                     * 1-1-1-1 = 15
                     *
                     * <pre>
                     *    8    4
                     * NW 1--->1 NE
                     *    x    |
                     *    x    v
                     * SW 1<---1 SE
                     *    1    2
                     * </pre>
                     *
                     * Cell not intersected by isoline (all vertices above isovalue)
                     */
                    break;
                default:
                    break;
                }
                // Remember the cell to reuse data
                // when iterating over the next cell row to the north
                previousCellRowToSouth[latIndex] = currentCell;
                // when handling the next cell to the east in the current row
                cellToWest = currentCell;
            }
            cellToWest = null;
        }
        return new ContourLines.IsolineSegments(isovalue, isolineSegments);
    }

    /**
     * Helper subclass to store the coordinate values of the four vertices of a
     * "square" cell that is being processed by the algorithm along with
     * interpolated edge intersection coordinate values.
     */
    private static class Cell {
        /**
         * Bit value used to indicate that a cell vertex is equal to or above an
         * isovalue.
         */
        private static final byte ABOVE = 0x1;

        /**
         * Bit value used to indicate that a cell vertex is below an isovalue.
         */
        private static final byte BELOW = 0x0;

        public final double latSouth;
        public final double lonWest;
        public final double latNorth;
        public final double lonEast;
        public final short eleNorthWest;
        public final short eleNorthEast;
        public final short eleSouthEast;
        public final short eleSouthWest;

        public IntersectedEdge edgeNorth = null;
        public IntersectedEdge edgeEast = null;
        public IntersectedEdge edgeSouth = null;
        public IntersectedEdge edgeWest = null;

        public Cell(double latSouth, double lonWest, double latNorth, double lonEast, short eleNorthWest,
                short eleNorthEast, short eleSouthEast, short eleSouthWest) {
            this.latSouth = latSouth;
            this.lonWest = lonWest;
            this.latNorth = latNorth;
            this.lonEast = lonEast;

            this.eleNorthWest = eleNorthWest;
            this.eleNorthEast = eleNorthEast;
            this.eleSouthEast = eleSouthEast;
            this.eleSouthWest = eleSouthWest;
        }

        /**
         * Determines the Marching Squares cell case.
         *
         * @param isovalue The isovalue for which to determine the Marching Squares cell
         *                 case.
         * @return The integer code in the range {@code 0...15} which is associated with
         *         the appropriate Marching Squares Cell case.
         */
        public int getCase(short isovalue) {
            byte[] binaryVertexStates = getBinaryVertexStates(isovalue);
            int caseCode = 0;
            for (int i = 0; i < binaryVertexStates.length; i++)
                caseCode = (caseCode << 1) | binaryVertexStates[i];
            return caseCode;
        }

        /**
         * Determines whether the average elevation of the cell is above (or equal) or
         * below a given isovalue.
         *
         * @param isovalue The isovalue for which to decide whether the average
         *                 elevation of the cell is above (or equal) or below.
         * @return {@code true} if the arithmetic average elevation of the cell is
         *         greater or equal to the given isovalue, {@code false} otherwise.
         */
        public boolean isAverageElevationEqualOrAbove(short isovalue) {
            double averageElevation = getAverageElevation();
            return averageElevation >= isovalue;
        }

        /**
         * Computes the average elevation of the cell.
         *
         * @return The arithmetic average elevation of the cell.
         */
        private double getAverageElevation() {
            return 0.25 * (eleNorthWest + eleNorthEast + eleSouthEast + eleSouthWest);
        }

        /**
         * Determines and returns the binary vertex states for the given isovalue. The
         * cell is traversed clockwise:
         *
         * <pre>
         * {@code
         *    8    4
         * NW o--->o NE
         *         |
         *         v
         * SW o<---o SE
         *    1    2
         * }
         * </pre>
         *
         * @param isovalue The isovalue for which to determine the binary states
         *                 ({@code ABOVE} or {@code BELOW} of the elevation values at
         *                 the cell's vertices.
         * @return An array of {@code length = 4} which contains the binary vertex
         *         states in order {@code {NW, NE, SE, SW}}.
         */
        private byte[] getBinaryVertexStates(short isovalue) {
            byte[] binaryVertexStates = new byte[4];
            binaryVertexStates[0] = getVertexState(eleNorthWest, isovalue);
            binaryVertexStates[1] = getVertexState(eleNorthEast, isovalue);
            binaryVertexStates[2] = getVertexState(eleSouthEast, isovalue);
            binaryVertexStates[3] = getVertexState(eleSouthWest, isovalue);
            return binaryVertexStates;
        }

        /**
         * Determines the binary state of a cell vertex with respect to a given
         * isovalue, i.e. if the vertex is equal to or above the isovalue or below the
         * isovalue.
         *
         * @param value    The value at the vertex.
         * @param isovalue The isovalue.
         * @return {@link ABOVE} if the value is equal to or above the isovalue.
         *         {@link BELOW} if the value is below the isovalue.
         */
        private static byte getVertexState(short value, short isovalue) {
            return value >= isovalue ? ABOVE : BELOW;
        }

        /**
         * Computes the longitude coordinate of the intersection point of the isoline
         * with the northern cell edge by linear interpolation.
         *
         * @param isovalue The isovalue of the isoline.
         */
        public void setIntersectionNorth(short isovalue) {
            edgeNorth = new IntersectedEdge(interpolateCoord(lonWest, lonEast, eleNorthWest, eleNorthEast, isovalue));
        }

        /**
         * Computes the latitude coordinate of the intersection point of the isoline
         * with the eastern cell edge by linear interpolation.
         *
         * @param isovalue The isovalue of the isoline.
         */
        public void setIntersectionEast(short isovalue) {
            edgeEast = new IntersectedEdge(interpolateCoord(latNorth, latSouth, eleNorthEast, eleSouthEast, isovalue));
        }

        /**
         * Computes the longitude coordinate of the intersection point of the isoline
         * with the southern cell edge by linear interpolation.
         *
         * @param isovalue The isovalue of the isoline.
         */
        public void setIntersectionSouth(short isovalue) {
            edgeSouth = new IntersectedEdge(interpolateCoord(lonWest, lonEast, eleSouthWest, eleSouthEast, isovalue));
        }

        /**
         * Computes the latitude coordinate of the intersection point of the isoline
         * with the western cell edge by linear interpolation.
         *
         * @param isovalue The isovalue of the isoline.
         */
        public void setIntersectionWest(short isovalue) {
            edgeWest = new IntersectedEdge(interpolateCoord(latNorth, latSouth, eleNorthWest, eleSouthWest, isovalue));
        }

        /**
         * Returns the coordinate of the previously computed isoline intersection point
         * with the northern edge.
         *
         * @return The coordinate of the intersection point or {@code null} if the edge
         *         is not intersected by an isoline.
         */
        public LatLon getIntersectionNorth() {
            if (edgeNorth == null)
                return null;
            return new LatLon(latNorth, edgeNorth.intersection);
        }

        /**
         * Returns the coordinate of the previously computed isoline intersection point
         * with the eastern edge.
         *
         * @return The coordinate of the intersection point or {@code null} if the edge
         *         is not intersected by an isoline.
         */
        public LatLon getIntersectionEast() {
            if (edgeEast == null)
                return null;
            return new LatLon(edgeEast.intersection, lonEast);
        }

        /**
         * Returns the coordinate of the previously computed isoline intersection point
         * with the southern edge.
         *
         * @return The coordinate of the intersection point or {@code null} if the edge
         *         is not intersected by an isoline.
         */
        public LatLon getIntersectionSouth() {
            if (edgeSouth == null)
                return null;
            return new LatLon(latSouth, edgeSouth.intersection);
        }

        /**
         * Returns the coordinate of the previously computed isoline intersection point
         * with the western edge.
         *
         * @return The coordinate of the intersection point or {@code null} if the edge
         *         is not intersected by an isoline.
         */
        public LatLon getIntersectionWest() {
            if (edgeWest == null)
                return null;
            return new LatLon(edgeWest.intersection, lonWest);
        }

        private static double interpolateCoord(double coord1, double coord2, short ele1, short ele2, short ele) {
            if (ele1 < ele2)
                return LinearInterpolation.interpolate(ele1, ele2, coord1, coord2, ele);
            return LinearInterpolation.interpolate(ele2, ele1, coord2, coord1, ele);
        }
    }

    /**
     * Helper subclass representing a cell edge which is intersected by an isoline.
     */
    private static class IntersectedEdge {
        public final double intersection;

        /**
         * Creates an intersected edge.
         *
         * @param intersection The coordinate in edge direction where the edge is
         *                     intersected by an isoline.
         */
        public IntersectedEdge(double intersection) {
            this.intersection = intersection;
        }
    }
}
