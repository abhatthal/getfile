package org.scec.getfile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

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
		this.meta = MetadataHandler.getInstance();
		this.meta.init(serverPath, clientPath);
	}
	
	/**
	 * Update all local files using new server files.
	 * This will force an update regardless of if there are any changes.
	 * @return 0 if success and 1 if any failure
	 */
	public int updateAll() {
		int status = 0;  // Returns 0 if no errors
		// Iterate over the files on the server
        for (String file : meta.getServerFiles()) {
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
		backupFile(meta.getClientPath().concat(meta.getClientMetaName()));
        for (String file : meta.getClientFiles()) {
			String filePath =
					meta.getClientPath().concat(meta.getServerMeta(file, "path"));
			backupFile(filePath);
        }
	}
	
	/**
	 * Updates a specific file.
	 * @param file			Name of key corresponding to file to try downloading
	 * @return				0 if success and 1 if any failure
	 */
	public int updateFile(String file) {
		final String serverVersion = meta.getServerMeta(file, "version");
		final String clientVersion = meta.getClientMeta(file, "version");
		final String clientPath = meta.getClientPath();
		final String serverPath = meta.getServerPath();

		if (clientVersion.equals(serverVersion)) {
			SimpleLogger.LOG(System.out, "File is already up to date.");
			// Not updating when already up to date isn't considered a failure
			return 0;
		}
		String downloadPath = clientPath.concat(meta.getServerMeta(file, "path"));
		// Create the file entry if it doesn't already exist
		if (clientVersion.equals("")) {
			meta.newClientEntry(file);
			// Delete new file on rollback since there's no version previously.
			markForDeletion(downloadPath);
			
		}
		int status = 0;
		SimpleLogger.LOG(System.out,
				"Update " + file + " " + clientVersion + " => " + serverVersion);
		// TODO: Move shouldPrompt logic into Prompter class
		String shouldPrompt = meta.getClientMeta(file, "prompt");
		if (shouldPrompt.equals("")) {
			shouldPrompt = String.valueOf(promptByDefault);
		}
		if ((shouldPrompt.equals("true") && Prompter.promptDownload(file)) ||
				shouldPrompt.equals("false")) {
			// Download and validate the new file from the server
			Downloader.downloadFile(serverPath.concat(meta.getServerMeta(file, "path")),
					downloadPath, /*retries=*/3);
			// Update the client meta version accordingly
			meta.setClientMeta(file, "version", serverVersion);
		} else {
			SimpleLogger.LOG(System.err, "Invalid prompt " + shouldPrompt +
					". Skip update for file " + file);
			status = 1;
		}
		return status;
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
				SimpleLogger.LOG(System.out, "Refused to backup " + filePath);
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
				SimpleLogger.LOG(System.out, "Marked " + filePath + " for deletion");
			} catch (IOException e) {
				SimpleLogger.LOG(System.err, "Refused to mark " + filePath + " for deletion");
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
					SimpleLogger.LOG(System.out, "File backup empty for " + filePath);
					return false;
				}
				String firstLine = it.next();
				return firstLine.equals(deleteMarker);
			} catch (IOException e) {
				SimpleLogger.LOG(System.err, "FileNotFound " + filePath);
				e.printStackTrace();
				return false;
			}
		}
		SimpleLogger.LOG(System.err, "FileNotFound " + filePath);
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
	 * Rollback to state when GetFile was constructed
	 * @return 0 if success and 1 if unable to rollback.
	 */
	public int rollback() {
		int status = 0;
		// Iterate over the local files to potentially rollback.
		final String clientPath = meta.getClientPath();
        for (String file : meta.getClientFiles()) {
				String filePath = clientPath.concat(meta.getServerMeta(file, "path"));
        	try {
				File savLoc = new File(filePath);
				File bakLoc = new File(filePath.concat(".bak"));
				if (savLoc.exists() && bakLoc.exists()) {
						if (shouldDelete(filePath)) {
							FileUtils.delete(savLoc);
							FileUtils.delete(bakLoc);
							SimpleLogger.LOG(System.out, "deleted marked file " + file);
						} else {
							FileUtils.delete(savLoc);
							FileUtils.moveFile(bakLoc, savLoc);
							SimpleLogger.LOG(System.out, "rolled back " + file);
						}
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
        File clientMetaBak = new File(clientMetaFile.getPath().concat(".bak"));
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
	
		
	
	private boolean promptByDefault = false;  // TODO: Transition to using Prompter class
	private final String deleteMarker = "GetFile: File marked for deletion in rollback. DO NOT MODIFY";
	private MetadataHandler meta;
}
