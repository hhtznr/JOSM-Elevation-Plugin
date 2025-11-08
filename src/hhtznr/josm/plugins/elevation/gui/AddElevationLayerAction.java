package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.actions.JosmAction;

import hhtznr.josm.plugins.elevation.ElevationPlugin;

/**
 * A menu action for re-adding the elevation layer if it is enabled, but was
 * deleted.
 *
 * @author Harald Hetzner
 */
public class AddElevationLayerAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final ElevationPlugin plugin;

    /**
     * Creates a new menu action.
     *
     * @param plugin The elevation plugin.
     */
    public AddElevationLayerAction(ElevationPlugin plugin) {
        super("Add elevation layer", "elevation.svg",
                "(Re-)add the elevation layer which displays contour lines and hillshade", null, true,
                "elevation-add-layer", false);
        this.plugin = plugin;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        plugin.setElevationLayerEnabled(true);
    }
}
