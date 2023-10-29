package hhtznr.josm.plugins.elevation;

import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Logging;

public class ElevationPlugin extends Plugin {

	 /**
     * Initializes the plugin.
     * @param info Context information about the plugin.
     */
    public ElevationPlugin(PluginInformation info) {
        super(info);
        Logging.info("Elevation plugin initialized");
    }

}
