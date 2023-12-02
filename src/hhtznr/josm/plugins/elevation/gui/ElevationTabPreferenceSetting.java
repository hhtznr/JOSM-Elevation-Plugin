package hhtznr.josm.plugins.elevation.gui;

import javax.swing.Box;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPreferences;

/**
 * Elevation tab in preferences.
 *
 * @author Harald Hetzner
 */
public final class ElevationTabPreferenceSetting extends DefaultTabPreferenceSetting {

    private final ElevationPlugin plugin;

    private ElevationPreferencePanel pnlElevationPreferences;

    /**
     * Creates a new tab for elevation preference settings.
     *
     * @param plugin The plugin instance for which this tab is created.
     */
    public ElevationTabPreferenceSetting(ElevationPlugin plugin) {
        super("elevation", I18n.tr("Elevation Data"),
                I18n.tr("Elevation preferences and connection settings for the SRTM server."));
        this.plugin = plugin;
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        pnlElevationPreferences = new ElevationPreferencePanel();
        pnlElevationPreferences.add(Box.createVerticalGlue(), GBC.eol().fill());
        gui.createPreferenceTab(this).add(pnlElevationPreferences, GBC.eol().fill());
    }

    /**
     * Saves the values to the preferences and applies them.
     */
    @Override
    public boolean ok() {
        // Save to preferences file
        pnlElevationPreferences.saveToPreferences();

        // Apply preferences
        boolean elevationEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_ENABLED,
                ElevationPreferences.DEFAULT_ELEVATION_ENABLED);
        plugin.setElevationEnabled(elevationEnabled);
        return false;
    }

    /**
     * No help available.
     */
    @Override
    public String getHelpContext() {
        return null;
    }
}
