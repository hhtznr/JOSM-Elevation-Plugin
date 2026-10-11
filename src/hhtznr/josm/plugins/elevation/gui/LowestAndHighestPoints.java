package hhtznr.josm.plugins.elevation.gui;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * Class implementing an object to represent the lowest and highest points
 * within a view on an SRTM tile grid.
 *
 * @author Harald Hetzner
 */
public class LowestAndHighestPoints extends AbstractSRTMTileGridPaintable {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Lowest and highest points");

    private final List<LatLonEle> lowestPoints;
    private final List<LatLonEle> highestPoints;
    private final double lowestElevation;
    private final double highestElevation;

    /**
     * Creates a new lowest and highest points object.
     *
     * @param elevationGridView The elevation grid view from which elevation data
     *                          should be obtained.
     */
    public LowestAndHighestPoints(ElevationGridView elevationGridView) {
        super(namer.nextName(), elevationGridView);

        int height = elevationGridView.getHeight();
        int width = elevationGridView.getWidth();

        double previousMinEle = Double.POSITIVE_INFINITY;
        double previousMaxEle = Double.NEGATIVE_INFINITY;

        List<Point> lowestPointIndices = new LinkedList<>();
        List<Point> highestPointIndices = new LinkedList<>();
        for (int latIndex = 0; latIndex < height; latIndex++) {

            for (int lonIndex = 0; lonIndex < width; lonIndex++) {

                double ele = elevationGridView.getElevation(latIndex, lonIndex);
                if (elevationGridView.isNoDataValue(ele)) {
                    continue;
                }

                if (ele < previousMinEle) {
                    lowestPointIndices.clear();
                    lowestPointIndices.add(new Point(lonIndex, latIndex));
                    previousMinEle = ele;
                } else if (ele == previousMinEle) {
                    lowestPointIndices.add(new Point(lonIndex, latIndex));
                }

                if (ele > previousMaxEle) {
                    highestPointIndices.clear();
                    highestPointIndices.add(new Point(lonIndex, latIndex));
                    previousMaxEle = ele;
                } else if (ele == previousMaxEle) {
                    highestPointIndices.add(new Point(lonIndex, latIndex));
                }
            }
        }
        if (previousMinEle == Double.POSITIVE_INFINITY) {
            lowestElevation = Double.NaN;
        } else {
            lowestElevation = previousMinEle;
        }
        if (previousMaxEle == Double.NEGATIVE_INFINITY) {
            highestElevation = Double.NaN;
        } else {
            highestElevation = previousMaxEle;
        }

        lowestPoints = new ArrayList<>(lowestPointIndices.size());
        for (Point point : lowestPointIndices) {
            int latIndex = point.y;
            int lonIndex = point.x;
            LatLon latLon = elevationGridView.toLatLon(latIndex, lonIndex);
            lowestPoints.add(new LatLonEle(latLon.lat(), latLon.lon(), lowestElevation));
        }

        highestPoints = new ArrayList<>(highestPointIndices.size());
        for (Point point : highestPointIndices) {
            int latIndex = point.y;
            int lonIndex = point.x;
            LatLon latLon = elevationGridView.toLatLon(latIndex, lonIndex);
            highestPoints.add(new LatLonEle(latLon.lat(), latLon.lon(), highestElevation));
        }
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
    public double getLowestElevation() {
        return lowestElevation;
    }

    /**
     * Returns the highest elevation value in the tile grid view.
     * {@link SRTMTile#SRTM_DATA_VOID} will be returned if the view does not contain
     * valid elevation data.
     *
     * @return The highest elevation value.
     */
    public double getHighestElevation() {
        return highestElevation;
    }
}
