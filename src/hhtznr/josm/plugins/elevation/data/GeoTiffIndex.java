package hhtznr.josm.plugins.elevation.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParsingException;

/**
 * This class implements a GeoTIFF index, which is used for fast
 * coordinate-based file lookups comparing the envelope of the GeoTIFF data. It
 * provides additional methods to read the relevant information from GeoTIFF
 * files located in a specified directory, load and save this information to a
 * JSON-based cache file, and to sync/rebuild cache and index from disk, if
 * GeoTIFF files were added or removed.
 *
 * @author Harald Hetzner
 */
public class GeoTiffIndex {

    private Path geoTiffDirectory;
    private boolean recursive;
    private Path cacheFile;
    private final GeoTiffIndexListener listener;

    private STRtree index;
    private boolean cacheUpdated = false;
    private final Object indexLock = new Object();
    private HashMap<String, GeoTiffRecord> cachedRecords;

    private final Object syncLock = new Object();
    private boolean syncInProgress = false;
    private boolean syncCanceled = false;

    private volatile Future<?> rebuildFuture = null;
    private final Object rebuildFutureLock = new Object();

    /**
     * Creates a new GeoTIFF index, which is used for fast coordinate-based file
     * lookups comparing the envelope of the GeoTIFF data.
     *
     * @param geoTiffDirectory The directory from which to read the GeoTIFF files.
     * @param recursive        If {@code true}, the GeoTIFF directory and its
     *                         subdirectories are traversed recursively searching
     *                         for GeoTIFF files. If {@code false}, only GeoTIFF
     *                         files in the GeoTIFF directory will be considered and
     *                         files in subdirectories will not be considered.
     * @param cacheFile        The path of a JSON file, where to store the created
     *                         GeoTIFF records with the envelope data in order to
     *                         persist them across JSON sessions.
     */
    public GeoTiffIndex(Path geoTiffDirectory, boolean recursive, Path cacheFile, GeoTiffIndexListener listener) {
        this.geoTiffDirectory = geoTiffDirectory.toAbsolutePath();
        this.recursive = recursive;
        this.cacheFile = cacheFile.toAbsolutePath();
        this.listener = listener;

        this.cachedRecords = new HashMap<>(0);
        index = new STRtree();
        index.build();

        HashMap<String, GeoTiffRecord> records;
        if (Files.exists(cacheFile)) {
            try {
                records = loadGeoTiffRecordsFromCacheFile();
            } catch (IOException e) {
                Logging.error("Elevation: I/O exception while attempting to read GeoTIFF envelope cache file '"
                        + cacheFile.toString() + "': " + e.toString());
                return;
            }
        } else {
            try {
                records = readGeoTiffRecordsFromGeoTiffDirectory();
                cacheUpdated = true;
            } catch (InterruptedException e) {
                return;
            }
        }

        try {
            rebuildIndex(records, cacheUpdated);
        } catch (InterruptedException e) {
            return;
        }
    }

    /**
     * Returns the path of the a JSON file, where to store the created GeoTIFF
     * records with the envelope data in order to persist them across JSON sessions.
     *
     * @return The path of the cache file.
     */
    public Path getCacheFilePath() {
        return cacheFile;
    }

    /**
     * Returns the GeoTIFF directory from which the envelopes of the contained
     * GeoTIFF files are synchronized to the index.
     *
     * @return The path of the GeoTIFF directory or {@code null}, if it is not
     *         specified.
     */
    public Path getGeoTiffDirectory() {
        return geoTiffDirectory;
    }

