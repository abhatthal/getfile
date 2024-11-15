package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

/**
 * A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	/**
	 * Constructor establishes connection with server and parses local and
	 * server file metadata into memory.
	 * @param clientMetaFile	Reference to local metadata file on client
	 * @param serverMetaURI		Link to hosted server metadata file to download
	 */
	public GetFile(File clientMetaFile, URI serverMetaURI) {
		clientMetaFile = clientMetaFile.getAbsoluteFile();
		// Create an empty client meta file if it doesn't already exist.
		if (!clientMetaFile.exists()) {
			try {
				FileUtils.writeStringToFile(
						clientMetaFile, "{}", StandardCharsets.UTF_8);
			} catch (IOException e) {
				SimpleLogger.LOG(System.err,
						"Failed to create client meta file " + clientMetaFile);
				e.printStackTrace();
			}
		}
		this.meta = new MetadataHandler(clientMetaFile, serverMetaURI);
		this.prompter = new Prompter(meta);
		this.backups = new HashMap<String, BackupManager>();
	}
	
	/**
	 * Update all local files using new server files.
	 * This will force an update regardless of if there are any changes.
	 */
	public void updateAll() {
		// Iterate over the files on the server
        for (String file : meta.getServerFiles()) {
        	updateFile(file);
        }
	}
	
	/**
	 * Updates a specific file.
	 * @param file			Name of key corresponding to file to try downloading
	 */
	public void updateFile(String file) {
		final String serverVersion = meta.getServerMeta(file, "version");
		final String clientVersion = meta.getClientMeta(file, "version");
		if (clientVersion.equals(serverVersion)) {
			SimpleLogger.LOG(System.out,
					"File " + file + " is already up to date.");
			return;
		}
		// Create the file entry if it doesn't already exist
		if (clientVersion.equals("")) {
			meta.newClientEntry(file);
		}
		boolean shouldPrompt = prompter.shouldPrompt(file);
		if ((shouldPrompt && prompter.promptDownload(file)) || !shouldPrompt) {
			SimpleLogger.LOG(System.out,
					"Update " + file + " " + clientVersion + " => " + serverVersion);
			// Download and validate the new file from the server
			Path downloadLoc = Paths.get(
					meta.getClientMetaFile().getParent(),
					meta.getServerMeta(file, "path"));
			URI serverLoc = URI.create(
					meta.getServerPath().toString().concat(
							meta.getServerMeta(file, "path")));
			Downloader.downloadFile(serverLoc, downloadLoc);
			// Update the client meta version accordingly
			meta.setClientMeta(file, "version", serverVersion);
		}
	}
	
	/**
	 * Each BackupManager can take a snapshot of the current directory and rollback
	 * to that state.
	 * Multiple BackupManagers may belong to a GetFile instance,
	 * allowing for multiple backups at different states.
	 * This method allows us to get or create a BackupManager as needed.
	 * @param identifier
	 * @return corresponding BackupManager for the given unique identifier.
	 */
	public BackupManager getBackupManager(String identifier) {
		if (backups.containsKey(identifier)) {
			return backups.get(identifier);
		}
		BackupManager backup = new BackupManager(meta, identifier);
		backups.put(identifier, backup);
		return backup;
	}
	
	/**
	 * Invoke the BackupManager with an empty string identifier.
	 * @return
	 */
	public BackupManager getBackupManager() {
		return getBackupManager("");
		
	}
	private final Map<String, BackupManager> backups;
	final MetadataHandler meta;
	private final Prompter prompter;
}
