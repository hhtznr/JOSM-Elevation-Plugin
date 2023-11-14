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
     * Bit value used to indicate that a cell vertex is equal to or above an
     * isovalue.
     */
    private static final byte ABOVE = 0x1;

    /**
     * Bit value used to indicate that a cell vertex is below an isovalue.
     */
    private static final byte BELOW = 0x0;

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

        // Iterate through the grid, accessing the data as "square" 2 x 2 cells
        // Therefore, we stop to iterate at index "length - 2" in both dimensions
        for (int latIndex = 0; latIndex < eleValues.length - 1; latIndex++) {
            double latNorth = southWest.lat() + latRange * (1.0 - Double.valueOf(latIndex) / (eleValues.length - 1));
            double latSouth = southWest.lat()
                    + latRange * (1.0 - Double.valueOf(latIndex + 1) / (eleValues.length - 1));

            for (int lonIndex = 0; lonIndex < eleValues[latIndex].length - 1; lonIndex++) {
                double lonWest = southWest.lon() + lonRange * Double.valueOf(lonIndex) / (eleValues[latIndex].length - 1);
                double lonEast = southWest.lon() + lonRange * Double.valueOf(lonIndex + 1) / (eleValues[latIndex].length - 1);

                short eleNorthWest;
                short eleNorthEast;
                short eleSouthEast;
                short eleSouthWest;
                byte[] binaryVertices = new byte[4];
                /**
                 * Clockwise traversal of the vertices of the cell
                 *
                 * <pre>
                 *    8    4
                 * NW o--->o NE
                 *         |
                 *         v
                 * SW o<---o SE
                 *    1    2
                 * </pre>
                 */
                // NW (North west)
                eleNorthWest = eleValues[latIndex][lonIndex];
                binaryVertices[0] = getVertexState(eleNorthWest, isovalue);
                // NE (North east)
                eleNorthEast = eleValues[latIndex][lonIndex + 1];
                binaryVertices[1] = getVertexState(eleNorthEast, isovalue);
                // SE (South east)
                eleSouthEast = eleValues[latIndex + 1][lonIndex + 1];
                binaryVertices[2] = getVertexState(eleSouthEast, isovalue);
                // SW (South west)
                eleSouthWest = eleValues[latIndex + 1][lonIndex];
                binaryVertices[3] = getVertexState(eleSouthWest, isovalue);

                Cell cell = new Cell(latSouth, lonWest, latNorth, lonEast, eleNorthWest, eleNorthEast, eleSouthEast,
                        eleSouthWest);

                switch (getCase(binaryVertices)) {
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
                    cell.setIntersectionWest(isovalue);
                    cell.setIntersectionSouth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionWest(), cell.getIntersectionSouth()));
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
                    cell.setIntersectionSouth(isovalue);
                    cell.setIntersectionEast(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionSouth(), cell.getIntersectionEast()));
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
                    cell.setIntersectionWest(isovalue);
                    cell.setIntersectionEast(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionWest(), cell.getIntersectionEast()));
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
                    cell.setIntersectionEast(isovalue);
                    cell.setIntersectionNorth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionEast(), cell.getIntersectionNorth()));
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
                    cell.setIntersectionWest(isovalue);
                    cell.setIntersectionSouth(isovalue);
                    cell.setIntersectionEast(isovalue);
                    cell.setIntersectionNorth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionWest(), cell.getIntersectionSouth()));
                    isolineSegments.add(new LatLonLine(cell.getIntersectionEast(), cell.getIntersectionNorth()));
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
                    cell.setIntersectionSouth(isovalue);
                    cell.setIntersectionNorth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionSouth(), cell.getIntersectionNorth()));
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
                    cell.setIntersectionWest(isovalue);
                    cell.setIntersectionNorth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionWest(), cell.getIntersectionNorth()));
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
                    cell.setIntersectionNorth(isovalue);
                    cell.setIntersectionWest(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionNorth(), cell.getIntersectionWest()));
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
                    cell.setIntersectionNorth(isovalue);
                    cell.setIntersectionSouth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionNorth(), cell.getIntersectionSouth()));
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
                    cell.setIntersectionNorth(isovalue);
                    cell.setIntersectionEast(isovalue);
                    cell.setIntersectionSouth(isovalue);
                    cell.setIntersectionWest(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionNorth(), cell.getIntersectionEast()));
                    isolineSegments.add(new LatLonLine(cell.getIntersectionSouth(), cell.getIntersectionWest()));
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
                    cell.setIntersectionNorth(isovalue);
                    cell.setIntersectionEast(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionNorth(), cell.getIntersectionEast()));
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
                    cell.setIntersectionEast(isovalue);
                    cell.setIntersectionWest(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionEast(), cell.getIntersectionWest()));
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
                    cell.setIntersectionEast(isovalue);
                    cell.setIntersectionSouth(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionEast(), cell.getIntersectionSouth()));
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
                    cell.setIntersectionSouth(isovalue);
                    cell.setIntersectionWest(isovalue);
                    isolineSegments.add(new LatLonLine(cell.getIntersectionSouth(), cell.getIntersectionWest()));
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
            }
        }
        return isolineSegments;
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
     * Determines the Marching Squares cell case
     *
     * @param binaryVertices
     * @return The integer code associated with the appropriate Marching Squares
     *         Cell case.
     */
    private static int getCase(byte[] binaryVertices) {
        if (binaryVertices.length != 4)
            throw new IllegalArgumentException("There must be exactly for binary vertex values per grid cell.");
        int caseCode = 0;
        for (int i = 0; i < binaryVertices.length; i++)
            caseCode = (caseCode << 1) | binaryVertices[i];
        return caseCode;
    }

    /**
     * Helper subclass to store the coordinate values of the four vertices of a
     * "square" cell that is being processed by the algorithm along with
     * interpolated edge intersection coordinate values.
     */
    private static class Cell {
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