    /**
     * Sets the GeoTIFF directory to the specified directory and initiates a re-sync
     * of the cache and a rebuild of the index if the new directory differs from the
     * previous directory.
     *
     * @param directory The new GeoTIFF directory to set.
     * @return {@code true} if the new GeoTIFF directory was actually set.
     *         {@code false} if the provided directory is {@code null}, not a
     *         directory or it is equal to the previous GeoTIFF directory.
     */
    public boolean setGeoTiffDirectory(Path directory) {
        synchronized (syncLock) {
            if (directory == null || !Files.isDirectory(directory) || directory.equals(geoTiffDirectory)) {
                return false;
            }
            if (syncInProgress) {
                cancelSync();
            }
            geoTiffDirectory = directory;
        }
        try {
            syncCacheFromDisk(null);
        } catch (IOException e) {
            Logging.error("Elevation: I/O exception while syncing GeoTIFF envelope index with files on disk: "
                    + e.toString());
        }
        return true;
    }

    /**
     * Returns a list of GeoTIFF records stored in the index, which intersect the
     * provided JOSM bounds.
     *
     * @param bounds The bounds
     * @return A list of GeoTIFF records intersecting the provided bounds.
     */
    public List<GeoTiffRecord> query(Bounds bounds) {
        Envelope envelope = new Envelope(bounds.getMinLon(), bounds.getMaxLon(), bounds.getMinLat(),
                bounds.getMaxLat());
        return query(envelope);
    }

    /**
     * Returns a list of GeoTIFF records stored in the index, which intersect the
     * provided JTS envelope.
     *
     * @param envelope The envelope.
     * @return A list of GeoTIFF records intersecting the provided envelope.
     */
    public List<GeoTiffRecord> query(Envelope envelope) {
        List<GeoTiffRecord> matches = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List results;
        synchronized (indexLock) {
            results = index.query(envelope);
        }

        for (Object item : results) {
            matches.add(GeoTiffRecord.class.cast(item));
        }
        return matches;
    }

