# JOSM Elevation Plugin
**Elevation** is a plugin for the [OpenStreetMap](https://www.openstreetmap.org/) editor [JOSM](https://josm.openstreetmap.de/). It displays local terrain elevation, visualizes terrain elevation in the map view and provides elevation tools. It is designed to use publicly available elevation data from NASA's [Shuttle Radar Topography Mission (SRTM)](https://www.earthdata.nasa.gov/sensors/srtm) and [Sonny's LiDAR Digital Terrain Models of Europe](https://sonny.4lima.de/). It can be configured to automatically download SRTM elevation data from [NASA Earthdata](https://www.earthdata.nasa.gov/).

## Features
- Terrain elevation at the mouse pointer location
- Elevation visualization layer
  - Contour lines
  - Hillshade
  - Lowest and highest points in the map view
  - Elevation raster
- Elevation tools
  - Set node elevation
  - Set peak prominence
  - Topographic isolation finder
  - Key col finder

This JOSM screenshot provides an overview of some of the features of the **Elevation** plugin:
![JOSM Elevation plugin -  Overview with highlighted features](https://github.com/hhtznr/JOSM-Elevation-Plugin/assets/57875126/6871d9e0-e881-4914-bc59-d08f22e83ae4)

## Building from source
**Elevation** plugin is configured as [Eclipse](https://www.eclipse.org/) Java project. The project directory must be placed in <code>josm/plugins</code> of the original [JOSM source](https://josm.openstreetmap.de/svn/trunk) tree for [build.xml](https://github.com/hhtznr/JOSM-Elevation-Plugin/blob/main/build.xml) to work. The location of the JOSM project needs to be specified on the build path. The built plugin <code>Elevation.jar</code> will be found in <code>josm/dist</code>.

## Installation
1. Copy the plugin JAR file `Elevation.jar` into the JOSM plugins directory. Under Linux, the plugins directory should be located at `$HOME/.local/share/JOSM/plugins`.
2. Launch JOSM and select `Edit -> Preferences` from the menu bar.
3. In the opened preferences dialog, select the tab `Plugins`.
4. In the sub-tab `Plugins`, select radio button option `Available`.
5. Search for `Elevation: [...]` in the list of available plugins and select the checkbox.
6. Click the `OK` button at the bottom.
7. It may be necessary to restart JOSM for the changes to take effect.

## Configuration
1. From the JOSM menu bar select `Edit -> Preferences`.
2. In the opened preferences dialog select the tab `Elevation Data`.
3. Adapt the configuration to your needs.
4. Click the `OK` button at the bottom.

For configuration options and a detailed description visit [JOSM/Plugins/Elevation on the OpenStreetMap Wiki](https://wiki.openstreetmap.org/wiki/JOSM/Plugins/Elevation).
