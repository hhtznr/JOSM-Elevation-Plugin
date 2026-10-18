package hhtznr.josm.plugins.elevation.math;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.concurrent.ParallelExecutorService;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonLine;
import hhtznr.josm.plugins.elevation.gui.paintables.ContourLines;

/**
 * Implementation of the Marching Squares algorithm to compute contour lines
 * from elevation raster data.
 *
 * @author Harald Hetzner
 */
public class MarchingSquares {

    private final ElevationGridView elevationGridView;
    private final short[] isovalues;
    private final double[] edgeLatitudes;
    private final double[] edgeLongitudes;

    /**
     * Creates a new instance of the Marching Squares algorithm dedicated to
     * computing contour lines from elevation raster data.
     *
     * @param elevationGridView The view on an elevation grid from which elevation
     *                          data should be obtained.
     * @param isovalues         An array of elevation values defining the isolevels,
     *                          i.e. the elevation values for which contour lines
     *                          should be generated, e.g.
     *                          {@code { 650, 660, 670, 680 }}. The isovalues should
     *                          be greater or equal to the minimum value of the
     *                          elevation raster and less or equal to the maximum
     *                          value of the elevation raster. Otherwise,
     *                          computation effort will be wasted.
     */
    public MarchingSquares(ElevationGridView elevationGridView, short[] isovalues) {
        this.elevationGridView = elevationGridView;
        this.isovalues = isovalues;
        int gridWidth = elevationGridView.getWidth();
        int gridHeight = elevationGridView.getHeight();
        int gridSize = Math.multiplyExact(gridWidth, gridHeight);
        edgeLatitudes = new double[gridSize];
        edgeLongitudes = new double[gridSize];
        for (int latIndex = 0; latIndex < gridHeight; latIndex++) {
            for (int lonIndex = 0; lonIndex < gridWidth; lonIndex++) {
                int linearIndex = latIndex * gridWidth + lonIndex;
                LatLon latLon = elevationGridView.toLatLon(latIndex, lonIndex);
                edgeLatitudes[linearIndex] = latLon.lat();
                edgeLongitudes[linearIndex] = latLon.lon();
            }
        }
    }

    private LatLon getLatLon(int latIndex, int lonIndex) {
        int gridWidth = elevationGridView.getWidth();
        int linearIndex = latIndex * gridWidth + lonIndex;
        double lat = edgeLatitudes[linearIndex];
        double lon = edgeLongitudes[linearIndex];
        return new LatLon(lat, lon);
    }

