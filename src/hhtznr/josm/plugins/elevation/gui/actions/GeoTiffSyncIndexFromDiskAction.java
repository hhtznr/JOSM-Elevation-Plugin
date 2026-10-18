package hhtznr.josm.plugins.elevation.gui.actions;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.Logging;
import org.xml.sax.SAXException;

import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffIndex;
import hhtznr.josm.plugins.elevation.gui.layers.GeoTiffEnvelopeLayer;

/**
 * This class implements a JOSM action to synchronize the GeoTIFF envelope index
 * with the GeoTIFF files in the GeoTIFF directory on disk. Synchronization
 * makes sense if GeoTIFF files have been added or removed from the GeoTIFF
 * directory, where the GeoTIFF files are located for which the envelopes
 * (bounds) are drawn on the {@link GeoTiffEnvelopeLayer}.
 *
 * @author Harald Hetzner
 */
public class GeoTiffSyncIndexFromDiskAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final GeoTiffIndex index;

    /**
     * Creates a new JOSM action to synchronize the GeoTIFF envelope index with the
     * GeoTIFF files in the GeoTIFF directory on disk.
     *
     * @param index The GeoTIFF envelope index.
     */
    public GeoTiffSyncIndexFromDiskAction(GeoTiffIndex index) {
        super("GeoTIFF: Sync files from disk", "dialogs/refresh.svg",
                "Update the GeoTIFF envelope index from GeoTIFF files in the GeoTIFF directory", null, true,
                "geotiff-sync-index-from-disk", false);
        this.index = index;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SyncCacheFromDiskTask task = new SyncCacheFromDiskTask(index);
        try {
            MainApplication.worker.submit(task);
        } catch (RejectedExecutionException ex) {
            Logging.error(
                    "Elevation: Execution of GeoTIFF envelope index synchronization task rejected: " + ex.toString());
        }
    }

    public static class SyncCacheFromDiskTask extends PleaseWaitRunnable {

        private final GeoTiffIndex index;

        /**
         * Creates a new task for synchronization of the GeoTIFF envelope index with
         * GeoTIFF files in the GeoTIFF directory on disk.
         *
         * @param index The GeoTIFF envelope index.
         */
        public SyncCacheFromDiskTask(GeoTiffIndex index) {
            super("Sync GeoTIFF index from disk");
            this.index = index;
        }

        @Override
        protected void cancel() {
            index.cancelSync();
        }

        @Override
        protected void realRun() throws SAXException, IOException, OsmTransferException {
            index.syncCacheFromDisk(getProgressMonitor());
        }

        @Override
        protected void finish() {
        }
    }
}
