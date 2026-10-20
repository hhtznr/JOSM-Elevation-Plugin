package hhtznr.josm.plugins.elevation.data;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if elevation data from an GeoTIFF images is available.
 *
 * @author Harald Hetzner
 */
public interface GeoTiffElevationDataProviderListener {

    /**
     * Informs the implementing class that the GeoTIFF envelope index has been
     * updated.
     */
    public void geoTiffIndexUpdated();
}
