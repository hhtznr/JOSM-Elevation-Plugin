package hhtznr.josm.plugins.elevation.io;

import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if an SRTM tile has been read from file.
 *
 * @author Harald Hetzner
 */
public interface SRTMFileReadListener {

    /**
     * Informs the implementing class that an SRTM tile has been read from file.
     *
     * @param tile The SRTM tile that has just been read from file.
     */
    public void srtmTileRead(SRTMTile tile);
}
