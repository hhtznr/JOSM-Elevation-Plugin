package hhtznr.josm.plugins.elevation.data.geotiff;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if the GeoTIFF envelope index has been updated.
 *
 * @author Harald Hetzner
 */
public interface GeoTiffIndexListener {

    /**
     * Informs the implementing class that the GeoTIFF envelope index has been
     * updated.
     */
    public void geoTiffIndexUpdated();
}
