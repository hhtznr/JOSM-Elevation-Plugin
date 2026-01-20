package hhtznr.josm.plugins.elevation.gui;

import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.data.SRTMTileGridView;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * Class implementing an object to represent the lowest and highest points
 * within a view on an SRTM tile grid.
 *
 * @author Harald Hetzner
 */
public class LowestAndHighestPoints extends AbstractSRTMTileGridPaintable {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator("Lowest and highest points");

    private final List<LatLonEle> lowestPoints;
    private final List<LatLonEle> highestPoints;
    private final short lowestElevation;
    private final short highestElevation;

    /**
     * Creates a new lowest and highest points object.
     *
     * @param tileGridView The SRTM tile grid view from which elevation data should
     *                     be obtained.
     */
    public LowestAndHighestPoints(SRTMTileGridView tileGridView) {
        super(namer.nextName(), tileGridView);

        lowestPoints = new LinkedList<>();
        highestPoints = new LinkedList<>();

        Bounds bounds = tileGridView.getBounds();
        int height = tileGridView.getHeight();
        int width = tileGridView.getWidth();

        double boundsMinLat = bounds.getMinLat();
        double boundsMinLon = bounds.getMinLon();
        double latScale = bounds.getHeight() / (double) (height - 1);
        double lonScale = bounds.getWidth() / (double) (width - 1);

        short previousMinEle = Short.MAX_VALUE;
        short previousMaxEle = Short.MIN_VALUE;

        for (int latIndex = 0; latIndex < height; latIndex++) {
            double lat = boundsMinLat + latScale * latIndex;

            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                double lon = boundsMinLon + lonScale * lonIndex;

                short ele = tileGridView.getElevation(latIndex, lonIndex);
                if (ele == SRTMTile.SRTM_DATA_VOID)
                    continue;

                if (ele < previousMinEle) {
                    lowestPoints.clear();
                    lowestPoints.add(new LatLonEle(lat, lon, ele));
                    previousMinEle = ele;
                } else if (ele == previousMinEle) {
                    lowestPoints.add(new LatLonEle(lat, lon, ele));
                }

                if (ele > previousMaxEle) {
                    highestPoints.clear();
                    highestPoints.add(new LatLonEle(lat, lon, ele));
                    previousMaxEle = ele;
                } else if (ele == previousMaxEle) {
                    highestPoints.add(new LatLonEle(lat, lon, ele));
                }
            }
        }
        if (previousMinEle == Short.MAX_VALUE)
            lowestElevation = SRTMTile.SRTM_DATA_VOID;
        else
            lowestElevation = previousMinEle;
        if (previousMaxEle == Short.MIN_VALUE)
            highestElevation = SRTMTile.SRTM_DATA_VOID;
        else
            highestElevation = previousMaxEle;
    }

    /**
     * Returns the list of the lowest points in the tile grid view. The list will be
     * empty if the view does not contain valid elevation data.
     *
     * @return The list with the lowest points.
     */
    public List<LatLonEle> getLowestPoints() {
        return lowestPoints;
    }

    /**
     * Returns the list of the highest points in the tile grid view. The list will
     * be empty if the view does not contain valid elevation data.
     *
     * @return The list with the highest points.
     */
    public List<LatLonEle> getHighestPoints() {
        return highestPoints;
    }

    /**
     * Returns the lowest elevation value in the tile grid view.
     * {@link SRTMTile#SRTM_DATA_VOID} will be returned if the view does not contain
     * valid elevation data.
     *
     * @return The lowest elevation value.
     */
    public short getLowestElevation() {
        return lowestElevation;
    }

    /**
     * Returns the highest elevation value in the tile grid view.
     * {@link SRTMTile#SRTM_DATA_VOID} will be returned if the view does not contain
     * valid elevation data.
     *
     * @return The highest elevation value.
     */
    public short getHighestElevation() {
        return highestElevation;
    }
}
