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
// TODO: See missing tests for GetFileTest

/**
 * A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	/**
	 * Constructor establishes connection with server and parses local and
	 * server file metadata into memory.
	 * @param serverPath		Server metadata and new files found here
	 * @param clientPath		Local metadata and downloaded files here
	 */
	public GetFile(String serverPath, String clientPath) {
		this.serverPath = serverPath;
		this.clientPath = clientPath;
		// Get current file versions
		this.clientMeta = parseJson(clientPath.concat(clientMetaName));	
		// Get a fresh copy of the latest file versions
		final File cachedServerMetaFile = new File(clientPath.concat(serverMetaName));
		final File freshServerMetaFile = new File(clientPath.concat("."+serverMetaName));
		downloadFile(serverPath.concat(serverMetaName),
				freshServerMetaFile.getPath(), /*retries=*/3);
		try {
			// Proceed with download if no cache hit
			if (!cachedServerMetaFile.exists() ||
				!FileUtils.contentEquals(
						cachedServerMetaFile, freshServerMetaFile)) {
				// Overwrite cache with fresh data
				if (cachedServerMetaFile.exists()) {
					FileUtils.delete(cachedServerMetaFile);
				}
				FileUtils.moveFile(freshServerMetaFile, cachedServerMetaFile);
				System.out.println("GetFile.GetFile: New files are available to download.");
			} else {
				FileUtils.delete(freshServerMetaFile);
				System.out.println("GetFile.GetFile: No new files found.");
			}
			this.serverMeta = parseJson(cachedServerMetaFile.getPath());
		} catch (IOException e) {
			System.err.println("GetFile.GetFile: IOException reading cache");
			e.printStackTrace();
		}
	}
	
	/**
	 * Update all local files using new server files.
	 * @return 0 if success and 1 if any failure
	 */
	public int updateAll() {
		if (clientMeta == null || serverMeta == null) {
			System.err.println("GetFile.updateAll: Unable to get metadata. Not updating files.");
			return 1;
		}
		int status = 0;  // Returns 0 if no errors
		// Backup local file metadata prior to updating.
		backupFile(clientPath.concat(clientMetaName));
		// Iterate over the files on the server
        for (java.util.Map.Entry<String, JsonElement> entry : serverMeta.entrySet()) {
            String file = entry.getKey();
            String serverVersion = getServerMeta(file, "version");
            String clientVersion = getClientMeta(file, "version");
            System.out.printf("%s %s %s\n", file, serverVersion, clientVersion);
            // Create the file entry if it doesn't already exist
            if (clientVersion.equals("")) {
            	newClientEntry(file);
            }
			String downloadPath = clientPath.concat(getServerMeta(file, "path"));
			backupFile(downloadPath);
			// Only download files where versions don't match.
            if (!clientVersion.equals(serverVersion)) {
            	System.out.printf("GetFile.updateAll: Update %s %s => %s\n",
            			file, clientVersion, serverVersion);
            	String shouldPrompt = getClientMeta(file, "prompt");
            	if (shouldPrompt.equals("")) {
            		shouldPrompt = String.valueOf(promptByDefault);
            	}
            	if ((shouldPrompt.equals("true") && promptDownload()) ||
            			shouldPrompt.equals("false")) {
            		// Download and validate the new file from the server
            		downloadFile(serverPath.concat(getServerMeta(file, "path")),
            				downloadPath, /*retries=*/3);
            		// Update the client meta version accordingly
            		setClientMeta(file, "version", serverVersion);
            	} else {
            		System.err.printf(
            				"GetFile.updateAll: Invalid prompt \"%s\". " +
            				"Skip update for file \"%s\"\n",
            				shouldPrompt, file);
            		status = 1;
            	}
            }
        }
		return status;
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
						"GetFile.backupFile: Refused to backup %s\n", filePath);
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Rollback update to last stored version.
	 * @return 0 if success and 1 if unable to rollback.
	 */
	public int rollback() {
		if (clientMeta == null) {
			System.err.println("Getfile.rollback: Unable to get metadata. Not rolling back.");
			return 1;
		}
		// Iterate over the local files to potentially rollback.
        for (java.util.Map.Entry<String, JsonElement> entry : clientMeta.entrySet()) {
				String file = entry.getKey();
				String filePath = clientPath.concat(getServerMeta(file, "path"));
        	try {
				File savLoc = new File(filePath);
				File bakLoc = new File(filePath.concat(".bak"));
				if (savLoc.exists() && bakLoc.exists()) {
						FileUtils.delete(savLoc);
						FileUtils.moveFile(bakLoc, savLoc);
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
        File clientMetaFile = new File(clientPath.concat(clientMetaName));
        File clientMetaBak = new File(clientMetaFile.getPath().concat(".bak"));
        if (clientMetaFile.exists() && clientMetaBak.exists()) {
        	try {
        		clientMetaFile.delete();
				FileUtils.moveFile(clientMetaBak, clientMetaFile);
				System.out.println("GetFile.rollback: rolled back local meta");
        	} catch (IOException e) {
				System.err.println("GetFile.rollback: Failed to read local meta files");
				e.printStackTrace();
        	}
        } else {
				System.err.println("GetFile.rollback: Failed to rollback local meta");
        }
        // Load the client meta into memory
		this.clientMeta = parseJson(clientMetaFile.getPath());	
        return 0;
	}
	
	/**
	 * Read a JSON file into memory for evaluation
	 * @param jsonFile		JSON file to parse
	 * @return				JSON tree interpreted as an object
	 */
	private JsonObject parseJson(String jsonFile) {
		// https://stackoverflow.com/a/62106829
		JsonObject json = null;
		try (Reader reader = new InputStreamReader(
				new FileInputStream(jsonFile), StandardCharsets.UTF_8)) {
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
		return getMetaImpl(file, key, serverMeta);
	}
	
	/**
	 * Helper method to read file metadata from client
	 * @param file		Key in the getfile.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	public String getClientMeta(String file, String key) {
		return getMetaImpl(file, key, clientMeta);
	}
	
	/**
	 * Set file[key] = value in client metadata.
	 * Used to update client file version after successful update.
	 * This requires the file entry to exist. File entries are created with
	 * the function `newClientEntry`.
	 * @param file
	 * @param key
	 * @param value
	 */
	private void setClientMeta(String file, String key, String value) {
		try {
			// Update the file value in memory
			((JsonObject) clientMeta.get(file)).addProperty(key, value);
			// Write the new JSON to disk
			writeClientMetaState();
		} catch (NullPointerException e) {
			e.printStackTrace();
			System.err.printf("GetFile.setClientMeta: Failed to set %s[%s]\n",
					file, key);
		}
	}
	
	/**
	 * Create a new JsonObject for the client metadata.
	 * When a new file is discovered on the server, we need to start tracking
	 * client versions and update our client metadata accordingly.
	 * @param file	Name of new JsonObject entry
	 */
	private void newClientEntry(String file) {
		JsonObject newFileEntry = new JsonObject();
		newFileEntry.addProperty("version", "");
		newFileEntry.addProperty("prompt", String.valueOf(promptByDefault));
		System.out.println(newFileEntry.toString());
		clientMeta.add(file, newFileEntry);
		writeClientMetaState();
	}
	
	/**
	 * Push current state of clientMeta in memory to the client meta file on disk.
	 */
	private void writeClientMetaState() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			FileWriter writer = new FileWriter(clientPath.concat(clientMetaName));
			gson.toJson(clientMeta, writer);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(
					"GetFile.writeClientMetaState: Failed to write clientMeta to disk");
		}
	}

	/**
	 * Shared logic for reading key-value pairs in file entry JsonObject.
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
	
	private boolean promptByDefault = false;  // Should prompt to update a file
	// Names of metadata files
	private final String clientMetaName = "getfile.json";
	private final String serverMetaName = "meta.json";
	// Where to find metadata and data files on client and server
	private final String serverPath;
	private final String clientPath;
	// Parsed metadata objects
	private JsonObject serverMeta;
	private JsonObject clientMeta;
}
