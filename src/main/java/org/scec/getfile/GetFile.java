package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.ImmutablePair;

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
	 * @return mapping of updated and unchanged files to list of file objects
	 */
	public Map<String, List<File>> updateAll() {
		// Don't attempt to update files that were removed from server
		new DeleteFile(meta).deleteMissingFiles();
		// Create map to store updated and unchanged files
		Map<String, List<File>> result = new HashMap<>();
		result.put("updated", new ArrayList<File>());
		result.put("unchanged", new ArrayList<File>());
		// Iterate over the files on the server
        for (String fileKey : meta.getServerFiles()) {
        	Pair<Boolean, File> pair = updateFile(fileKey);
        	// Add File object to one of the updated or unchanged lists
			result.get(pair.getLeft()
					? "updated"
					: "unchanged").add(pair.getRight());
        }
        return result;
	}
	
	/**
	 * Updates a specific file.
	 * A file is considered updated if the version is changed.
	 * Path changes are not considered an update.
	 * @param fileKey			Name of key corresponding to file to try downloading
	 * @return boolean if file is updated or unchanged, Reference to file updated
	 */
	public Pair<Boolean, File> updateFile(String fileKey) {
		final String serverVersion = meta.getServerMeta(fileKey, "version");
		final String clientVersion = meta.getClientMeta(fileKey, "version");
		// Handle if file doesn't exist on server
		if (serverVersion.equals("")) {
			SimpleLogger.LOG(System.err,
					"File key \"" + fileKey + "\" does not exist in server meta");
			return ImmutablePair.of(false, null);
		}
		// Create the file entry if it doesn't already exist
		if (clientVersion.equals("")) {
			meta.newClientEntry(fileKey);
		}
		File file = updatePath(fileKey);
		if (clientVersion.equals(serverVersion)) {
			SimpleLogger.LOG(System.out,
					"File \"" + fileKey + "\" is already up to date.");
			return ImmutablePair.of(false, file);
		}
		boolean shouldPrompt = prompter.shouldPrompt(fileKey);
		if ((shouldPrompt && prompter.promptDownload(fileKey)) || !shouldPrompt) {
			SimpleLogger.LOG(System.out,
					"Update " + fileKey + " " + clientVersion + " => " + serverVersion);
			// Download and validate the new file from the server
			Path downloadLoc = Paths.get(
					meta.getClientMetaFile().getParent(),
					meta.getClientMeta(fileKey, "path"));
			URI serverLoc = URI.create(
					meta.getServerPath().toString().concat(
							meta.getServerMeta(fileKey, "path")));
			int status = Downloader.downloadFile(serverLoc, downloadLoc);
			if (status == 0) {
				// Update the client meta version accordingly
				meta.setClientMeta(fileKey, "version", serverVersion);
				return ImmutablePair.of(true, file);
			}
		}
		SimpleLogger.LOG(System.err, "Failed to download " + fileKey);
		return ImmutablePair.of(false, file);
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
	
	/**
	 * If the serverPath and clientPath mismatch, then the file location was
	 * updated and the client file location should be updated accordingly.
	 * Invoked in updateFile regardless of if file is outdated.
	 * @param file		Name of file key in metadata
	 * @return File object with updated or unchanged path
	 */
	private File updatePath(String file) {
		String serverPath = meta.getServerMeta(file, "path");
		String clientPath = meta.getClientMeta(file, "path");
		String root = meta.getClientMetaFile().getParent();
		File oldLoc = new File(root, clientPath);
		File newLoc = new File(root, serverPath);
		if (clientPath.equals(serverPath)) {
			return oldLoc;
		}
		if (oldLoc.exists()) {
			try {
				FileUtils.moveFile(oldLoc, newLoc);
				meta.setClientMeta(file, "path", serverPath);
				SimpleLogger.LOG(System.out,
						"Updated " + file + " path " + oldLoc + " => " + newLoc);
				if (oldLoc.getParent() != null) {
					DeleteFile.deleteIfEmpty(Paths.get(oldLoc.getParent()));
				}
			} catch (IOException e) {
				SimpleLogger.LOG(System.err,
						"Failed to update file path " + oldLoc + " => " + newLoc);
				e.printStackTrace();
			}
		}
		return newLoc;
	}

	/**
	 * Store the current URL where the latest code is found. This allows us to update
	 * the endpoint without manually editing each client that invokes GetFile for self-updating.
	 */
	public static final String LATEST_JAR_URL =
			"https://raw.githubusercontent.com/abhatthal/getfile/refs/heads/main/libs/libs.json";
	// GetFile metadata is stored in MetadataHandler to pass data into utility classes.
	final MetadataHandler meta;
	// Each GetFile instance can have multiple concurrent backups. 1-many relationship via Map.
	private final Map<String, BackupManager> backups;
	// Each GetFile instance has its own Prompter with default user prompting behavior.
	private final Prompter prompter;
}
