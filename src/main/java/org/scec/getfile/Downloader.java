package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * This utility class contains all the logic to simply download a file from a server
 * with validation and specified retry attempts.
 * Unlike GetFile, there is no versioning, metadata handling, or backup logic.
 */
class Downloader {
	/**
	 * Retry download until it succeeds or `retries` attempts exceeded.
	 * If retries is not specified, defaults to 1 attempt.
	 * @param uri					URI of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @param retries				Count of retry attempts
	 * @return						0 if success and 1 if reached n executions
	 */
	static int downloadFile(URI uri, Path saveLocation, int retries) {
		int status = 1;
		for (int i = 0; i < retries && status != 0; i++) {
			status = downloadFile(uri, saveLocation);
		}
		return status;
	}

	/**
	 * Downloads a file with MD5 validation
	 * @param uri					URI of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @return						0 if success and 1 if any failure
	 */
	static int downloadFile(URI uri, Path saveLocation) {
		File savLoc = saveLocation.toFile();
		File dwnLoc = new File(saveLocation.toString().concat(".part"));
		try {
			FileUtils.copyURLToFile(uri.toURL(), dwnLoc,
					/*connectionTimeoutMillis=*/5000, /*readTimeoutMillis=*/5000);
			 // Calculate the MD5 checksum of the downloaded file
			String calculatedMd5 =
					DigestUtils.md5Hex(Files.newInputStream(dwnLoc.toPath()));
			if (calculatedMd5.equalsIgnoreCase(getExpectedMd5(uri))) {
				FileUtils.copyFile(dwnLoc, savLoc);
				if (dwnLoc.exists()) {
					dwnLoc.delete();
				}
				SimpleLogger.LOG(System.out, "downloaded " + uri);
				return 0;
			}
			if (dwnLoc.exists()) {
				dwnLoc.delete();
			}
			SimpleLogger.LOG(System.err, "MD5 validation failed for " + uri);
			return 1;
		} catch (IOException e) {
			SimpleLogger.LOG(System.err, "Failed to download " + uri);
			if (dwnLoc.exists()) {
				dwnLoc.delete();
			}
			System.err.println(e);
			return 1;
		}
	}

	/**
	 * Gets the precomputed MD5 checksum for a file at the corresponding file.md5.
	 * @param uri		URI of file to download
	 * @return			String of precomputed md5sum from the md5 file on server.
	 * 					Returns an empty string if not found.
	 */
	private static String getExpectedMd5(URI uri) {
		try {
			uri = new URI(uri.toString().concat(".md5"));
			InputStream inputStream = uri.toURL().openStream();
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		} catch (URISyntaxException | IOException e) {
			SimpleLogger.LOG(
					System.err, "Could not find precomputed md5sum for " + uri);
			System.err.println(e);
			return "";
		}
	}
}
