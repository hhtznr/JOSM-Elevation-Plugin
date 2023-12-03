package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager;

import hhtznr.josm.plugins.elevation.ElevationPlugin;

/**
 * A menu action for re-adding the elevation layer if it is enabled, but was
 * deleted.
 *
 * @author Harald Hetzner
 */
public class AddElevationLayerAction extends JosmAction implements LayerManager.LayerChangeListener {

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
        MainApplication.getLayerManager().addLayerChangeListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ElevationLayer elevationLayer = plugin.getElevationLayer();
        if (elevationLayer == null)
            return;

        MainLayerManager layerManager = MainApplication.getLayerManager();
        if (!layerManager.containsLayer(elevationLayer))
            layerManager.addLayer(elevationLayer);
        else
            layerManager.setActiveLayer(elevationLayer);
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        if (e.getAddedLayer().equals(plugin.getElevationLayer()))
            setEnabled(false);
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        if (e.getRemovedLayer().equals(plugin.getElevationLayer()))
            setEnabled(true);
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }

}
