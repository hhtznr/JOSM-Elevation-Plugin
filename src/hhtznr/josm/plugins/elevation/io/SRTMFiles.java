package hhtznr.josm.plugins.elevation.io;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Static methods for working with SRTM files.
 *
 * @author Harald Hetzner
 */
public class SRTMFiles {

    /**
     * Token in the file name of a ZIP-compressed SRTM file indicating that it
     * contains SRTM1 data.
     */
    public static final String SRTM1_ZIP_FILE_ID = "SRTMGL1";

    /**
     * Token in the file name of a ZIP-compressed SRTM file indicating that it
     * contains SRTM3 data.
     */
    public static final String SRTM3_ZIP_FILE_ID = "SRTMGL3";

    /**
     * Extension of an uncompressed SRTM file.
     */
    public static final String SRTM_FILE_EXTENSION = "hgt";

    /**
     * Regular expression pattern for matching the tile ID of in an SRTM file name.
     * The ID, e.g. N37W105, refers to the coordinate of its south west (lower left)
     * corner.
     */
    public static final Pattern SRTM_FILE_TILE_ID_PATTERN = Pattern.compile("^(([NS])(\\d{2})([EW])(\\d{3})).+");

    /**
     * Returns the extension of a file.
     *
     * @param file The file from which to obtain the extension.
     * @return The extension of the file without leading dot.
     */
    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf + 1);
    }

    /**
     * Extracts the SRTM tile ID from the given SRTM file name.
     *
     * @param fileName The name of the compressed SRTM file.
     * @return The tile ID of the SRTM file or {@code null} if it could not be
     *         extracted.
     */
    public static String getSRTMTileIDFromFileName(String fileName) {
        Matcher matcher = SRTM_FILE_TILE_ID_PATTERN.matcher(fileName);
        if (matcher.matches())
            return matcher.group(1);
        return null;
    }

    /**
     * Extracts the SRTM tile type (SRTM1, SRTM3)
     *
     * @param fileName The name of the compressed SRTM file.
     * @return The tile type of the SRTM file or {@code null} if it could not be
     *         extracted.
     */
    public static SRTMTile.Type getSRTMTileTypeFromEarthdataFileName(String fileName) {
        if (fileName.contains(SRTM1_ZIP_FILE_ID))
            return SRTMTile.Type.SRTM1;
        else if (fileName.contains(SRTM3_ZIP_FILE_ID))
            return SRTMTile.Type.SRTM3;
        else
            return null;
    }

    /**
     * Get the name of a ZIP-compressed SRTM file based on the SRTM tile ID and the
     * data type (SRTM1, SRTM3).
     *
     * @param srtmTileID The SRTM tile ID.
     * @param srtmType   The SRTM data type.
     * @return The corresponding name of the compressed file.
     */
    public static String getEarthdataSRTMFileName(String srtmTileID, SRTMTile.Type srtmType) {
        if (srtmType == SRTMTile.Type.SRTM1)
            return srtmTileID + ".SRTMGL1.hgt.zip";
        return srtmTileID + ".SRTMGL3.hgt.zip";
    }

    private SRTMFiles() {
    }
}
