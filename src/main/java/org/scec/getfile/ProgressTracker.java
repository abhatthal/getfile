package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.lang.Math;

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
	 * Monitoring thread will run this to track the status of a file update
	 * and update the CalcProgressBar to show user the status.
	 * @param fileKey		Name of file key in metadata
	 * @param progress		Progress bar to update
	 */
	void updateProgress(String fileKey, CalcProgressBar progress) {
		long total = getFileSize(fileKey);
		if (total <= 0) {
			return;
		}
		File file = new File(
			meta.getClientMetaFile().getParent(),
			meta.getClientMeta(fileKey, "path"));
		File partial = new File(file.toString().concat(".part"));
		// Wait until partial is built with 2s timeout
		for (int i = 0; i < 20; i++) {
			if (partial.exists()) {
				break;
			}
			try {
				 TimeUnit.MILLISECONDS.sleep(100);
			 } catch (InterruptedException e) {
				 Thread.currentThread().interrupt();
				 break;
			 }
		}
		progress.setVisible(true);
		long count = 0;
		// Show progress until download is complete
		while (partial.exists()) {
			count = partial.length();
			progress.updateProgress(count, total,
					fileKey + ": " +
					(int)Math.round(count/1e6) + " of " +
					(int)Math.round(total/1e6) + " MB downloaded");
			try {
				// Sleep for a short duration to periodically check the file size
				 TimeUnit.SECONDS.sleep(1);
				 // Exit loop after download finishes
			 } catch (InterruptedException e) {
				 Thread.currentThread().interrupt();
				 break;
			 }
		}
		progress.setVisible(false);
		progress.dispose();
	}
	
	private MetadataHandler meta;
}
