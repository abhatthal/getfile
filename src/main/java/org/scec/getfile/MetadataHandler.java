package org.scec.getfile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Singleton MetadataHandler handles metadata IO on server and client.
 * One shared instance is used for data access across GetFile classes.
 */
public class MetadataHandler {
	/**
	 * Reads file metadata from server and client and writes client metadata
	 * as new files are downloaded. One time initialization
	 * @param serverPath		Server metadata and new files found here
	 * @param clientPath		Local metadata and downloaded files here
	 */
	public void init(String serverPath, String clientPath) {
		this.serverPath = serverPath;
		this.clientPath = clientPath;
		loadClientMeta();
		// Get a fresh copy of the latest file versions
		final File cachedServerMetaFile = new File(
				clientPath.concat(serverMetaName));
		final File freshServerMetaFile = new File(
				clientPath.concat("." + serverMetaName));
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
		this.serverMeta = parseJson(cachedServerMetaFile.getPath());
		this.initialized = true;
	}

	/**
	 * Get an instance of MetadataHandler and create one if there isn't any
	 * Requires invoking the init method at least once prior to use.
	 * @return
	 */
	public static MetadataHandler getInstance() {
		if (instance == null) {
			instance = new MetadataHandler();
		}
		return instance;
	}
	
	/**
	 * Private constructor ensures only made via getInstance() followed by init().
	 */
	private MetadataHandler() {};
	
	/**
	 * Checks if the MetadaHandler has invoked the init method.
	 * @return
	 */
	private boolean isInitialized() {
		if (!initialized) {
			SimpleLogger.LOG(System.err, "MetadataHandler singleton not initialized.");
		}
		return initialized;
	}
	
	/**
	 * Read cached file metadata from server
	 * @param file		Key in the meta.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	public String getServerMeta(String file, String key) {
		return getMetaImpl(file, key, serverMeta);
	}
	
	/**
	 * Get keynames for files on server
	 * @return
	 */
	public Set<String> getServerFiles() {
		return serverMeta.keySet();
	}
	
	/**
	 * Get keynames for files on client
	 * @return
	 */
	public Set<String> getClientFiles() {
		return clientMeta.keySet();
	}
	
	/**
	 * Loads client metadata from file into memory.
	 * Can be done multiple times to load fresh changes made directly to file.
	 */
	public void loadClientMeta() {
		if (clientPath == null) {
			SimpleLogger.LOG(
					System.err, "clientPath hasn't been set. Can't load client metadata");
		}
		this.clientMeta = parseJson(clientPath.concat(clientMetaName));	
	}

	/**
	 * Read file metadata from client
	 * @param file		Key in the getfile.json file. Not necessarily filename.
	 * @param key		Filedata to lookup, i.e. path, version
	 * @return			Value corresponding to key in JSON
	 */
	public String getClientMeta(String file, String key) {
		return getMetaImpl(file, key, clientMeta);
	}

	/**
	 * Getter for root path to server files
	 * @return
	 */
	public String getServerPath() {
		if (!isInitialized()) return "";
		return serverPath;
	}
	
	/**
	 * Getter for root path to client files
	 * @return
	 */
	public String getClientPath() {
		if (!isInitialized()) return "";
		return clientPath;
	}
	
	/**
	 * Getter for name of metadata JSON file on client
	 * @return
	 */
	public final String getClientMetaName() {
		return clientMetaName;
	}
	
	/**
	 * Getter for name of metadata JSON file on server
	 * @return
	 */
	public final String getServerMetaName() {
		return serverMetaName;
	}
	
	/**
	 * Set file[key] = value in client metadata in both memory and disk.
	 * Used to update client file version after successful update.
	 * This requires the file entry to exist. File entries are created with
	 * the function `newClientEntry`.
	 * @param file
	 * @param key
	 * @param value
	 */
	public void setClientMeta(String file, String key, String value) {
		if (!isInitialized()) return;
		try {
			// Update the file value in memory
			((JsonObject) clientMeta.get(file)).addProperty(key, value);
			// Write the new JSON to disk
			writeClientMetaState();
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
	public void newClientEntry(String file) {
		if (!isInitialized()) return;
		JsonObject newFileEntry = new JsonObject();
		newFileEntry.addProperty("version", "");
		// newFileEntry.addProperty("prompt", String.valueOf(promptByDefault));
		clientMeta.add(file, newFileEntry);
		writeClientMetaState();
	}
	
	/**
	 * Push current state of clientMeta in memory to the client meta file on disk.
	 */
	private void writeClientMetaState() {
		if (!isInitialized()) return;
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			FileWriter writer = new FileWriter(clientPath.concat(clientMetaName));
			gson.toJson(clientMeta, writer);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			SimpleLogger.LOG(System.err, "Failed to write clientMeta to disk");
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
		if (!isInitialized()) return "";
		try {
			return ((JsonObject) meta.get(file))
				.get(key).toString().replaceAll("\"", "");
		} catch (NullPointerException e) {
			SimpleLogger.LOG(System.err, file+"." + key + " not found in meta"); 
			return "";
		}
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
			SimpleLogger.LOG(System.err, "FileNotFound " + jsonFile);
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			SimpleLogger.LOG(System.err, "UnsupportedEncoding");
		} catch (IOException e) {
			SimpleLogger.LOG(System.err, "IOException");
			e.printStackTrace();
		}
		return json;
	}
	
	// Instance of Metadatahandler singleton
	private static MetadataHandler instance;
	private boolean initialized = false;  // Use IsInitialized() for check
	// Names of metadata files
	private String clientMetaName = "getfile.json";
	private final String serverMetaName = "meta.json";
	private String serverPath;
	private String clientPath;
	// Parsed metadata objects
	private JsonObject serverMeta;
	private JsonObject clientMeta;

}