    /**
     * Reads selected file system and metadata of GeoTIFF files located in the
     * specified directory and creates a {@code HashMap} with instances of
     * {@link GeoTiffRecord}, which are used for fast coordinate-based file lookups
     * using an index and comparing the envelope of the GeoTIFF data.
     *
     * @return A hash map with the GeoTIFF records using the absolute path name as
     *         keys.
     * @throws InterruptedException If the thread reading the GeoTIFF files is
     *                              interrupted.
     */
    private HashMap<String, GeoTiffRecord> readGeoTiffRecordsFromGeoTiffDirectory() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Creating GeoTIFF index from files in directory "
                    + geoTiffDirectory.toString() + " interrupted prior to execution");
        }

        if (geoTiffDirectory == null) {
            Logging.info("Elevation: GeoTIFF directory is not defined");
            return new HashMap<>(0);
        }

        if (!Files.exists(geoTiffDirectory)) {
            Logging.error("Elevation: GeoTIFF directory path '" + geoTiffDirectory.toString() + "' does not exist");
            return new HashMap<>(0);
        }
        if (!Files.isDirectory(geoTiffDirectory)) {
            Logging.error("Elevation: GeoTIFF directory path '" + geoTiffDirectory.toString() + "' is not a directory");
            return new HashMap<>(0);
        }
        if (!Files.isReadable(geoTiffDirectory)) {
            Logging.error("Elevation: Permission for reading from GeoTIFF directory path '"
                    + geoTiffDirectory.toString() + "' is denied");
            return new HashMap<>(0);
        }

        Logging.info("Elevation: Creating GeoTIFF index for GeoTIFF directory " + geoTiffDirectory.toString() + " in "
                + (recursive ? "recursive" : "non-recurisve") + " mode");

        List<Path> paths = findTiffFilesInGeoTiffDirectory();

        if (paths.isEmpty()) {
            Logging.info(
                    "Elevation: No GeoTIFF files found in GeoTIFF directory '" + geoTiffDirectory.toString() + "'");
            return new HashMap<>(0);
        }

        HashMap<String, GeoTiffRecord> geoTiffRecords = new HashMap<>(paths.size());
        for (Path path : paths) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Creating GeoTIFF index from files in directory "
                        + geoTiffDirectory.toString() + " was interrupted while working on GeoTIFF files");
            }
            path = path.toAbsolutePath();
            Logging.info("Elevation: Reading GeoTIFF file '" + path.toString() + "' for index");

            long fileSize;
            try {
                fileSize = Files.size(path);
            } catch (IOException e) {
                Logging.error("Elevation: I/O exception attempting to get file size for '"
                        + path.toAbsolutePath().toString() + "': " + e.toString());
                continue;
            }

            Instant lastModified;
            try {
                lastModified = Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis());
            } catch (IOException e) {
                Logging.error("Elevation: I/O exception attempting to get modification date for '"
                        + path.toAbsolutePath().toString() + "': " + e.toString());
                continue;
            }

            ReferencedEnvelope envelope = GeoTiffImage.readReferencedEnvelopeAsEPSG4326(path);
            if (envelope == null) {
                continue;
            }
            String crs = GeoTiffImage.CRS_EPSG_4326;
            GeoTiffRecord record = new GeoTiffRecord(path, fileSize, lastModified, envelope, crs);
            geoTiffRecords.put(path.toString(), record);
        }
        Logging.info("Elevation: Created GeoTIFF index with " + geoTiffRecords.size()
                + " GeoTiff files for GeoTIFF directory " + geoTiffDirectory.toString());
        return geoTiffRecords;
    }

    private List<Path> findTiffFilesInGeoTiffDirectory() {
        if (geoTiffDirectory == null) {
            Logging.info("Elevation: GeoTIFF directory is not defined");
            return new ArrayList<Path>(0);
        }

        // Set maximum depth: 1 means only the directory itself, MAX_VALUE means
        // infinite recursion
        int maxDepth = recursive ? Integer.MAX_VALUE : 1;

        List<Path> paths;
        try (Stream<Path> stream = Files.find(geoTiffDirectory, maxDepth, (path, attrs) -> {
            if (!attrs.isRegularFile()) {
                return false;
            }
            String fileName = path.getFileName().toString().toLowerCase();
            return fileName.endsWith(".tif") || fileName.endsWith(".tiff");
        })) {
            paths = stream.collect(Collectors.toList());
        } catch (IOException e) {
            Logging.error("Elevation: I/O exception while finding GeoTIFF files in the GeoTIFF directory '"
                    + geoTiffDirectory.toString() + "': " + e.toString());
            return new ArrayList<Path>(0);
        }
        return paths;
    }

    /**
     * Loads the previously cached GeoTIFF envelopes from the cache file.
     *
     * @throws IOException If the file reader cannot be created.
     */
    private HashMap<String, GeoTiffRecord> loadGeoTiffRecordsFromCacheFile() throws IOException {
        if (!Files.exists(cacheFile)) {
            Logging.info("Elevation: GeoTIFF envelope cache file '" + cacheFile.toAbsolutePath().toString()
                    + "' does not exist.");
            return new HashMap<>(0);
        }

        HashMap<String, GeoTiffRecord> loadedRecords = new HashMap<>(0);
        // FileReader may throw FileNotFoundException
        try (JsonReader reader = Json.createReader(Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8))) {
            JsonObject rootObj = reader.readObject();
            JsonArray geoTiffsArray = rootObj.getJsonArray("geotiffs");
            loadedRecords = new HashMap<>(geoTiffsArray.size());

            for (int i = 0; i < geoTiffsArray.size(); i++) {
                JsonObject recordObj = geoTiffsArray.getJsonObject(i);

                Path filePath = Path.of(recordObj.getString("filePath"));

                long fileSize = recordObj.getJsonNumber("fileSize").longValue();

                Instant lastModified = Instant.parse(recordObj.getString("lastModified"));

                JsonObject envelopeObj = recordObj.getJsonObject("referencedEnvelope");

                String crsString = envelopeObj.getString("crs");

                CoordinateReferenceSystem crs;
                try {
                    crs = CRS.decode(crsString, true);
                } catch (FactoryException e) {
                    continue;
                }

                double minX = envelopeObj.getJsonNumber("minX").doubleValue();
                double maxX = envelopeObj.getJsonNumber("maxX").doubleValue();
                double minY = envelopeObj.getJsonNumber("minY").doubleValue();
                double maxY = envelopeObj.getJsonNumber("maxY").doubleValue();

                ReferencedEnvelope envelope = new ReferencedEnvelope(minX, maxX, minY, maxY, crs);

                GeoTiffRecord record = new GeoTiffRecord(filePath, fileSize, lastModified, envelope, crsString);
                loadedRecords.put(filePath.toString(), record);
            }
        } catch (JsonParsingException | NullPointerException e) {
            Logging.error("Elevation: Exception JSON parsing the GeoTIFF envelope cache file: " + e.toString());
            try {
                Logging.info("Elevation: Attempting to delete corrupt GeoTIFF envelope cache file '"
                        + cacheFile.toString() + "'");
                Files.delete(cacheFile);
                Logging.info("Elevation: Deleted corrupt GeoTIFF envelope cache file '" + cacheFile.toString() + "'");
            } catch (IOException ex) {
                Logging.error("Elevation: Could not delete corrupt GeoTIFF envelope cache file '" + cacheFile.toString()
                        + "': " + ex.toString());
            }
            try {
                loadedRecords = readGeoTiffRecordsFromGeoTiffDirectory();
                synchronized (indexLock) {
                    cacheUpdated = true;
                    return loadedRecords;
                }
            } catch (InterruptedException ex) {
                return loadedRecords;
            }
        }

        Logging.info("Elevation: Loaded cached envelopes of " + loadedRecords.size() + " GeoTIFF files from '"
                + cacheFile.toString() + "'");
        return loadedRecords;
    }

    /**
     * Saves the list of GeoTIFF envelopes to the cache file.
     *
     * @throws IOException If the cache file cannot be written.
     */
    public void saveGeoTiffRecordsToCacheFile() throws IOException {
        Collection<GeoTiffRecord> records;
        synchronized (indexLock) {
            if (!cacheUpdated && Files.exists(cacheFile)) {
                Logging.info("Elevation: No changes to cached envelopes of GeoTIFF files; therfore not updating '"
                        + cacheFile.toAbsolutePath().toString() + "'");
                return;
            }

            records = cachedRecords.values();
        }

        if (records.isEmpty()) {
            Logging.info("Elevation: No cached envelopes of GeoTIFF files to save");
            if (Files.exists(cacheFile)) {
                Logging.info(
                        "Elevation: Attempting to delete GeoTIFF envelope cache file '" + cacheFile.toString() + "'");
                Files.deleteIfExists(cacheFile);
                Logging.info("Elevation: Deleted GeoTIFF envelope cache file '" + cacheFile.toString() + "'");
            }
        }

        Logging.info("Elevation: Saving " + records.size() + " cached envelopes of GeoTIFF files to '"
                + cacheFile.toAbsolutePath().toString().toString() + "'");

        // Create a temporary file for the content of the cache file
        Path tempFile = Files.createTempFile("geotiff-index-", ".json");

        try (JsonGenerator gen = Json.createGenerator(Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8))) {
            gen.writeStartObject(); // {
            gen.writeStartArray("geotiffs"); // "geotiffs": [
            for (GeoTiffRecord record : records) {
                ReferencedEnvelope envelope = record.getReferencedEnvelope();

                gen.writeStartObject(); // { "record"
                gen.write("filePath", record.getAbsolutePath().toAbsolutePath().toString());
                gen.write("fileSize", record.getFileSize());
                gen.write("lastModified", record.getLastModified().toString());

                gen.writeStartObject("referencedEnvelope"); // "referencedEnvelope": {

                gen.write("crs", record.getCoordinateReferenceSystem());
                gen.write("minX", envelope.getMinX());
                gen.write("maxX", envelope.getMaxX());
                gen.write("minY", envelope.getMinY());
                gen.write("maxY", envelope.getMaxY());

                gen.writeEnd(); // } referencedEnvelope
                gen.writeEnd(); // } record
            }

            gen.writeEnd(); // ] geotiffs
            gen.writeEnd(); // } root object
        }

        try {
            // Move the temporary file to the actual cache file location
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // If atomic move not supported by file system, move file without this option
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }

        Logging.info("Elevation: Saved cached envelopes of " + records.size() + " GeoTIFF files to '"
                + cacheFile.toString() + "'");
    }

    /**
     * Synchronizes the cache with the GeoTIFF envelopes from the GeoTIFF files in
     * the GeoTIFF directory.
     *
     * @param progressMonitor A progress monitor dialog to inform the user about the
     *                        progress.
     * @throws IOException If an I/O exception occurs upon finding GeoTIFF files in
     *                     the GeoTIFF directory.
     */
    public void syncCacheFromDisk(ProgressMonitor progressMonitor) throws IOException {
        synchronized (syncLock) {
            if (syncInProgress) {
                Logging.warn(
                        "Elevation: Not starting sync of GeoTIFF envelope index from disk; sync already in progress");
                return;
            }
            if (syncCanceled) {
                syncInProgress = false;
                syncCanceled = false;
                return;
            }
            syncInProgress = true;
        }
        Path directory = geoTiffDirectory;
        try {
            if (directory == null) {
                synchronized (indexLock) {
                    if (!cachedRecords.isEmpty()) {
                        Logging.info("Elevation: GeoTIFF directory is no longer defined; clearing index and cache");
                        try {
                            rebuildIndex(new HashMap<String, GeoTiffRecord>(0), true);
                        } catch (InterruptedException e) {
                            return;
                        }
                        return;
                    }
                }
                Logging.error(
                        "Elevation: You need to define the path of the directory with the GeoTIFF files in the preferences first");
                synchronized (syncLock) {
                    syncInProgress = false;
                    syncCanceled = false;
                    return;
                }
            }
            if (!Files.exists(directory)) {
                Logging.error("Elevation: GeoTIFF directory path '" + directory.toString() + "' does not exist");
                return;
            }
            if (!Files.isDirectory(directory)) {
                Logging.error("Elevation: GeoTIFF directory path '" + directory.toString() + "' is not a directory");
                return;
            }
            if (!Files.isReadable(directory)) {
                Logging.error("Elevation: Permission for reading from GeoTIFF directory path '" + directory.toString()
                        + "' is denied");
                return;
            }

            List<Path> paths = findTiffFilesInGeoTiffDirectory();

            if (paths.isEmpty()) {
                Logging.info("Elevation: No GeoTIFF files found in GeoTIFF directory '" + directory.toString() + "'");
                synchronized (indexLock) {
                    if (!index.isEmpty()) {
                        Logging.info("Elevation: Clearing index with GeoTIFF envelopes");
                        index = new STRtree();
                        cachedRecords = new HashMap<>(0);
                    }
                }
                return;
            }
            if (progressMonitor != null) {
                progressMonitor.setTicksCount(paths.size());
            }

            Set<String> filesOnDisk = new HashSet<>();
            @SuppressWarnings("unchecked")
            HashMap<String, GeoTiffRecord> newCachedRecords = (HashMap<String, GeoTiffRecord>) cachedRecords.clone();
            boolean cacheUpdated = false;
            int numberOfProcessedPaths = 0;
            Logging.info("Elevation: Synchronizing GeoTIFF elevation index from GeoTIFF directory '"
                    + directory.toString() + "'");
            // Discover new files and update modified files
            for (Path path : paths) {
                synchronized (syncLock) {
                    if (syncCanceled) {
                        return;
                    }
                }
                path = path.toAbsolutePath();
                filesOnDisk.add(path.toString());
                if (progressMonitor != null) {
                    progressMonitor.setCustomText(path.toString());
                }

                long onDiskFileSize = Files.size(path);

                Instant onDiskLastModified = Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis());

                GeoTiffRecord record = cachedRecords.get(path.toString());

                // If the GeoTIFF is not known to the cache or the version on disk has a
                // different modification date
                if (record == null || onDiskFileSize != record.getFileSize()
                        || !onDiskLastModified.equals(record.getLastModified())) {
                    // Found a new GeoTIFF file on disk
                    // Read the envelope from the file
                    ReferencedEnvelope envelope = GeoTiffImage.readReferencedEnvelopeAsEPSG4326(path);
                    if (envelope == null) {
                        continue;
                    }

                    GeoTiffRecord newRecord = new GeoTiffRecord(path, onDiskFileSize, onDiskLastModified, envelope,
                            GeoTiffImage.CRS_EPSG_4326);
                    newCachedRecords.put(path.toString(), newRecord);
                    cacheUpdated = true;
                }

                numberOfProcessedPaths++;
                if (progressMonitor != null) {
                    // Increase progress by one tick
                    progressMonitor.worked(1);
                    progressMonitor.setExtraText(" (" + numberOfProcessedPaths + " of " + paths.size() + ")");
                }
            }
            Logging.info("Elevation: Processed " + numberOfProcessedPaths + " GeoTIFF files for cache synchronization");

            // Identify and remove dead entries no longer present in the scanned scope
            // returns true if the set changed as a result of the call
            if (newCachedRecords.keySet().retainAll(filesOnDisk)) {
                cacheUpdated = true;
            }

            // Rebuild the index if the cache was updated
            if (cacheUpdated) {
                try {
                    rebuildIndex(newCachedRecords, true);
                } catch (InterruptedException e) {
                    return;
                }
            }
        } finally {
            synchronized (syncLock) {
                syncInProgress = false;
                syncCanceled = false;
            }
        }
    }

    /**
     * Cancels synchronization of the cache from disk if it is running.
     */
    public void cancelSync() {
        synchronized (syncLock) {
            syncCanceled = true;
        }
    }

    private void rebuildIndex(HashMap<String, GeoTiffRecord> newRecords, boolean updated) throws InterruptedException {
        synchronized (rebuildFutureLock) {
            if (rebuildFuture != null && !rebuildFuture.isDone()) {
                rebuildFuture.cancel(true);
            }
            rebuildFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
                try {
                    rebuildIndexTask(newRecords, updated);
                } catch (InterruptedException e) {
                    return;
                }
            });
        }
    }

    private void rebuildIndexTask(HashMap<String, GeoTiffRecord> newRecords, boolean updated)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Index rebuild interrupted before it started");
        }
        Logging.info("Elevation: Rebuilding GeoTFF envelope index");
        STRtree newIndex = new STRtree();
        for (GeoTiffRecord record : newRecords.values()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Index rebuild interrupted");
            }
            ReferencedEnvelope envelope = record.getReferencedEnvelope();
            Path path = record.getAbsolutePath();
            newIndex.insert(envelope, record);
            Logging.info("Elevation: Added GeoTIFF file " + path.toString() + " to GeoTIFF index");
        }
        newIndex.build();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Index rebuild interrupted");
        }
        synchronized (indexLock) {
            this.cachedRecords = newRecords;
            index = newIndex;
            cacheUpdated = updated;
            listener.geoTiffIndexUpdated();
        }
        try {
            this.saveGeoTiffRecordsToCacheFile();
        } catch (IOException e) {
            Logging.error("Elevation: Could not save the cached GeoTIFF envelopes to '" + cacheFile.toString() + "': "
                    + e.toString());
        }
        Logging.info("Elevation: Finished rebuilding of GeoTFF envelope index");
    }
}
