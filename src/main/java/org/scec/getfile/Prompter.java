package org.scec.getfile;

// TODO
/**
 * Prompt a user with a graphical pane to update a file. Contains logic for if
 * a prompt should occur for a given file.
 */
public class Prompter {
	/**
	 * Prompt user with JOptionPane if they want to update to latest version of file
	 * @param file			Name of key corresponding to file to try downloading
	 * @return true if we should download the latest version of this file
	 */
	public static boolean promptDownload(String file) {
		// In the event of a manual update type, prompt the user prior to download
		// "Would you like to update `file` version to latestVersion now?"
		// "Update Now" ,"Later", "Skip this Version"

		// Returns false for Later or Skip options.
		// If we want to be able to skip versions,
		// we need to keep a JsonObj of skipped versions per file inside getfile.json

		// I need to manually test the behaviour of promptDownload when
		// invoked in updateAll.
		return true;  // TODO
	}

	/** If we should try to prompt user to download this file or just download
	 * silently in the background.
	 * @param file			Name of key corresponding to file to try downloading
	 * @return				true if client metadata or default indicates
	 */
	public static boolean shouldPrompt(String file) {
		MetadataHandler meta = MetadataHandler.getInstance();
		String shouldPrompt = meta.getClientMeta(file, "prompt");
		if (shouldPrompt.equals("")) {
			return promptByDefault;
		}
		if (shouldPrompt.equals("true")) {
			return true;
		}
		if (!shouldPrompt.equals("false")) {
			SimpleLogger.LOG(System.err,
					"Invalid prompt value " + shouldPrompt +
					". Assuming \"false\"");
		}
		return false;
	}

	private static final boolean promptByDefault = false;
}
