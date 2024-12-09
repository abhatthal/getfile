package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import java.lang.Math;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * The ProgressTracker updates a CalcProgressBar with download data.
 */
class ProgressTracker {

	/**
	 * ProgressTracker Constructor
	 * @param meta
	 */
	ProgressTracker(MetadataHandler meta) {
		this.meta = meta;
	}

	/**
	 * Gets the size of a file on server in bytes.
	 * @param fileKey	Key in server metadata corresponding to server file
	 * @return			size in bytes or 0 if not found
	 */
	long getFileSize(String fileKey) {
		final String path = meta.getServerMeta(fileKey, "path");
		if (path.equals("")) {
			SimpleLogger.LOG(System.err,
					"File key \"" + fileKey + "\" does not exist in server meta");
			return 0;
		}
		URI serverLoc = URI.create(
				meta.getServerPath().toString().concat(path));
		try {
			HttpURLConnection connection =
					(HttpURLConnection)serverLoc.toURL().openConnection();
			connection.setRequestMethod("HEAD");
			return connection.getContentLengthLong();
		} catch (MalformedURLException e) {
			SimpleLogger.LOG(System.err, "URL is invalid " + serverLoc);
			e.printStackTrace();
		} catch (IOException e) {
			SimpleLogger.LOG(System.err, "Could not read " + serverLoc);
			e.printStackTrace();
		}
		return 0;
	}
	
	/**
	 * Gets the current progress of a download in representable `chunks`.
	 * As file downloads become very large, it becomes infeasible to show
	 * progress without chunking into larger groups. This is used to show
	 * the progress bar in updateProgress.
	 * @param count		Number of bytes downloaded
	 * @param total		Number of bytes total
	 * @return			A pair of chunks downloaded and total chunks
	 */
	private Pair<Long, Long> chunkProgress(long count, long total) {
		if (count > total || count < 0 || total < 0) {
			SimpleLogger.LOG(System.err, "Unable to parse progress "
					+ count + " / " + total);
			return null;
		}
		final long NUM_CHUNKS = (long) 1e3;
		if (total <= NUM_CHUNKS) {
			return ImmutablePair.of(count, total);
		}
		return ImmutablePair.of(NUM_CHUNKS * count / total, NUM_CHUNKS);
	}

	/**
	 * Monitoring thread will run this to track the status of a file update
	 * and update the CalcProgressBar to show user the status.
	 * @param fileKey		Name of file key in metadata
	 * @param progress		Progress bar to update
	 */
	void updateProgress(String fileKey, CalcProgressBar progress) {
		final long fileSizeBytes = getFileSize(fileKey);
		if (fileSizeBytes <= 0) {
			return;
		}

		File file = new File(
			meta.getClientMetaFile().getParent(),
			meta.getClientMeta(fileKey, "path"));
		File partial = new File(file.toString().concat(".part"));

		// Define SwingWorker for background processing
		SwingWorker<Void, Long> worker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() throws Exception {
				// Show progress until the download completes
				while (partial.exists()) {
					System.out.println("ProgressTracker.updateProgress(...).new SwingWorker() {...}.doInBackground()");
					publish(partial.length());
					Thread.sleep(200); // Non-EDT sleep
				}
				return null;
			}

			@Override
			protected void process(List<Long> chunks) {
				System.out.println("ProgressTracker.updateProgress(...).new SwingWorker() {...}.process()");
				// Update progress bar on the EDT
				long totalBytesDownloaded = chunks.get(chunks.size() - 1);
				Pair<Long, Long> chunkedProgress =
						ProgressTracker.this.chunkProgress(
								totalBytesDownloaded, fileSizeBytes);
				final int FILE_KEY_LEN = 24;
				// Update the progress bar using the chunked progress
				progress.updateProgress(
					chunkedProgress.getLeft(), chunkedProgress.getRight(),
					((fileKey.length() <= FILE_KEY_LEN)
						? fileKey
						: fileKey.substring(0, FILE_KEY_LEN))
						+ ": "
						+ (int) Math.round(totalBytesDownloaded / 1e6) + " of "
						+ (int) Math.round(fileSizeBytes / 1e6) + " MB downloaded"
				);
			}

			@Override
			protected void done() {
				// Hide and dispose progress bar when done
				System.out.println("ProgressTracker.updateProgress(...).new SwingWorker() {...}.done()");
				progress.setVisible(false);
				progress.dispose();
			}
		};

		// Wait until partial file exists
		for (int i = 0; i < 10; i++) {
			if (partial.exists()) {
				break;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (partial.exists()) {
			// Show the progress bar and start the worker
			SwingUtilities.invokeLater(() -> progress.setVisible(true));
			worker.execute();
		} else {
			SimpleLogger.LOG(System.err, "Download took too long to start. Not showing progress bar.");
		}
	}
	
	private MetadataHandler meta;
}
