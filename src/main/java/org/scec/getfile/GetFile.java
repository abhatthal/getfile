package org.scec.getfile;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	 * @param clientMetaFile	Reference to local metadata file on client
	 * @param serverMetaURI		Link to hosted server metadata file to download
	 */
	public GetFile(File clientMetaFile, URI serverMetaURI) {
		this.meta = new MetadataHandler(clientMetaFile, serverMetaURI);
		this.prompter = new Prompter(meta);
		// TODO: Keep a hashmap of BackupManagers to manage multiple backups
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
	// TODO: Make meta private after move BackupManager internally
	public MetadataHandler meta;
	private Prompter prompter;
}
