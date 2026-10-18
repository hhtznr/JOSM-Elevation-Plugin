package hhtznr.josm.plugins.elevation.data.geotiff;

import java.nio.file.Path;
import java.time.Instant;

import org.geotools.geometry.jts.ReferencedEnvelope;

/**
 * This class implements a cache and index record, which is used to discover
 * GeoTIFF files based on their envelope (bounds).
 *
 * @author Harald Hetzner
 */
public class GeoTiffRecord {

    private final Path path;
    private final long fileSize;
    private final Instant lastModified;
    private final ReferencedEnvelope envelope;
    private final String crs;

    /**
     * Creates a new GeoTIFF record.
     *
     * @param path         The path of the GeoTIFF file.
     * @param fileSize     The size of the GeoTIFF file.
     * @param lastModified The modification date of the GeoTIFF file.
     * @param envelope     The envelope of the GeoTIFF file.
     * @param crs          The identifier of the coordinate reference system used to
     *                     represent the envelope.
     */
    public GeoTiffRecord(Path path, long fileSize, Instant lastModified, ReferencedEnvelope envelope, String crs) {
        this.path = path.toAbsolutePath();
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.envelope = envelope;
        this.crs = crs;
    }

    /**
     * Returns the absolute path of the GeoTIFF file represented by this record.
     *
     * @return The absolute path of the GeoTIFF file.
     */
    public Path getAbsolutePath() {
        return path;
    }

    /**
     * Returns the file size of the GeoTIFF file represented by this record.
     *
     * @return The file size in bytes.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Returns the modification date of the GeoTIFF file represented by this record.
     *
     * @return The modification date of the GeoTIFF file.
     */
    public Instant getLastModified() {
        return lastModified;
    }

    /**
     * Returns the envelope (bounds) of the GeoTIFF file represented by this record.
     *
     * @return The envelope of the GeoTIFF file.
     */
    public ReferencedEnvelope getReferencedEnvelope() {
        return envelope;
    }

    /**
     * Returns the identifier of the coordinate reference system (CRS) used to
     * represent the envelope of the GeoTIFF file represented by this record.
     *
     * @return The identifier of the coordinate reference system.
     */
    public String getCoordinateReferenceSystem() {
        return crs;
    }
}
