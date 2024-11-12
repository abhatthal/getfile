package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;

/**
 * A class to manage backups and rollbacks. A backup creates a snapshot of the
 * current data and metadata that can be rolled back to. Each BackupManager is
 * capable of managing one backup and its corresponding rollback.
 */
public class BackupManager {
	
	/**
	 * A constructor for the BackupManager takes a String identifier, to uniquely
	 * identify the backup and rollback accordingly. The identifier is simply appended
	 * to the end of the file name. If two instances are made with the same identifier
	 * then they will overwrite any existing backup.
	 * @param identifier
	 */
	public BackupManager(String identifier) {
		// Warn user that this identifier was already created this session.
		// We don't prevent instantiation as it simply overwrites existing backups
		// created by another BackupManager.
		if (identifiers.contains(identifier)) {
			SimpleLogger.LOG(System.err,
					"Warning: The BackupManager identifier=" + identifier +
					" is already in use");
		}
		identifiers.add(identifier);
		if (!identifier.equals("")) {
			identifier = "-" + identifier;
		}
		this.identifier = ".bak".concat(identifier);
		this.meta = MetadataHandler.getInstance();
	}
	/**
	 * The constructor without an identifier passed assumes an empty string.
	 */
	public BackupManager() {
		this("");
	}
	
	/**
	 * Get identifier for this backup manager
	 * @return
	 */
	public final String getIdentifier() {
		return identifier;
	}
	
	/**
	 * Backups up all files and metadata. Rollback invocation will return to this state.
	 * Rollbacks do nothing if no backup exists. Backups persist across GetFile instances.
	 */
	public void backup() {
		if (!meta.isInitialized()) return;
		backupFile(meta.getClientPath().concat(meta.getClientMetaName()));
        for (String file : meta.getClientFiles()) {
			String filePath =
					meta.getClientPath().concat(meta.getServerMeta(file, "path"));
			backupFile(filePath);
        }
	}
	
	/**
	 * Rollback to state when backup was last invoked.
	 * A rollback consumes the backup, requiring a second backup to rollback
	 * with the same BackupManager instance.
	 * @return 0 if success and 1 if unable to rollback.
	 */
	public int rollback() {
		if (!backupExists()) {
			SimpleLogger.LOG(System.err, "No backup snapshot found for rollback");
			return 1;
		}
		int status = 0;
		// Iterate over the local files to potentially rollback.
		final String clientPath = meta.getClientPath();
        for (String file : meta.getClientFiles()) {
				String filePath = clientPath.concat(meta.getServerMeta(file, "path"));
        	try {
				File savLoc = new File(filePath);
				File bakLoc = new File(filePath.concat(identifier));
				if (!savLoc.exists() && !bakLoc.exists()) {
					SimpleLogger.LOG(System.err,
							"Tracked file is missing. Skipping " + filePath);
					status = 1;
					continue;
				}
				if (bakLoc.exists()) {
					// Rollback files that have a backup
					if (savLoc.exists()) {
						FileUtils.delete(savLoc);
					}
					FileUtils.moveFile(bakLoc, savLoc);
					SimpleLogger.LOG(System.out, "rolled back " + file);
				} else if (savLoc.exists()) {
					// Delete tracked files that don't have a backup
					FileUtils.delete(savLoc);
					SimpleLogger.LOG(System.out, "deleted " + file);
				}
			} catch (IOException e) {
						SimpleLogger.LOG(System.err, "Failed to rollback " + filePath);
				status = 1;
				e.printStackTrace();
			}
        }
        deleteEmptyDirs(Paths.get(clientPath));
        // Rollback the local meta itself
        File clientMetaFile = new File(clientPath.concat(meta.getClientMetaName()));
        File clientMetaBak = new File(clientMetaFile.getPath().concat(identifier));
        if (clientMetaFile.exists() && clientMetaBak.exists()) {
        	try {
        		clientMetaFile.delete();
				FileUtils.moveFile(clientMetaBak, clientMetaFile);
				SimpleLogger.LOG(System.out, "rolled back local meta");
        	} catch (IOException e) {
				SimpleLogger.LOG(System.err, "Failed to read local meta files");
				e.printStackTrace();
				status = 1;
        	}
        } else {
				SimpleLogger.LOG(System.err, "Failed to rollback local meta");
				status = 1;
        }
        // Load the client meta into memory
        meta.loadClientMeta();
        return status;
	}
	
	/**
	 * Backs up file if it exists
	 * @param filePath
	 */
	private void backupFile(String filePath) {
		File file = new File(filePath);
		File bak = new File(filePath.concat(identifier));
		if (file.exists()) {
			try {
				if (bak.exists()) {
					FileUtils.delete(bak);
				}
				FileUtils.copyFile(file, new File(filePath.concat(identifier)));
			} catch (IOException e) {
				SimpleLogger.LOG(System.out, "Refused to backup " + filePath);
				e.printStackTrace();
			}
		}
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
			            SimpleLogger.LOG(System.out, "Deleted empty directory: " + dir);
			        }
			    } catch (IOException e) {
					SimpleLogger.LOG(System.out, "Error deleting directory " + dir + ": " + e.getMessage());
			    }
			});
		} catch (IOException e) {
			SimpleLogger.LOG(System.out, "Root directory not found " + directory);
			e.printStackTrace();
		}	
	}
	
	/**
	 * Returns true if there exists a backup for current identifier.
	 * @return
	 */
	private boolean backupExists() {
		if (!meta.isInitialized()) return false;
		File metaBak = new File(
				meta.getClientPath().concat(meta.getClientMetaName() + identifier));
		return metaBak.exists();
	}
	
	private final String identifier;
	private static final Set<String> identifiers = new HashSet<>();
	private MetadataHandler meta;
}
