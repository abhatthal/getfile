package org.scec.getfile;

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
		final String clientPath = meta.getClientPath();
		final String serverPath = meta.getServerPath();

		if (clientVersion.equals(serverVersion)) {
			SimpleLogger.LOG(System.out,
					"File " + file + " is already up to date.");
			return;
		}
		String downloadPath = clientPath.concat(meta.getServerMeta(file, "path"));
		// Create the file entry if it doesn't already exist
		if (clientVersion.equals("")) {
			meta.newClientEntry(file);
		}
		boolean shouldPrompt = Prompter.shouldPrompt(file);
		if ((shouldPrompt && Prompter.promptDownload(file)) || !shouldPrompt) {
			SimpleLogger.LOG(System.out,
					"Update " + file + " " + clientVersion + " => " + serverVersion);
			// Download and validate the new file from the server
			Downloader.downloadFile(serverPath.concat(meta.getServerMeta(file, "path")),
					downloadPath, /*retries=*/3);
			// Update the client meta version accordingly
			meta.setClientMeta(file, "version", serverVersion);
		}
	}
	
	private MetadataHandler meta;
}
