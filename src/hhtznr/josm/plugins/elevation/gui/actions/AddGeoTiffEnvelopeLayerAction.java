package hhtznr.josm.plugins.elevation.gui.actions;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.actions.JosmAction;

import hhtznr.josm.plugins.elevation.ElevationPlugin;

/**
 * A menu action for re-adding the GeoTiff envelope layer if it is enabled, but
 * was deleted.
 *
 * @author Harald Hetzner
 */
public class AddGeoTiffEnvelopeLayerAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final ElevationPlugin plugin;

    /**
     * Creates a new menu action.
     *
     * @param plugin The elevation plugin.
     */
    public AddGeoTiffEnvelopeLayerAction(ElevationPlugin plugin) {
        super("Add GeoTIFF overview layer", "layers/elevation_geotiff.svg",
                "(Re-)add the GeoTIFF overview layer which displays the envelopes of local GeoTIFF images", null, true,
                "geotiff-overview-add-layer", false);
        this.plugin = plugin;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        plugin.setGeoTiffEnvelopeLayerEnabled(true, true);
    }
}
