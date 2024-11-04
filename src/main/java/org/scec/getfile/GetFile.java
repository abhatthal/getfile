package org.scec.getfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

// TODO: Move download functions into DownloadUtil class
// TODO: Create a DownloadPrompt class with corresponding logic
// TODO: Add check if new meta file is different to backup.
//		 If the new meta is unchanged, then don't bother trying to update.
//		 This also requires us to keep a cached copy of the server meta
//		 and to handle failure to download meta logic.

/**
 * A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	/**
	 * Constructor establishes connection with server and parses local and
	 * server file metadata into memory.
	 * @param server			String of URL to connect to
	 * @param local_meta_path	Path to local file metadata
	 */
	public GetFile(String server, String local_meta_path) {
		// Read the local getfile json to get current file versions.
		local_meta_ = parseJson(local_meta_path);	
		local_meta_path_ = local_meta_path;
		// Get a fresh copy of the latest file versions
		final String server_meta_name = "meta.json";
		downloadFile(server.concat(server_meta_name),
				server_meta_name, /*retries=*/3);
		server_meta_ = parseJson(server_meta_name);
		// Delete cached copy of server meta as we always want fresh data.
		File server_meta_file = new File(server_meta_name);
		if (server_meta_file.exists()) {
			server_meta_file.deleteOnExit();
		}
		server_ = server;
	}
	
	/**
	 * Update all local files using new server files.
	 * @return 0 if success and 1 if any failure
	 */
	public int updateAll() {
		if (local_meta_ == null || server_meta_ == null) {
			System.err.println("GetFile.updateAll: Unable to get metadata. Not updating files.");
			return 1;
		}
		// Backup local file metadata prior to updating.
		backupFile(local_meta_path_);
		// Iterate over the local files to potentially update
        for (java.util.Map.Entry<String, JsonElement> entry : local_meta_.entrySet()) {
            String file = entry.getKey();
            String clientVersion = getClientMeta(file, "version");
            String serverVersion = getServerMeta(file, "version");
            if (serverVersion == "") {
            	System.err.println(
            			"GetFile.updateAll: File metadata found in client missing on server");
            	return 1;
            }
			// We must backup the file regardless of version to ensure matches
            // local meta backup. :. Invoking updateAll twice will purge backup.
			String local_file_path = getClientMeta(file, "path");
			backupFile(local_file_path);
			// Only download files where versions don't match.
            if (!clientVersion.equals(serverVersion)) {
            	System.out.printf("GetFile.updateAll: Update %s %s => %s\n",
            			file, clientVersion, serverVersion);
            	String updateType = getClientMeta(file, "update_type");
            	if ((updateType.equals("manual") && promptDownload()) ||
            			updateType.equals("automatic")) {
            		// Download and validate the new file from the server
            		downloadFile(server_.concat(getServerMeta(file, "path")),
            				local_file_path, /*retries=*/3);
            		// Update the client meta version accordingly
            		setClientMeta(file, "version", serverVersion);
            	} else {
            		System.err.printf(
            				"GetFile.updateAll: Invalid update_type \"%s\". " +
            				"Skip update for file \"%s\"\n",
            				updateType, file);
            		return 1;
            	}
            }
        }
		return 0;
	}
	
	/**
	 * Backs up file if it exists
	 */
	private void backupFile(String filePath) {
		File file = new File(filePath);
		if (file.exists()) {
			try {
				FileUtils.copyFile(file, new File(filePath.concat(".bak")));
			} catch (IOException e) {
				System.err.printf(
						"GetFile.backupFile: Failed to backup %s\n", filePath);
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Rollback update to last stored version.
	 * @return 0 if success and 1 if unable to rollback.
	 */
	public int rollback() {
		if (local_meta_ == null) {
			System.err.println("Getfile.rollback: Unable to get metadata. Not rolling back.");
			return 1;
		}
		// Iterate over the local files to potentially rollback.
        for (java.util.Map.Entry<String, JsonElement> entry : local_meta_.entrySet()) {
				String file = entry.getKey();
				String filePath = getClientMeta(file, "path");
        	try {
				File savLoc = new File(filePath);
				File bakLoc = new File(filePath.concat(".bak"));
				if (savLoc.exists() && bakLoc.exists()) {
						FileUtils.copyFile(bakLoc, savLoc);
						bakLoc.delete();
						System.out.printf(
								"GetFile.rollback: rolled back %s\n", file);
				}
			} catch (IOException e) {
				System.err.printf(
						"GetFile.rollback: Failed to rollback %s\n", filePath);
				e.printStackTrace();
			}
        }
        // Rollback the local meta itself
        File local_meta_file = new File(local_meta_path_);
        File local_meta_bak = new File(local_meta_path_.concat(".bak"));
        if (local_meta_file.exists() && local_meta_bak.exists()) {
        	try {
				FileUtils.copyFile(local_meta_bak, local_meta_file);
				local_meta_bak.delete();
				System.out.println("GetFile.rollback: rolled back local meta");
        	} catch (IOException e) {
				System.err.println("GetFile.rollback: Failed to read local meta files");
				e.printStackTrace();
        	}
        } else {
				System.err.println("GetFile.rollback: Failed to rollback local meta");
        }
        // Load the old local meta into memory
        local_meta_ = parseJson(local_meta_path_);
        return 0;
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
			System.err.println("GetFile.parseJson: FileNotFound");
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.err.println("GetFile.parseJson: UnsupportedEncoding");
			throw new RuntimeException(e);
		} catch (IOException e) {
			System.err.println("GetFile.parseJson: IOException");
			e.printStackTrace();
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
	 * Set file[key] = value in client metadata.
	 * Used to update client file version after successful update.
	 * @param file
	 * @param key
	 * @param value
	 */
	private void setClientMeta(String file, String key, String value) {
		try {
			// Update the file value in memory
			((JsonObject) local_meta_.get(file)).addProperty(key, value);
			// Write the new JSON to disk
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(local_meta_path_);
			gson.toJson(local_meta_, writer);
			writer.flush();
			writer.close();
		} catch (NullPointerException | IOException e) {
			e.printStackTrace();
			System.err.printf("GetFile.setClientMeta: Failed to set %s[%s]\n",
					file, key);
		}
	}

	/**
	 * Shared logic for getServerFileVal and getClientFileVal
	 * @param file		Key in the meta JSON file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @param meta		Which metadata to consider
	 * @return			Value corresponding to key in JSON or empty string if not found.
	 */
	private String getMetaImpl(String file, String key, JsonObject meta) {
		try {
			return ((JsonObject) meta.get(file))
				.get(key).toString().replaceAll("\"", "");
		} catch (NullPointerException e) {
			e.printStackTrace();
			System.err.printf(
					"GetFile.getMetaImpl: %s.%s not found in meta\n", file, key);
			return "";
		}
	}
		
	/**
	 * Downloads a file with MD5 validation
	 * @param fileUrl				URL of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @return 0 if success and 1 if any failure
	 */
	private int downloadFile(String fileUrl, String saveLocation) {
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
				System.out.printf(
						"GetFile.downloadFile: downloaded %s\n",	fileUrl);
				return 0;
			}
			if (dwnLoc.exists()) {
				dwnLoc.delete();
			}
			System.err.printf("GetFile.downloadFile: MD5 validation failed: %s\n", fileUrl);
			return 1;
		} catch (IOException | URISyntaxException e) {
			System.err.println("GetFile.downloadFile: Unable to connect to server");
			File dwnLoc = new File(saveLocation.concat(".part"));
			if (dwnLoc.exists()) {
				dwnLoc.delete();
			}
			System.err.println(e);
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
					"GetFile.getExpectedMd5: Could not find precomputed Md5 checksum for %s\n",
					fileUrl);
			System.err.println(e);
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

		// Returns false for Later or Skip options.
		// If we want to be able to skip versions,
		// we need to keep a JsonObj of skipped versions per file inside getfile.json

		// I need to manually test the behaviour of promptDownload when
		// invoked in updateAll.
		return true;  // TODO
	}
	
	private final String server_;
	private final String local_meta_path_;
	private JsonObject server_meta_;
	private JsonObject local_meta_;
	
}
