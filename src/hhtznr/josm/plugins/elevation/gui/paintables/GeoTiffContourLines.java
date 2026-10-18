package hhtznr.josm.plugins.elevation.gui.paintables;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.math.MarchingSquares;

/**
 * Class implementing contour lines composed of individual segments which cover
 * specified bounds. This contour line implementation is specific to use with
 * GeoTIFF elevation data.
 *
 * @author Harald Hetzner
 */
public class GeoTiffContourLines {

    public static final int BOUNDS_SCALE_RASTER_STEP = 2;

    private static final int UNDEFINED_ISOSTEP = -1;

    private final ElevationGridView elevationGridView;

    private final int isostep;
    private final ContourLines.IsolineSegments[] isolineSegments;

    /**
     * Creates a new set of contour lines covering the bounds of the specified view.
     *
     * @param elevationGridView    The view on an elevation grid for which the
     *                             contour lines should be computed.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     * @throws AsyncOperationException Thrown if asynchronous computation of the
     *                                 contour lines is rejected or interrupted.
     */
    public GeoTiffContourLines(ElevationGridView elevationGridView, int isostep, short lowerCutoffElevation,
            short upperCutoffElevation) throws AsyncOperationException {
        this(elevationGridView, elevationGridView.getIsovalues(isostep, lowerCutoffElevation, upperCutoffElevation),
                isostep);
    }

    /**
     * Creates a new set of contour lines for the specified isovalues only covering
     * the bounds of the specified view.
     *
     * @param elevationGridView The view on an elevation grid for which the contour
     *                          lines should be computed.
     * @param isovalues         The isovalues for which to create contour lines.
     * @throws AsyncOperationException Thrown if asynchronous computation of the
     *                                 contour lines is rejected or interrupted.
     */
    public GeoTiffContourLines(ElevationGridView elevationGridView, short[] isovalues) throws AsyncOperationException {
        this(elevationGridView, isovalues, UNDEFINED_ISOSTEP);
    }

    private GeoTiffContourLines(ElevationGridView elevationGridView, short[] isovalues, int isostep)
            throws AsyncOperationException {
        this.elevationGridView = elevationGridView;
        if (isostep == UNDEFINED_ISOSTEP) {
            if (isovalues.length < 2) {
                this.isostep = 1;
            } else {
                this.isostep = isovalues[1] - isovalues[0];
            }
        } else {
            this.isostep = isostep;
        }
        MarchingSquares marchingSquares = new MarchingSquares(this.elevationGridView, isovalues);
        this.isolineSegments = marchingSquares.getIsolineSegments();
    }

    /**
     * Returns the step between adjacent contour lines.
     *
     * @return The step in meters between adjacent contour lines.
     */
    public int getIsostep() {
        return isostep;
    }

    /**
     * Returns the array of isoline segments forming the contour linescovering the
     * bounds.
     *
     * @return The array of isoline segments.
     */
    public ContourLines.IsolineSegments[] getIsolineSegments() {
        return isolineSegments;
    }
}
