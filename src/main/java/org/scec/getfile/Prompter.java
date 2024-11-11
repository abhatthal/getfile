package org.scec.getfile;

// TODO
/**
 * Prompt a user with a graphical pane to update a file. Contains logic for if
 * a prompt should occur for a given file.
 */
public class Prompter {
	/**
	 * Prompt user with JOptionPane if they want to update to latest version of file
	 * @return true if we should download the latest version of this file
	 */
	public boolean promptDownload(String file) {
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

	public boolean shouldPrompt(String file) {
		return promptByDefault;
		// TODO: Attempt to read client metadata, else return promptByDefault
	}

	private final boolean promptByDefault = false;
}
