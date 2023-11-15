package hhtznr.josm.plugins.elevation.math;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.data.LatLonLine;

/**
 * Naive implementation of the Marching Squares algorithm to compute contour
 * lines from elevation raster data.
 *
 * @author Harald Hetzner
 */
public class MarchingSquares {

    private final short[][] eleValues;
    private final LatLon southWest;
    private final LatLon northEast;
    private final short[] isovalues;

    /**
     * Creates a new instance of the Marching Squares algorithm dedicated to
     * computing contour lines from elevation raster data.
     *
     * @param eleValues The elevation raster data, e.g. obtained from SRTM tiles.
     * @param southWest The coordinates of the south west corner of the elevation
     *                  raster data.
     * @param northEast The coordinates of the north east corner of the elevation
     *                  raster data.
     * @param isovalues An array of elevation values defining the isolevels, i.e.
     *                  the elevation values for which contour lines should be
     *                  generated, e.g. {@code { 650, 660, 670, 680 }}. The
     *                  isovalues should greater or equal to the minimum value of
     *                  the elevation raster and less or equal to the maximum value
     *                  of the elevation raster. Otherwise, computation effort will
     *                  be wasted.
     */
    public MarchingSquares(short[][] eleValues, LatLon southWest, LatLon northEast, short[] isovalues) {
        this.eleValues = eleValues;
        this.southWest = southWest;
        this.northEast = northEast;
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
    public List<LatLonLine> getIsolineSegments() {
        ArrayList<LatLonLine> isolineSegments = new ArrayList<>();
        for (short isovalue : isovalues)
            isolineSegments.addAll(getIsolineSegments(isovalue));
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
    private List<LatLonLine> getIsolineSegments(short isovalue) {
        ArrayList<LatLonLine> isolineSegments = new ArrayList<>();

        double latRange = northEast.lat() - southWest.lat();
        double lonRange = northEast.lon() - southWest.lon();

        Cell[] cellRowToNorth = new Cell[eleValues.length];
        // Iterate through the grid, accessing the data as "square" 2 x 2 cells
        // Therefore, we stop to iterate at index "length - 2" in both dimensions
        for (int latIndex = 0; latIndex < eleValues.length - 1; latIndex++) {
            double latNorth = southWest.lat() + latRange * (1.0 - Double.valueOf(latIndex) / (eleValues.length - 1));
            double latSouth = southWest.lat()
                    + latRange * (1.0 - Double.valueOf(latIndex + 1) / (eleValues.length - 1));

            // The cell to the north of the currently processed cell
            Cell cellToNorth = cellRowToNorth[latIndex];
            // The cell to the west of the currently processed cell
            Cell cellToWest = null;
            for (int lonIndex = 0; lonIndex < eleValues[latIndex].length - 1; lonIndex++) {
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
                    eleNorthWest = eleValues[latIndex][lonIndex];
                    eleSouthWest = eleValues[latIndex + 1][lonIndex];
                    lonWest = southWest.lon() + lonRange * Double.valueOf(lonIndex) / (eleValues[latIndex].length - 1);
                }
                // If there is a previous cell to the west,
                // copy values of shared edge and vertices instead
                else {
                    eleNorthWest = cellToWest.eleNorthEast;
                    eleSouthWest = cellToWest.eleSouthEast;
                    lonWest = cellToWest.lonEast;
                }
                // Same under consideration of a previous cell to the north
                if (cellToNorth == null) {
                    eleNorthEast = eleValues[latIndex][lonIndex + 1];
                    lonEast = southWest.lon() + lonRange * Double.valueOf(lonIndex + 1) / (eleValues[latIndex].length - 1);
                }
                else {
                    eleNorthEast = cellToNorth.eleSouthEast;
                    lonEast = cellToNorth.lonEast;
                }
                eleSouthEast = eleValues[latIndex + 1][lonIndex + 1];

                Cell currentCell = new Cell(latSouth, lonWest, latNorth, lonEast, eleNorthWest, eleNorthEast, eleSouthEast,
                        eleSouthWest);

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
                    currentCell.setIntersectionSouth(isovalue);
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionSouth()));
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
                    currentCell.setIntersectionSouth(isovalue);
                    currentCell.setIntersectionEast(isovalue);
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionEast()));
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
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionEast()));
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
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionNorth()));
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
                     * Two isolines intersect (saddle point case): Isoline intersects from western
                     * to southern edge Isoline intersects from eastern to northern edge
                     */
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    currentCell.setIntersectionSouth(isovalue);
                    currentCell.setIntersectionEast(isovalue);
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionSouth()));
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionNorth()));
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
                    currentCell.setIntersectionSouth(isovalue);
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionNorth()));
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
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionWest(), currentCell.getIntersectionNorth()));
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
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionWest()));
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
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    currentCell.setIntersectionSouth(isovalue);
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionSouth()));
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
                     * Two isolines intersect (saddle point case): Isoline intersects from northern
                     * to eastern edge Isoline intersects from southern to western edge
                     */
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    currentCell.setIntersectionEast(isovalue);
                    currentCell.setIntersectionSouth(isovalue);
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionEast()));
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionWest()));
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
                    if (cellToNorth == null)
                        currentCell.setIntersectionNorth(isovalue);
                    else
                        currentCell.edgeNorth = cellToNorth.edgeSouth;
                    currentCell.setIntersectionEast(isovalue);
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionNorth(), currentCell.getIntersectionEast()));
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
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionWest()));
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
                    currentCell.setIntersectionSouth(isovalue);
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionEast(), currentCell.getIntersectionSouth()));
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
                    currentCell.setIntersectionSouth(isovalue);
                    if (cellToWest == null)
                        currentCell.setIntersectionWest(isovalue);
                    else
                        currentCell.edgeWest = cellToWest.edgeEast;
                    isolineSegments.add(new LatLonLine(currentCell.getIntersectionSouth(), currentCell.getIntersectionWest()));
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
                // when iterating over the next cell row to the south
                cellRowToNorth[latIndex] = currentCell;
                // when handling the next cell to the east in the current row
                cellToWest = currentCell;
            }
            cellToWest = null;
        }
        return isolineSegments;
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
         * Determines the Marching Squares cell case,
         *
         * @param isovalue The isovalue for which to determine the Marching Squares cell case.
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
         * Determines and returns the binary vertex states for the given isovalue. The
         * cell is traversed clockwise:
         *
         * <pre>
         *    8    4
         * NW o--->o NE
         *         |
         *         v
         * SW o<---o SE
         *    1    2
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
