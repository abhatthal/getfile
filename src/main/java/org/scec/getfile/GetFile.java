package org.scec.getfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.InputStreamReader;

// TODO: Update traces using Java lang.reflect.Method instead of hardcoded
// TODO: Set up modules to make utility classes private outside JAR

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
		Downloader.downloadFile(serverPath.concat(serverMetaName),
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
			this.serverMeta = null;
			e.printStackTrace();
		}
	}
	
	/**
	 * Update all local files using new server files.
	 * This will force an update regardless of if there are any changes.
	 * @return 0 if success and 1 if any failure
	 */
	public int updateAll() {
		if (clientMeta == null || serverMeta == null) {
			System.err.println("GetFile.updateAll: Unable to get metadata. Not updating files.");
			return 1;
		}
		int status = 0;  // Returns 0 if no errors
		// Iterate over the files on the server
        for (java.util.Map.Entry<String, JsonElement> entry : serverMeta.entrySet()) {
            String file = entry.getKey();
            if (updateFile(file) == 1) {
            	status = 1;
            }
        }
		return status;
	}
	
	/**
	 * Backups up all files and metadata. Rollback invocation will return to this state.
	 * Rollbacks do nothing if no backup exists. Backups persist across GetFile instances.
	 */
	public void backup() {
		backupFile(clientPath.concat(clientMetaName));
        for (java.util.Map.Entry<String, JsonElement> entry : clientMeta.entrySet()) {
        	String file = entry.getKey();
			String filePath = clientPath.concat(getServerMeta(file, "path"));
			backupFile(filePath);
        }
	}
	
	/**
	 * Updates a specific file. If a new update is available, backs up previous.
	 * @param file			Name of key corresponding to file to try downloading
	 * @return				0 if success and 1 if any failure
	 */
	public int updateFile(String file) {
		if (clientMeta == null || serverMeta == null) {
			System.err.println("GetFile.updateFile: Unable to get metadata. Not updating files.");
			return 1;
		}
		String serverVersion = getServerMeta(file, "version");
		String clientVersion = getClientMeta(file, "version");
		if (clientVersion.equals(serverVersion)) {
			System.err.println("GetFile.updateFile: File is already up to date.");
			// Not updating when already up to date isn't considered a failure
			return 0;
		}
		String downloadPath = clientPath.concat(getServerMeta(file, "path"));
		// Create the file entry if it doesn't already exist
		if (clientVersion.equals("")) {
			newClientEntry(file);
			// Delete new file on rollback since there's no version previously.
			markForDeletion(downloadPath);
			
		}
		int status = 0;
		System.out.printf("GetFile.updateAll: Update %s %s => %s\n",
				file, clientVersion, serverVersion);
		String shouldPrompt = getClientMeta(file, "prompt");
		if (shouldPrompt.equals("")) {
			shouldPrompt = String.valueOf(promptByDefault);
		}
		if ((shouldPrompt.equals("true") && promptDownload()) ||
				shouldPrompt.equals("false")) {
			// Download and validate the new file from the server
			Downloader.downloadFile(serverPath.concat(getServerMeta(file, "path")),
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
		return status;
	}
	
	private boolean promptDownload() {
		// TODO: Delete this after completing Prompter class
		return false;
	}

	/**
	 * Backs up file if it exists
	 * @param filePath
	 */
	private void backupFile(String filePath) {
		File file = new File(filePath);
		File bak = new File(filePath.concat(".bak"));
		if (file.exists()) {
			try {
				if (bak.exists()) {
					FileUtils.delete(bak);
				}
				FileUtils.copyFile(file, new File(filePath.concat(".bak")));
			} catch (IOException e) {
				System.err.printf(
						"GetFile.backupFile: Refused to backup %s\n", filePath);
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * In the event that the file is rolled back, instead of restoring an older
	 * version, the file will just be deleted.
	 * This is used when newly created files should be rolled back to not existing.
	 * @param filePath
	 */
	private void markForDeletion(String filePath) {
		File bak = new File(filePath.concat(".bak"));
		if (!bak.exists()) {
			try {
				FileUtils.writeStringToFile(bak, deleteMarker, StandardCharsets.UTF_8);
				System.out.printf("GetFile.markForDeletion: Marked %s for deletionll\n", filePath);
			} catch (IOException e) {
				System.err.printf(
						"GetFile.markForDeletion: Refused to mark %s for deletion\n", filePath);
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Returns true if a file is marked for deletion and should be deleted with its backup
	 * @param filePath
	 */
	private boolean shouldDelete(String filePath) {
		File file = new File(filePath);
		File bak = new File(filePath.concat(".bak"));
		if (file.exists() && bak.exists()) {
			// Use a line iterator to avoid loading entire file in memory
			try (LineIterator it = FileUtils.lineIterator(bak)) {
				if (!it.hasNext()) {
					System.err.printf(
							"GetFile.shouldDelete: File backup empty %s\n", filePath);
					return false;
				}
				String firstLine = it.next();
				return firstLine.equals(deleteMarker);
			} catch (IOException e) {
				System.err.printf(
						"GetFile.shouldDelete: File not found %s\n", filePath);
				e.printStackTrace();
				return false;
			}
		}
		System.err.printf(
				"GetFile.shouldDelete: File not found %s\n", filePath);
		return false;
	}
	
	/**
	 * Recursively delete all empty directories inside dir.
	 * This is necessary to delete empty directories left after file deletions
	 * inside a rollback.
	 * @param directory
	 */
	private void deleteEmptyDirs(Path directory) {
		try {
			Files.walk(directory)
			.filter(Files::isDirectory)
			.sorted((p1, p2) -> p2.getNameCount() - p1.getNameCount()) // Deepest directories first
			.forEach(dir -> {
			    try {
			        if (Files.list(dir).findAny().isEmpty()) {
			            Files.delete(dir);
			            System.out.println("GetFile.deleteEmptyDirs: Deleted empty directory: " + dir);
			        }
			    } catch (IOException e) {
			        System.err.println(
			        		"GetFile.deleteEmptyDirs: Error deleting directory " + dir + ": " + e.getMessage());
			    }
			});
		} catch (IOException e) {
			System.err.println("GetFile.deleteEmptyDirs: Root directory not found " + directory);
			e.printStackTrace();
		}	
	}
	
	/**
	 * Rollback to state when GetFile was constructed
	 * @return 0 if success and 1 if unable to rollback.
	 */
	public int rollback() {
		if (clientMeta == null) {
			System.err.println("Getfile.rollback: Unable to get metadata. Not rolling back.");
			return 1;
		}
		int status = 0;
		// Iterate over the local files to potentially rollback.
        for (java.util.Map.Entry<String, JsonElement> entry : clientMeta.entrySet()) {
				String file = entry.getKey();
				String filePath = clientPath.concat(getServerMeta(file, "path"));
        	try {
				File savLoc = new File(filePath);
				File bakLoc = new File(filePath.concat(".bak"));
				if (savLoc.exists() && bakLoc.exists()) {
						if (shouldDelete(filePath)) {
							FileUtils.delete(savLoc);
							FileUtils.delete(bakLoc);
							System.out.printf(
									"GetFile.rollback: deleted marked file %s\n", file);
						} else {
							FileUtils.delete(savLoc);
							FileUtils.moveFile(bakLoc, savLoc);
							System.out.printf(
									"GetFile.rollback: rolled back %s\n", file);
						}
				}
			} catch (IOException e) {
				System.err.printf(
						"GetFile.rollback: Failed to rollback %s\n", filePath);
				status = 1;
				e.printStackTrace();
			}
        }
        deleteEmptyDirs(Paths.get(clientPath));
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
				status = 1;
        }
        // Load the client meta into memory
		this.clientMeta = parseJson(clientMetaFile.getPath());	
        return status;
	}
	
	/**
	 * Read a JSON file into memory for evaluation
	 * @param jsonFile		JSON file to parse
	 * @return				JSON tree interpreted as an object.
	 * 						Returns null if any error encountered.
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
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.err.println("GetFile.parseJson: UnsupportedEncoding");
		} catch (IOException e) {
			System.err.println("GetFile.parseJson: IOException");
			e.printStackTrace();
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
//		newFileEntry.addProperty("prompt", String.valueOf(promptByDefault));
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
			System.err.printf(
					"GetFile.getMetaImpl: %s.%s not found in meta\n", file, key);
			return "";
		}
	}
		
	
	private boolean promptByDefault = false;  // Should prompt to update a file
	private final String deleteMarker = "GetFile: File marked for deletion in rollback. DO NOT MODIFY";
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
