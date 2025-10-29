package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import java.lang.Math;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * The ProgressTracker updates a CalcProgressBar with download data.
 */
class ProgressTracker {
	private MetadataHandler meta;
	private String appName;

	/**
	 * ProgressTracker Constructor
	 * @param meta
	 * @param appName
	 */
	ProgressTracker(MetadataHandler meta, String appName) {
		this.meta = meta;
		this.appName = appName;
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
	 */
	void updateProgress(String fileKey) {
		SwingUtilities.invokeLater(() -> {
			CalcProgressBar progress = new CalcProgressBar(
				/*owner=*/null,
				/*title=*/"Downloading " + appName + " Files",
				/*info=*/"downloading " + fileKey,
				/*visible=*/false);
			final long fileSizeBytes = getFileSize(fileKey);
			if (fileSizeBytes <= 0) {
				progress.dispose();
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
						publish(partial.length());
						Thread.sleep(200); // Non-EDT sleep
					}
					return null;
				}

				@Override
				protected void process(List<Long> chunks) {
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
					progress.showProgress(false);
				}
			};
			
			// Check for the .part file in a background thread (not on the EDT)
			CompletableFuture.runAsync(() -> {
				try {
					for (int i = 0; i < 4; i++) {
						if (partial.exists()) break;
						Thread.sleep(1000);
					}
					if (partial.exists()) {
						SwingUtilities.invokeLater(() -> {
							progress.showProgress(true);
							worker.execute();
						});
					} else {
						SwingUtilities.invokeLater(() -> {
							progress.showProgress(false);
						});
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			if (progress.isDisplayable()) {
			    progress.dispose();
			}
		});
	}
}