    /**
     * Returns the array of isoline segments for the isovalues.
     *
     * @return The isoline segments.
     * @throws AsyncOperationException If the computation of the isoline segments is
     *                                 interrupted or fails for any reason.
     */
    public ContourLines.IsolineSegments[] getIsolineSegments() throws AsyncOperationException {
        List<Callable<ContourLines.IsolineSegments>> tasks = new ArrayList<>(isovalues.length);
        for (short isovalue : isovalues) {
            tasks.add(() -> getIsolineSegments(isovalue));
        }

        ParallelExecutorService executor = ElevationPlugin.getParallelExecutor();
        List<Future<ContourLines.IsolineSegments>> futures = executor.submit(tasks);
        ContourLines.IsolineSegments[] isolineSegments = new ContourLines.IsolineSegments[futures.size()];

        try {
            for (int i = 0; i < futures.size(); i++) {
                isolineSegments[i] = futures.get(i).get();
            }
        } catch (InterruptedException e) {
            futures.forEach(future -> future.cancel(true));
            Thread.currentThread().interrupt();
            throw new AsyncOperationException(e);
        } catch (ExecutionException | CancellationException e) {
            futures.forEach(future -> future.cancel(true));
            throw new AsyncOperationException(e instanceof ExecutionException ? e.getCause() : e);
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
     * @throws InterruptedException If the computation is interrupted by another thread.
     */
    private ContourLines.IsolineSegments getIsolineSegments(short isovalue) throws InterruptedException{
        if (Thread.currentThread().isInterrupted()) {
           throw new InterruptedException("Isoline computation interrupted");
        }

        ArrayList<LatLonLine> isolineSegments = new ArrayList<>();

        int rasterWidth = elevationGridView.getWidth();
        int rasterHeight = elevationGridView.getHeight();

        // Stores the currently processed row of cells so the northern edges can be
        // reused in the next iteration
        Cell[] previousCellRowToSouth = new Cell[rasterWidth];
        // Iterate through the grid, accessing the data as "square" 2 x 2 cells
        // Therefore, we stop to iterate at index "length - 2" in both dimensions
        for (int latIndex = 0; latIndex < rasterHeight - 1; latIndex++) {
            // The cell to the west of the currently processed cell
            Cell cellToWest = null;
            for (int lonIndex = 0; lonIndex < rasterWidth - 1; lonIndex++) {
                // The cell to the south of the currently processed cell
                Cell cellToSouth = previousCellRowToSouth[lonIndex];
                // Get the elevation values and the edge coordinates
                double eleNorthWest;
                double eleNorthEast;
                double eleSouthEast;
                double eleSouthWest;

                LatLon northWest = getLatLon(latIndex + 1, lonIndex);
                LatLon southEast = getLatLon(latIndex, lonIndex + 1);
                double latNorth = northWest.lat();
                double lonWest = northWest.lon();
                double latSouth = southEast.lat();
                double lonEast = southEast.lon();

                // If there is no previous cell to the west,
                // compute these values from the elevation raster
                if (cellToWest == null) {
                    eleNorthWest = elevationGridView.getElevation(latIndex + 1, lonIndex);
                    eleSouthWest = elevationGridView.getElevation(latIndex, lonIndex);
                }
                // If there is a previous cell to the west,
                // copy values of shared edge and vertices instead
                else {
                    eleNorthWest = cellToWest.eleNorthEast;
                    eleSouthWest = cellToWest.eleSouthEast;
                }

                if (elevationGridView.isNoDataValue(eleNorthWest) || elevationGridView.isNoDataValue(eleSouthWest)) {
                    previousCellRowToSouth[lonIndex] = null;
                    cellToWest = null;
                    continue;
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
                eleNorthEast = elevationGridView.getElevation(latIndex + 1, lonIndex + 1);
                if (elevationGridView.isNoDataValue(eleNorthEast)) {
                    previousCellRowToSouth[lonIndex] = null;
                    cellToWest = null;
                    continue;
                }

                // Same under consideration of a previous cell to the south
                if (cellToSouth == null) {
                    eleSouthEast = elevationGridView.getElevation(latIndex, lonIndex + 1);
                } else {
                    eleSouthEast = cellToSouth.eleNorthEast;
                }

                if (elevationGridView.isNoDataValue(eleSouthEast)) {
                    previousCellRowToSouth[lonIndex] = null;
                    cellToWest = null;
                    continue;
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
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
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
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
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
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
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
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
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
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
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
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
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
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
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
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
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
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
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
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
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
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
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
                    if (cellToSouth == null) {
                        currentCell.setIntersectionSouth(isovalue);
                    } else {
                        currentCell.edgeSouth = cellToSouth.edgeNorth;
                    }
                    if (cellToWest == null) {
                        currentCell.setIntersectionWest(isovalue);
                    } else {
                        currentCell.edgeWest = cellToWest.edgeEast;
                    }
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
                previousCellRowToSouth[lonIndex] = currentCell;
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
        public final double eleNorthWest;
        public final double eleNorthEast;
        public final double eleSouthEast;
        public final double eleSouthWest;

        public IntersectedEdge edgeNorth = null;
        public IntersectedEdge edgeEast = null;
        public IntersectedEdge edgeSouth = null;
        public IntersectedEdge edgeWest = null;

        public Cell(double latSouth, double lonWest, double latNorth, double lonEast, double eleNorthWest,
                double eleNorthEast, double eleSouthEast, double eleSouthWest) {
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
            for (int i = 0; i < binaryVertexStates.length; i++) {
                caseCode = (caseCode << 1) | binaryVertexStates[i];
            }
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
        private static byte getVertexState(double value, short isovalue) {
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
            if (edgeNorth == null) {
                return null;
            }
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
            if (edgeEast == null) {
                return null;
            }
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
            if (edgeSouth == null) {
                return null;
            }
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
            if (edgeWest == null) {
                return null;
            }
            return new LatLon(edgeWest.intersection, lonWest);
        }

        private static double interpolateCoord(double coord1, double coord2, double ele1, double ele2, double ele) {
            if (ele1 < ele2) {
                return LinearInterpolation.interpolate(ele1, ele2, coord1, coord2, ele);
            }
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
