package org.scec.getfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;

/**
 * MetadataHandler handles metadata IO on server and client.
 */
class MetadataHandler {
	/**
	 * Reads file metadata from server and client and writes client metadata
	 * as new files are downloaded. Reads fresh server meta on initialization.
	 * @param clientMetaFile	Reference to local metadata file on client
	 * @param serverMetaURI		Link to hosted server metadata file to download
	 */
	private MetadataHandler(File clientMetaFile, URI serverMetaURI) {
		// Read client metadata
		this.clientMetaFile = clientMetaFile;
		loadClientMeta();
		// Get a fresh copy of the latest file versions
		String serverMetaFileName;
		this.serverMetaURI = serverMetaURI;
		String path = serverMetaURI.getPath();
		serverMetaFileName = path.substring(path.lastIndexOf('/') + 1);
		File cachedServerMetaFile = new File(
				clientMetaFile.getParent(), serverMetaFileName);
		File freshServerMetaFile = new File(
				clientMetaFile.getParent(), "." + serverMetaFileName);
		int downloadStatusCode = Downloader.downloadFile(serverMetaURI,
				freshServerMetaFile.toPath(), /*retries=*/3);
		if (downloadStatusCode == 1) {
			SimpleLogger.LOG(System.err, "Failed to download server metadata at " + serverMetaURI);
			if (freshServerMetaFile.exists()) {
				freshServerMetaFile.delete();
			}
		} else {
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
					SimpleLogger.LOG(System.out, "New files are available to download.");
				} else {
					FileUtils.delete(freshServerMetaFile);
					SimpleLogger.LOG(System.out, "No new files found.");
				}
			} catch (IOException e) {
				SimpleLogger.LOG(System.err, "IOException reading cache");
				this.serverMeta = null;
				e.printStackTrace();
			}
		}
		this.serverMetaFile = cachedServerMetaFile.exists() ? cachedServerMetaFile : null;
		this.serverMeta = parseJson(serverMetaFile);
	}
	
	/**
	 * The MetadataHandlerFactory ensures that a new instance is only created
	 * if there isn't already another MetadataHandler instance for the same
	 * clientMetaFile.
	 * @param clientMetaFile
	 * @param serverMetaURI
	 * @return
	 */
	static MetadataHandler MetadataHandlerFactory(
			File clientMetaFile, URI serverMetaURI) {
		String path = clientMetaFile.getAbsolutePath();
		if (metaMap.containsKey(path)) {
			return metaMap.get(path);
		}
		MetadataHandler newInstance = new MetadataHandler(clientMetaFile, serverMetaURI);
		metaMap.put(path, newInstance);
		return newInstance;
	}

	/**
	 * Read cached file metadata from server
	 * @param file		Key in the meta.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	String getServerMeta(String file, String key) {
		return getMetaImpl(file, key, serverMeta);
	}

	/**
	 * Read file metadata from client
	 * @param file		Key in the getfile.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	String getClientMeta(String file, String key) {
		return getMetaImpl(file, key, clientMeta);
	}
	
	/**
	 * Get keynames for files on server
	 * @return
	 */
	Set<String> getServerFiles() {
		if (serverMeta == null) {
			return new HashSet<String>();
		}
		return serverMeta.keySet();
	}
	
	/**
	 * Get keynames for files on client
	 * @return
	 */
	Set<String> getClientFiles() {
		if (clientMeta == null) {
			return new HashSet<String>();
		}
		return clientMeta.keySet();
	}
	
	/**
	 * Loads client metadata from file into memory.
	 * Can be done multiple times to load fresh changes made directly to file.
	 */
	void loadClientMeta() {
		clientMeta = parseJson(clientMetaFile);
	}

	/**
	 * Get File where server metadata is cached
	 * @return
	 */
	File getServerMetaFile() {
		return serverMetaFile;
	}
	
	/**
	 * Get File where client metadata is stored
	 * @return
	 */
	File getClientMetaFile() {
		return clientMetaFile;
	}
	
	/**
	 * Get link where all server files in hosted metadata file are stored
	 * @return
	 */
	URI getServerPath() {
		// Get the path part of the URI and find the last slash index
		String path = serverMetaURI.getPath();
		int lastSlashIndex = path.lastIndexOf('/');
		// Get the parent directory path
		String dirPath = (lastSlashIndex != -1)
				? path.substring(0, lastSlashIndex + 1)
				: path;
		// Create a new URI for the directory
		return URI.create(
				serverMetaURI.getScheme() + "://"
						+ serverMetaURI.getHost()
						+ (serverMetaURI.getPort() != -1
							? ":" + serverMetaURI.getPort()
							: "")
						+ dirPath);
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
	void setClientMeta(String file, String key, String value) {
		try {
			// Update the file value in memory
			((JsonObject) clientMeta.get(file)).addProperty(key, value);
		} catch (NullPointerException e) {
			e.printStackTrace();
			SimpleLogger.LOG(System.err, "Failed to set " + file + "[" + key + "]");
		}
	}
	
	/**
	 * Create a new JsonObject for the client metadata and append the entry into
	 * both memory and disk.
	 * @param file	Name of new JsonObject entry
	 */
	void newClientEntry(String file) {
		JsonObject newFileEntry = new JsonObject();
		newFileEntry.addProperty("version", "");
		newFileEntry.addProperty("path", getServerMeta(file, "path"));
		// newFileEntry.addProperty("prompt", String.valueOf(promptByDefault));
		clientMeta.add(file, newFileEntry);
	}
	
	/**
	 * Delete from JsonObject. Used in DeleteFile class.
	 * @param file	Name of file entry
	 */
	void deleteClientEntry(String file) {
		if (clientMeta.has(file)) {
			clientMeta.remove(file);
		}
	}
	
	/**
	 * Push current state of clientMeta in memory to the client meta file on disk.
	 */
	void writeClientMetaState() {
		synchronized(getLock(clientMetaFile)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			// Parse json and merge with current meta before writing
			try (FileWriter writer = new FileWriter(clientMetaFile)) {
				gson.toJson(clientMeta, writer);
			} catch (IOException e) {
				e.printStackTrace();
				SimpleLogger.LOG(System.err, "Failed to write clientMeta to disk");
			}
		}
	}

	/**
	 * Shared logic for reading key-value pairs in file entry JsonObject.
	 * @param file		Key in the meta JSON file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @param meta		Which metadata to consider
	 * @return			Value corresponding to key in JSON or empty string if not found.
	 */
	String getMetaImpl(String file, String key, JsonObject meta) {
		try {
			return ((JsonObject) meta.get(file))
				.get(key).toString().replaceAll("\"", "");
		} catch (NullPointerException e) {
			SimpleLogger.LOG(System.err, file +"." + key + " not found in meta"); 
			return "";
		}
	}

	/**
	 * Read a JSON file into memory for evaluation
	 * @param file			JSON file to parse
	 * @return				JSON tree interpreted as an object.
	 * 						Returns null if any error encountered.
	 */
    private JsonObject parseJson(File file) {
		try (FileReader reader = new FileReader(file)) {
			// Parse JSON content as a JsonObject
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException e) {
			SimpleLogger.LOG(System.err, "Unable to parse JSON for " + file.getName());
			e.printStackTrace();
		}
		return null;
    }

	/**
	 * Returns the fileLock for the clientMetaFile
	 * @return
	 */
	private static Object getLock(File file) {
		String filePath = file.getAbsolutePath();
		if (fileLocks.containsKey(filePath)) {
			return fileLocks.get(filePath);
		}
		Object newLock = new Object();
		fileLocks.put(filePath, newLock);
		return newLock;
	}

	// Track instances of MetadataHandler for MetadataHandlerFactory
	private static final Map<String, MetadataHandler> metaMap = new HashMap<>();
	// Each unique clientMetaFile has its own FileLock. 1-1 relationship.
	private static final Map<String, Object> fileLocks = new HashMap<>();
	private URI serverMetaURI;
	// Names of metadata JSON files
	private File clientMetaFile;
	private File serverMetaFile;
	// Parsed metadata objects
	private JsonObject serverMeta;
	private JsonObject clientMeta;
}
