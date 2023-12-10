package hhtznr.josm.plugins.elevation.gui;

import java.util.List;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.LatLonLine;

/**
 * Class implementing contour lines composed of individual segments which cover
 * specified bounds.
 *
 * @author Harald Hetzner
 */
public class ContourLines extends AbstractSRTMTileGridPaintable {

    private final int isostep;
    private final List<LatLonLine> isolineSegments;

    /**
     * Creates a new set of contour lines covering the specified bounds.
     *
     * @param nominalBounds   The nominal bounds in latitude-longitude coordinate
     *                        space.
     * @param actualBounds    c
     * @param isolineSegments The list of isoline segments forming the contour
     *                        lines.
     * @param isostep         The step in meters between adjacent contour lines.
     */
    public ContourLines(Bounds nominalBounds, Bounds actualBounds, List<LatLonLine> isolineSegments, int isostep) {
        super(nominalBounds, actualBounds);
        this.isostep = isostep;
        this.isolineSegments = isolineSegments;
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
     * Returns the list of isoline segments forming the contour lines.
     *
     * @return The list of isoline segments forming the contour lines covering the
     *         bounds.
     */
    public List<LatLonLine> getIsolineSegments() {
        return isolineSegments;
    }
}
