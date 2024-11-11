package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * This utility class contains all the logic to simply download a file from a server
 * with validation and specified retry attempts.
 * Unlike GetFile, there is no versioning, metadata handling, or backup logic.
 */
public class Downloader {
	/**
	 * Retry download until it succeeds or `retries` attempts exceeded.
	 * If retries is not specified, defaults to 1 attempt.
	 * @param fileUrl				URL of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @param retries				Count of retry attempts
	 * @return						0 if success and 1 if reached n executions
	 */
	public static int downloadFile(String fileUrl, String saveLocation, int retries) {
		int status = 1;
		for (int i = 0; i < retries && status != 0; i++) {
			status = downloadFile(fileUrl, saveLocation);
		}
		return status;
	}

	/**
	 * Downloads a file with MD5 validation
	 * @param fileUrl				URL of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @return						0 if success and 1 if any failure
	 */
	public static int downloadFile(String fileUrl, String saveLocation) {
		try {
			File savLoc = new File(saveLocation);
			// Download the file from the given URL
			File dwnLoc = new File(saveLocation.concat(".part"));
			FileUtils.copyURLToFile(new URI(fileUrl).toURL(), dwnLoc);
			 // Calculate the MD5 checksum of the downloaded file
			String calculatedMd5 =
					DigestUtils.md5Hex(Files.newInputStream(dwnLoc.toPath()));
			if (calculatedMd5.equalsIgnoreCase(getExpectedMd5(fileUrl))) {
				FileUtils.copyFile(dwnLoc, savLoc);
				if (dwnLoc.exists()) {
					dwnLoc.delete();
				}
				SimpleLogger.LOG(System.out, "downloaded " + fileUrl);
				return 0;
			}
			if (dwnLoc.exists()) {
				dwnLoc.delete();
			}
			SimpleLogger.LOG(System.err, "MD5 validation failed for " + fileUrl);
			return 1;
		} catch (IOException | URISyntaxException e) {
			SimpleLogger.LOG(System.err, "Unable to connect to server");
			File dwnLoc = new File(saveLocation.concat(".part"));
			if (dwnLoc.exists()) {
				dwnLoc.delete();
			}
			System.err.println(e);
			return 1;
		}
	}

	/**
	 * Gets the precomputed MD5 checksum for a file at the corresponding file.md5.
	 * @param fileUrl	URL to file on server to find checksum for
	 * @return			String of precomputed MD5 from the md5 file on server.
	 * 					Returns an empty string if not found.
	 */
	private static String getExpectedMd5(String fileUrl) {
		try {
			URI uri = new URI(fileUrl.concat(".md5"));
			InputStream inputStream = uri.toURL().openStream();
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		} catch (URISyntaxException | IOException e) {
			SimpleLogger.LOG(System.err, "Could not find precomputed Md5 checksum for " + fileUrl);
			System.err.println(e);
			return "";
		}
	}
}
