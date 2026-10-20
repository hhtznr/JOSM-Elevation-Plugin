package hhtznr.josm.plugins.elevation.gui.components;

import java.nio.file.Path;

import javax.swing.Box;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
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
        gui.createPreferenceTab(this, true).add(pnlElevationPreferences, GBC.eol().fill());
    }

    /**
     * Saves the values to the preferences and applies them.
     */
    @Override
    public boolean ok() {
        // Save to preferences file
        pnlElevationPreferences.saveToPreferences();
        // Apply preferences
        boolean srtmElevationLayerEnabled = ElevationPreferences.getElevationLayerEnabled();
        // If the elevation layer is to be enabled, but was previously disabled
        if (srtmElevationLayerEnabled && !plugin.isElevationLayerEnabled()) {
            // Reconfigure the data provider (can include change of SRTM type)
            plugin.reconfigureElevationDataProvider();
            // And afterwards enable the layer which will immediately use the new data provider configuration
            plugin.setSrtmElevationLayerEnabled(srtmElevationLayerEnabled);
        }
        // If the elevation layer is to be disabled or was previously already enabled
        else  {
            // Set the enabled state of the layer first (only has an effect if it gets disabled)
            plugin.setSrtmElevationLayerEnabled(srtmElevationLayerEnabled);
            // And afterwards reconfigure the data provider
            // This avoids rendering the layer with a new configuration if it gets disabled anyway
            plugin.reconfigureElevationDataProvider();
        }

        boolean geoTiffEnvelopeLayerEnabled = ElevationPreferences.getGeoTiffEnvelopeLayerEnabled();
        Path geoTiffDirectory = ElevationPreferences.getGeoTiffIndexDirectory();
        boolean recursive = ElevationPreferences.getGeoTiffIndexDirectoryRecursive();
        if (geoTiffEnvelopeLayerEnabled && !plugin.isGeoTiffEnvelopeLayerEnabled()) {
            plugin.getElevationDataProvider().getGeoTiffIndex().setGeoTiffDirectory(geoTiffDirectory, recursive);
            plugin.setGeoTiffEnvelopeLayerEnabled(true, false);
        } else {
            plugin.setGeoTiffEnvelopeLayerEnabled(false, false);
            plugin.getElevationDataProvider().getGeoTiffIndex().setGeoTiffDirectory(geoTiffDirectory, recursive);
        }

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
