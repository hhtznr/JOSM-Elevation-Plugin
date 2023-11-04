# JOSM Elevation Plugin
**Elevation** is a plugin for the [OpenStreetMap](https://www.openstreetmap.org/) editor [JOSM](https://josm.openstreetmap.de/). It adds functionality for displaying terrain elevation at the mouse pointer location on the map. It is designed to use publicly available elevation data from NASA's [Shuttle Radar Topography Mission (SRTM)](https://www.earthdata.nasa.gov/sensors/srtm). It can be configured to automatically download SRTM elevation data as needed.

![JOSM Elevation Plugin - Local elevation label](https://github.com/hhtznr/JOSM-Elevation-Plugin/assets/57875126/5a0b252e-1c1d-49db-95ec-dcaaa1cfe21b)

## Building from source
**Elevation** plugin is configured as <a href="https://www.eclipse.org/">Eclipse</a> Java project. The project directory must be placed in <code>josm/plugins</code> of the original <a href="https://josm.openstreetmap.de/svn/trunk">JOSM source</a> tree for <a href="https://github.com/hhtznr/JOSM-Elevation-Plugin/blob/main/build.xml">build.xml</a> to work. The location of the JOSM project needs to be specified on the build path. The built plugin <code>Elevation.jar</code> will be found in <code>josm/dist</code>.

## Installation
1. Copy the plugin JAR file `Elevation.jar` into the JOSM plugins directory. Under Linux, the plugins directory should be located at `$HOME/.local/share/JOSM/plugins`.
2. Launch JOSM and select `Edit -> Preferences` from the menu bar.
3. In the opened preferences dialog, select the tab `Plugins`.
4. In the sub-tab `Plugins`, select radio button option `Available`.
5. Search for `Elevation: [...]` in the list of available plugins and select the checkbox.
6. Click the `OK` button at the bottom.
7. Restart JOSM for the changes to take effect.
</ol>

## Configuration
1. From the JOSM menu bar, select `Edit -> Preferences`.
2. In the opened preferences dialog, select the tab `Elevation Data`.
3. Adapt the configuration to your needs.
4. Click the `OK` button at the bottom.

For configuration options visit the <a href="https://github.com/hhtznr/JOSM-Elevation-Plugin/wiki">project wiki</a>.
