package org.scec.getfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Utility class with logic for deletion of files.
 */
class DeleteFile {

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

