package hhtznr.josm.plugins.elevation.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Class {@code SRTMFileReader} reads elevation data from SRTM (Shuttle Radar
 * Topography Mission Height) files. It can identify and read ZIP-compressed
 * SRTM1 and SRTM3 files.
 *
 * SRTM1 files are available at
 * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/">SRTM1
 * download page of NASA's Land Processes Distributed Active Archive Center (LP
 * DAAC)</a>.
 *
 * SRTM3 files are available at
 * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL3.003/2000.02.11/">SRTM3
 * download page of NASA's Land Processes Distributed Active Archive Center (LP
 * DAAC)</a>.
 *
 * In order to access these files, registration at
 * <a href="https://urs.earthdata.nasa.gov/users/new/">NASA Earthdata Login User
 * Registration</a> and creating an authorization bearer token on this site are
 * required.
 *
 * @author Harald Hetzner
 */
public class SRTMFileReader {

    /**
     * Creates a new SRTM file reader.
     */
    public SRTMFileReader() {
    }

    /**
     * Reads an SRTM tile from a ZIP-compressed SRTM file as obtained from NASA.
     *
     * <b>Format of an uncompressed SRTM file</b>
     *
     * Source: https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
     *
     * <i>Height files have the extension .HGT, and the DEM is provided as two-byte
     * (16-bit) binary signed integer raster data. Two-byte signed integers can
     * range from -32,767 to 32,767 m and can encompass the range of the Earth’s
     * elevations. Header or trailer bytes are not embedded in the file. The data
     * are stored in row major order, meaning all the data for the northernmost row,
     * row 1, are followed by all the data for row 2, and so on.
     *
     * The two-byte data are in Motorola "big-endian" order with the most
     * significant byte first. Most personal computers, and Macintosh computers
     * built after 2006 use Intel ("little-endian") order so byte swapping may be
     * necessary. Some software programs perform the swapping during ingest.
     *
     * Voids in Versions 1.0 and 2.1 are flagged with the value -32,768. There are
     * no voids in Version 3.0.</i>
     *
     * @param srtmFile The SRTM file to read.
     * @param type     The type of SRTM file, SRTM1 or SRTM3.
     * @return The SRTM tile read from the file.
     * @throws IOException Thrown if the data in the file has a wrong length or the
     *                     file cannot be read at all.
     */
    public SRTMTile readSRTMFile(File srtmFile, SRTMTile.Type type) throws IOException {
        String srtmTileID = SRTMFiles.getSRTMTileIDFromFileName(srtmFile.getName());

        Logging.info("Elevation: Reading SRTM file '" + srtmFile.getAbsolutePath() + "' for tile ID " + srtmTileID);

        // Expected number of elevation data points in one dimension
        int srtmTileLength;
        if (type == SRTMTile.Type.SRTM1)
            srtmTileLength = SRTMTile.SRTM1_TILE_LENGTH;
        else
            srtmTileLength = SRTMTile.SRTM3_TILE_LENGTH;

        // Expected number of bytes in the SRTM file (number of data points * 2 bytes
        // per short).
        int bytesExpected = srtmTileLength * srtmTileLength * 2;

        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(Compression.getUncompressedFileInputStream(srtmFile.toPath()));

            byte[] readBuffer = new byte[8192]; // 8 KB buffer (typical size)
            int bytesRead;

            // https://www.baeldung.com/convert-input-stream-to-array-of-bytes
            ByteBuffer byteBuffer = ByteBuffer.allocate(bytesExpected);
            while ((bytesRead = inputStream.read(readBuffer)) != -1) {
                if (byteBuffer.remaining() < bytesRead)
                    throw new IOException("SRTM file '" + srtmFile.getName() + "' contains more bytes than expected.");
                byteBuffer.put(readBuffer, 0, bytesRead);
            }

            if (byteBuffer.position() != bytesExpected)
                throw new IOException("Wrong number of bytes in SRTM file '" + srtmFile.getName() + "'. Expected: "
                        + bytesExpected + " bytes; read: " + bytesRead + " bytes");

            // Convert byte order
            byteBuffer.order(ByteOrder.BIG_ENDIAN);

            // SRTM file:
            // - Northernmost row = row 1
            // - Elevation values in a row from west to east
            // - Start of file: North-west corner
            // - End of file: South-east corner
            // Array storing the elevation values at the raster locations
            // - Southernmost row = row 1
            // - Elevation values in a row from west to east
            // - Start of array: South-west corner
            // - End of array: North-east corner
            short[] elevationData = new short[srtmTileLength * srtmTileLength];
            for (int latIndex = 0; latIndex < srtmTileLength; latIndex++) {
                int flippedLatIndex = srtmTileLength - 1 - latIndex;
                for (int lonIndex = 0; lonIndex < srtmTileLength; lonIndex++)
                    // Flip the order of the rows to rearrange the data with southernmost row first
                    // This will make read access more intuitive because latitude increases from
                    // south to north
                    elevationData[flippedLatIndex * srtmTileLength + lonIndex] = byteBuffer
                            .getShort((latIndex * srtmTileLength + lonIndex) * Short.BYTES);
            }
            return new SRTMTile(srtmTileID, type, elevationData, SRTMTile.Status.VALID);
        } finally {
            if (inputStream != null)
                inputStream.close();
        }
    }
}
