package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.ActionEvent;

import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.ImageProvider;

import hhtznr.josm.plugins.elevation.ElevationPlugin;

/**
 * This class implements an action to open the key col finder dialog from the
 * menu.
 *
 * @author Harald Hetzner
 */
public class KeyColFinderAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final ElevationPlugin plugin;

    private KeyColFinderDialog dialog = null;

    /**
     * Creates a new action that will open the key col finder dialog, if performed.
     *
     * @param plugin A reference to the plugin instance in order to access required
     *               internal resources.
     */
    public KeyColFinderAction(ElevationPlugin plugin) {
        super("Key Col Finder", new ImageProvider("dialogs", "key_col.svg"), "Determine the key col between two peaks",
                null, true, "key-col-finder", false);
        this.plugin = plugin;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        SwingUtilities.invokeLater(() -> {
            if (dialog == null)
                dialog = new KeyColFinderDialog(MainApplication.getMainFrame(), plugin.getElevationDataProvider());
            dialog.showDialog();
        });
    }
}
