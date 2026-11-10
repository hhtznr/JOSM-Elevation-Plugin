package hhtznr.josm.plugins.elevation.data.coordinate;

import java.util.HashMap;
import java.util.Map;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;

import hhtznr.josm.plugins.elevation.math.MarchingSquares;

/**
 * This class implements a projection cache for conversion of latitude-longitude
 * coordinates to east-north coordinates. This projection cache is intended to
 * be used for conversion cases, where a significant number of equal coordinate
 * points and hence conversions is expected, e.g. for the isoline endpoints of
 * {@link MarchingSquares}.
 *
 * @author Harald Hetzner
 */
public class LatLon2EastNorthProjectionCache {

    private final Projection projection;
    private final Map<LatLon, EastNorth> cache = new HashMap<>();

    /**
     * Creates a new projection cache for conversion of latitude-longitude
     * coordinates to east-north coordinates.
     *
     * @param projection The projection to use for converting coordinates.
     */
    public LatLon2EastNorthProjectionCache(Projection projection) {
        this.projection = projection;
    }

    /**
     * Converts an unprojected latitude-longitude coordinate to a projected
     * east-north coordinate.<b> Retrieves the conversion from the cache, if a point
     * with the same coordinate was previously processed. Otherwise converts the
     * coordinate using the projection and caches the mapping.
     *
     * @param point The point in latitude-longitude coordinates.
     * @return The point projected to east-north coordinates.
     */
    public EastNorth convert(LatLon point) {
        return cache.computeIfAbsent(point, p -> projection.latlon2eastNorth(point));
    }

    /**
     * Clears the cache by removing all projection mappings.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the cache size.
     *
     * @return The number of projection mappings in the cache.
     */
    public int size() {
        return cache.size();
    }
}
