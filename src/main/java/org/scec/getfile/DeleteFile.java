package org.scec.getfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Logic for deletion of files
 */
class DeleteFile {
	private MetadataHandler meta;
	
	/**
	 * Constructor. DeleteFile contains static methods that do not depend
	 * on instance metadata. Such methods generally apply to the deletion of
	 * directories. DeleteFile instances operate on metadata to selectively
	 * delete files.
	 * @param meta
	 */
	DeleteFile(MetadataHandler meta) {
		this.meta = meta;
	}
	
	/**
	 * Deletes all client files missing from server and update meta accordingly.
	 */
	void deleteMissingFiles() {
		Set<String> missingFiles = new HashSet<String>(meta.getClientFiles());
		missingFiles.removeAll(meta.getServerFiles());
		String root = meta.getClientMetaFile().getParent();
		for (String file : missingFiles) {
			SimpleLogger.LOG(System.out, "Delete " + file);
			// Delete files on client that aren't on server
			String path = meta.getClientMeta(file, "path");
			File loc = new File(root, path);
			if (loc.exists()) {
				loc.delete();
			}
			// Delete such entries from the client metadata
			meta.deleteClientEntry(file);
		}
		deleteEmptyDirs(Paths.get(root));
	}

	/**
	 * Recursively delete all empty directories inside dir.
	 * This is necessary to delete empty directories left after file deletions
	 * inside a rollback.
	 * @param directory the root directory to clean
	 */
	static void deleteEmptyDirs(Path directory) {
		try {
			Files.walk(directory)
				.filter(Files::isDirectory)
				.sorted(Comparator.comparingInt(Path::getNameCount).reversed()) // Deepest directories first
				.forEach(DeleteFile::deleteIfEmpty);
		} catch (IOException e) {
			SimpleLogger.LOG(System.err, "Root directory not found: " + directory);
			e.printStackTrace();
		}
	}

	/**
	 * Deletes the given directory if it is empty.
	 * @param dir the directory to check and delete if empty
	 */
	static void deleteIfEmpty(Path dir) {
		try {
			if (Files.isDirectory(dir) && Files.list(dir).findAny().isEmpty()) {
				Files.delete(dir);
				SimpleLogger.LOG(System.out, "Deleted empty directory: " + dir);
			}
		} catch (IOException e) {
			SimpleLogger.LOG(System.err,
					"Error deleting directory " + dir + ": " + e.getMessage());
		}
	}
}
