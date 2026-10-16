package hhtznr.josm.plugins.elevation.data;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.gui.LowestAndHighestPoints;

public class GeoTiffElevationGridView implements ElevationGridView {

    private final GeoTiffElevationGrid elevationGrid;
    private final int gridRasterLatIndexSouth;
    private final int gridRasterLonIndexWest;
    private final int height;
    private final int width;
    private final Bounds bounds;
    private final LowestAndHighestPoints lowestAndHighestPoints;

    protected GeoTiffElevationGridView(GeoTiffElevationGrid elevationGrid, int latIndexSouth, int latIndexNorth,
            int lonIndexWest, int lonIndexEast, Bounds bounds) {
        this.elevationGrid = elevationGrid;
        gridRasterLatIndexSouth = latIndexSouth;
        gridRasterLonIndexWest = lonIndexWest;
        height = latIndexNorth - latIndexSouth + 1;
        width = lonIndexEast - lonIndexWest + 1;
        this.bounds = bounds;

        double previousMinEle = Double.POSITIVE_INFINITY;
        double previousMaxEle = Double.NEGATIVE_INFINITY;

        double lowestElevation = Double.POSITIVE_INFINITY;
        double highestElevation = Double.NEGATIVE_INFINITY;

        List<Point> lowestPointIndices = new LinkedList<>();
        List<Point> highestPointIndices = new LinkedList<>();

        for (int latIndex = 0; latIndex < height; latIndex++) {
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                double ele = getElevation(latIndex, lonIndex);
                if (isNoDataValue(ele)) {
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

        ArrayList<LatLonEle> lowestPoints = new ArrayList<>(lowestPointIndices.size());
        for (Point point : lowestPointIndices) {
            int latIndex = point.y;
            int lonIndex = point.x;
            LatLon latLon = toLatLon(latIndex, lonIndex);
            lowestPoints.add(new LatLonEle(latLon.lat(), latLon.lon(), lowestElevation));
        }

        ArrayList<LatLonEle> highestPoints = new ArrayList<>(highestPointIndices.size());
        for (Point point : highestPointIndices) {
            int latIndex = point.y;
            int lonIndex = point.x;
            LatLon latLon = toLatLon(latIndex, lonIndex);
            highestPoints.add(new LatLonEle(latLon.lat(), latLon.lon(), highestElevation));
        }
        lowestAndHighestPoints = new LowestAndHighestPoints(this, lowestPoints, highestPoints);
    }

    @Override
    public ElevationGrid getElevationGrid() {
        return elevationGrid;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public double getElevation(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.getElevation(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public boolean isNoDataValue(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.isNoDataValue(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public boolean isNoDataValue(double ele) {
        return elevationGrid.isNoDataValue(ele);
    }

    @Override
    public LatLon toLatLon(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.toLatLon(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public LatLonEle toLatLonEle(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.toLatLonEle(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    @Override
    public short[] getIsovalues(int isostep, int lowerCutoffElevation, int upperCutoffElevation) {
        List<LatLonEle> lowestPoints = lowestAndHighestPoints.getLowestPoints();
        List<LatLonEle> highestPoints = lowestAndHighestPoints.getHighestPoints();

        double lowestElevation = Double.NaN;
        double highestElevation = Double.NaN;
        if (lowestPoints.size() > 0) {
            lowestElevation = lowestPoints.get(0).ele();
        }
        if (highestPoints.size() > 0) {
            highestElevation = highestPoints.get(0).ele();
        }
        return GeoTiffElevationGrid.getIsovalues(lowestElevation, highestElevation, isostep, lowerCutoffElevation,
                upperCutoffElevation);
    }

    @Override
    public LowestAndHighestPoints getLowestAndHighestPoints() {
        return lowestAndHighestPoints;
    }
}
