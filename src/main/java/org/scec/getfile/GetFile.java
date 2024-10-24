package org.scec.getfile;

//import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//import com.google.gson.stream.JsonReader;
//import com.google.gson.stream.JsonToken;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
//import java.io.FileReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


/**
 * A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	public GetFile(String server, String getfileJson) {
		// Read the local getfile json to get current file versions.
		local_meta_ = parseJson(getfileJson);	
		// Get a fresh copy of the latest file versions
		downloadFile(server.concat("meta.json"), "meta.json", /*retries=*/3);
		server_meta_ = parseJson("meta.json");
		if (server_meta_ == null) {
			System.err.println("Unable to get fileserver metadata. Not updating files.");
			return;
		}
		
	}
	
	/**
	 * Update all local files using new server files.
	 * @return 0 if success and 1 if any failure
	 */
	public int updateAll() {
		// For each file in local_meta
		//  * if file["version"] != latestVersion in server_meta_
		//    * if file["uploadType"]==manual
		//      then promptDownload
		//    * download directly from server_meta file path if automatic
		//	  * throw error if other uploadType
		
		// https://stackoverflow.com/a/10593838
//		Iterator<String> keys = local_meta_.keys();
		return 1;  // TODO
	}
	
	/**
	 * Read a JSON file into memory for evaluation
	 * @param jsonFile		Path to a JSON file
	 * @return
	 */
	private JsonObject parseJson(String jsonFile) {
		// https://stackoverflow.com/a/62106829
		JsonObject json = null;
		try (Reader reader = new InputStreamReader(
				new FileInputStream(jsonFile), "UTF-8")) {
		    json = (JsonObject) JsonParser.parseReader(reader);
		} catch (FileNotFoundException e) {
			System.err.println("GetFile.parseJson FileNotFound");
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			System.err.println("GetFile.parseJson UnsupportedEncoding");
			throw new RuntimeException(e);
		} catch (IOException e) {
			System.err.println("GetFile.parseJson IOException");
			throw new RuntimeException(e);
		}
		return json;
	}
	
	/**
	 * Helper method to read file metadata from server
	 * @param file		Key in the meta.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	public String getServerMeta(String file, String key) {
		return getMetaImpl(file, key, server_meta_);
	}
	
	/**
	 * Helper method to read file metadata from client
	 * @param file		Key in the getfile.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	public String getClientMeta(String file, String key) {
		return getMetaImpl(file, key, local_meta_);
	}
	/**
	 * Shared logic for getServerFileVal and getClientFileVal
	 * @param file		Key in the meta JSON file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @param meta		Which metadata to consider
	 * @return
	 */
	private String getMetaImpl(String file, String key, JsonObject meta) {
		return ((JsonObject) meta.get(file))
			.get(key).toString().replaceAll("\"", "");
	}
	
	/**
	 * Downloads a file with MD5 validation
	 * @param fileUrl				URL of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @return 0 if success and 1 if any failure
	 */
	private int downloadFile(String fileUrl, String saveLocation) {
		try {
			// Download the file from the given URL
			File file = new File(".".concat(saveLocation));
			FileUtils.copyURLToFile(new URI(fileUrl).toURL(), file);
			 // Calculate the MD5 checksum of the downloaded file
			String calculatedMd5 =
					DigestUtils.md5Hex(Files.newInputStream(file.toPath()));
			if (calculatedMd5.equalsIgnoreCase(getExpectedMd5(fileUrl))) {
				// Move hidden file to overwrite.
				FileUtils.copyFile(file, new File(saveLocation));
				file.delete();
				System.out.printf(
						"GetFile.downloadFile updated %s from %s\n",
						saveLocation, fileUrl);
				return 0;
			}
			if (file.exists()) {
				file.delete();
			}
			System.err.printf("GetFile.downloadFile MD5 validation failed: %s\n", fileUrl);
			return 1;
		} catch (IOException | URISyntaxException e) {
			System.err.println("GetFile.downloadFile Unable to connect to server");
			throw new RuntimeException(e);
			
		}
	}
	
	/**
	 * Gets the precomputed MD5 checksum for a file at the corresponding file.md5.
	 * @param fileUrl	URL to file on server to find checksum for
	 * @return			String of precomputed MD5 from the md5 file on server.
	 */
	private String getExpectedMd5(String fileUrl) {
		try {
			URI uri = new URI(fileUrl.concat(".md5"));
			InputStream inputStream = uri.toURL().openStream();
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		} catch (URISyntaxException | IOException e) {
			System.err.printf(
					"GetFile.getExpectedMd5 Could not find precomputed Md5 checksum for %s\n",
					fileUrl);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Retry download until it succeeds or `retries` attempts exceeded.
	 * If retries is not specified, defaults to 1 attempt.
	 * @param fileUrl				URL of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @param retries				Count of retry attempts
	 * @return						0 if success and 1 if reached n executions
	 */
	private int downloadFile(String fileUrl, String saveLocation, int retries) {
		int status = 1;
		for (int i = 0; i < retries && status != 0; i++) {
			status = downloadFile(fileUrl, saveLocation);
		}
		return status;
	}
	
	/**
	 * Prompt user with JOptionPane if they want to update to latest version of file
	 * @return true if we should download the latest version of this file
	 */
	private boolean promptDownload() {
		// In the event of a manual update type, prompt the user prior to download
		// "Would you like to update `file` version to latestVersion now?"
		// "Update Now" ,"Later", "Skip this Version"
		// If we want to be able to skip versions,
		// we need to keep a JsonArray of skipped versions inside getfile.json
		return true;  // TODO
	}
	
	private JsonObject server_meta_;
	private JsonObject local_meta_;
	
}
