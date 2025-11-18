package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.WindowConstants;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.ImageProvider;

import hhtznr.josm.plugins.elevation.ElevationPlugin;

/**
 * This class implements an action to open the topographic elevation finder
 * dialog from the menu.
 *
 * @author Harald Hetzner
 */
public class TopographicIsolationFinderAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final ElevationPlugin plugin;

    private TopographicIsolationFinderDialog dialog = null;

    /**
     * Creates a new action that will open the topographic elevation finder dialog,
     * if performed.
     *
     * @param plugin A reference to the plugin instance in order to access required
     *               internal resources.
     */
    public TopographicIsolationFinderAction(ElevationPlugin plugin) {
        super("Topograhic Isolation Finder", new ImageProvider("dialogs", "topographic_isolation"),
                "Determine topographic isolation of a peak", null, true, "topographic-isolation-finder", false);
        this.plugin = plugin;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        if (dialog == null) {
            dialog = new TopographicIsolationFinderDialog(MainApplication.getMainFrame(),
                    plugin.getElevationDataProvider());
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    dialog = null;
                }
            });
        }
        dialog.showDialog();
    }
}
