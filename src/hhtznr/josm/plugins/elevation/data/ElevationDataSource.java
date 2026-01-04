package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.tools.Logging;

/**
 * Class {@code ElevationDataSource}
 *
 * @author Harald Hetzner
 */
public class ElevationDataSource {

    public static final String PERMANENTLY_MISSING_FILE_NAME = "Permanently missing.txt";

    private final String name;
    private final File dataDirectory;
    private final SRTMTile.Type srtmTileType;
    private final URL downloadBaseURL;
    private final String ssoHost;
    private final boolean canAutoDownload;
    private final List<String> missingSRTMTiles;

    /**
     * Creates a new elevation data source.
     *
     * @param name            The name of the elevation data source.
     * @param dataDirectory   The local directory where elevation data files are
     *                        stored.
     * @param srtmTileType    The type of SRTM data, SRTM1 or SRTM3.
     * @param downloadBaseURL A base URL, which can be extended by an SRTM file name
     *                        in order to download an SRTM file.
     * @param ssoHost         A single sign-on host where to authorize file
     *                        downloads.
     * @param canAutoDownload If {@code true}, it is assumed that automatic download
     *                        of SRTM files from the given base URL is possible.
     */
    public ElevationDataSource(String name, File dataDirectory, SRTMTile.Type srtmTileType, URL downloadBaseURL,
            String ssoHost, boolean canAutoDownload) {
        this.name = name;
        this.dataDirectory = dataDirectory;
        this.srtmTileType = srtmTileType;
        this.downloadBaseURL = downloadBaseURL;
        this.ssoHost = ssoHost;
        this.canAutoDownload = canAutoDownload;
        missingSRTMTiles = loadMissingSRTMTiles();
    }

    /**
     * Returns the name of this elevation data source.
     *
     * @return The name of this elevation data source.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the data directory of this elevation data source.
     *
     * @return The data directory of this elevation data source.
     */
    public File getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Determines the SRTM file for the given tile ID from the data directory of
     * this data source if it exists
     *
     * @param srtmTileID The SRTM tile ID.
     * @return An {@code Optional} holding the SRTM file or an empty
     *         {@code Optional} if no file is available for the given tile ID.
     */
    public Optional<File> getLocalSRTMFile(String srtmTileID) {
        Logging.info("Elevation: Looking for on-disk SRTM file for tile ID " + srtmTileID);
        File srtmFile = null;
        // List the SRTM directory and filter out files that start with the SRTM tile ID
        // https://www.baeldung.com/java-list-directory-files
        Set<File> files = Stream.of(dataDirectory.listFiles())
                .filter(file -> file.isFile() && file.getName().startsWith(srtmTileID)).collect(Collectors.toSet());

        if (files.size() > 0)
            srtmFile = files.iterator().next();

        return Optional.ofNullable(srtmFile);
    }

    /**
     * Returns the SRTM type of this elevation data source.
     *
     * @return The SRTM type of this elevation data source, SRTM1 or SRTM3.
     */
    public SRTMTile.Type getSRTMTileType() {
        return srtmTileType;
    }

    /**
     * Returns the base URL where SRTM files of this elevation data source are
     * available for download.
     *
     * @return The base URL where SRTM files of this elevation data source are
     *         available for download.
     */
    public URL getDownloadBaseURL() {
        return downloadBaseURL;
    }

    /**
     * A single sign-on host where to authorize prior to downloading SRTM files.
     *
     * @return The hostname of a single sign-on host.
     */
    public String getSSOHost() {
        return ssoHost;
    }

    /**
     * Returns whether automatic file download of locally missing SRTM files from
     * this elevation data source is possible.
     *
     * @return If {@code true}, automatic download of SRTM files from the base URL
     *         of this elevation data source is possible.
     */
    public boolean canAutoDownload() {
        return canAutoDownload;
    }

    /**
     * Returns whether the SRTM tile identified by the given ID is known to be
     * permanently missing from this elevation data source (e.g. tiles that are
     * located entirely over the sea are typically missing from elevation data
     * sources).
     *
     * @param srtmTileID The ID of the SRTM tile of interest.
     * @return {@code true} if the SRTM tile is known to be permanently missing from
     *         this source.
     */
    public boolean isSRTMTilePermanentlyMissing(String srtmTileID) {
        synchronized (missingSRTMTiles) {
            int index = Collections.binarySearch(missingSRTMTiles, srtmTileID);
            // Binary search returns an index >= 0 if the ID is on the list
            // Binary search returns an index < 0 if the ID is not on the list
            return index >= 0;
        }
    }

    /**
     * Adds the specified SRTM tile ID to the list of permanently missing SRTM tiles
     * of this source and saves the list to the file of missing tiles in the
     * source's data directory.
     *
     * @param srtmTileID The ID of the missing SRTM tile.
     */
    public void addPermanentlyMissingSRTMTile(String srtmTileID) {
        if (!SRTMTile.isValidSRTMTileID(srtmTileID)) {
            Logging.warn("Elevation: SRTM tile " + srtmTileID
                    + " is invalid and therefore cannot be added to list of permanently missing tiles of source "
                    + name);
            return;
        }
        synchronized (missingSRTMTiles) {
            int index = Collections.binarySearch(missingSRTMTiles, srtmTileID);
            // Binary search returns a negative index if the ID is not on the list
            if (index < 0) {
                // Get the insertion point and add to the list
                index = -(index + 1);
                missingSRTMTiles.add(index, srtmTileID);
                Logging.info("Elevation: Added permanently missing SRTM tile " + srtmTileID + " for source " + name);
                // Save to file
                saveMissingSRTMTiles();
                return;
            }
        }
        Logging.info("Elevation: SRTM tile " + srtmTileID + " is known to be permanently missing for source " + name);
    }

    private List<String> loadMissingSRTMTiles() {
        Path filePath = dataDirectory.toPath().resolve(PERMANENTLY_MISSING_FILE_NAME);

        // Check if the file exists and is a regular file
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath))
            return new ArrayList<>();

        try (Stream<String> lineStream = Files.lines(filePath)) {
            List<String> missingTiles = lineStream.filter(SRTMTile::isValidSRTMTileID)
                    .collect(Collectors.toCollection(ArrayList::new));
            // Sort the list
            Collections.sort(missingTiles);
            return missingTiles;
        } catch (IOException e) {
            Logging.error("Elevation: Cannot load list of missing SRTM tiles from '" + filePath.toString() + "': "
                    + e.toString());
            return new ArrayList<>();
        }
    }

    private void saveMissingSRTMTiles() {
        Path filePath = dataDirectory.toPath().resolve(PERMANENTLY_MISSING_FILE_NAME);

        try {
            // Write the list to file, one item per line, using UTF-8 by default
            synchronized (missingSRTMTiles) {
                Files.write(filePath, missingSRTMTiles);
            }
            Logging.info("Elevation: Saved list of permanently missing SRTM tiles to '" + filePath.toString() + "'");
        } catch (IOException e) {
            Logging.error("Elevation: Cannot save list of missing SRTM tiles to '" + filePath.toString() + "': "
                    + e.toString());
        }
    }
}
