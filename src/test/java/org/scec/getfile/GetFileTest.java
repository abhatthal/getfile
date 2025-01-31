package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** TODO: Implement these tests
 *   changedPath: Behavior when an existing file on server has its path changed (data and metadata)
 *   deletedFile: Handling of when a file entry is deleted from the server
 *   multipleBackups: Test multiple concurrent backup managers. Each should work and can't overwrite each other.
 *   noClientDataUpdate: Test update logic when there is no client data or client metadata file 
 * 	 noClientDataBackup: Test backup logic when there’s no client data to backup
 * 						 Also consider when there’s an update from no data initially
 * 
 * TODO: Restructure tests into multiple classes all inheriting from
 * 		 BaseWireMockTest with shared wiremock logic.
 */

/**
 * Test GetFile for correct JSON parsing and server response
 */
public class GetFileTest {

	private GetFile getfile;
	private MetadataHandler meta;
	private WireMockServer wireMockServer;
	private BackupManager backupManager;
	private final String clientRoot = "src/test/resources/client_root/";

	@BeforeEach
	public void setUp() {
		System.out.println("GetFileTest.setUp()");
		// Initialize WireMock server on port 8088
		// This server is our host for updated files and file metadata
        wireMockServer =
        		new WireMockServer(WireMockConfiguration.wireMockConfig()
        				.bindAddress("localhost")
        				.port(8088)
        				.withRootDirectory("src/test/resources"));
        wireMockServer.start();
        
        // Configure WireMock to mock endpoint that serves a JSON file
        wireMockServer.stubFor(get("/meta.json")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBodyFile("meta.json")));
        wireMockServer.stubFor(get("/meta.json.md5")
                .willReturn(aResponse()
                		.withStatus(200)
                        .withBodyFile("meta.json.md5")));
        wireMockServer.stubFor(head(urlEqualTo("/data/file1.txt"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/octet-stream")
                    .withHeader("Content-Length", "25")
                    .withStatus(200)));
        wireMockServer.stubFor(head(urlEqualTo("/data/file2.txt"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/octet-stream")
                    .withHeader("Content-Length", "25")
                    .withStatus(200)));
        wireMockServer.stubFor(head(urlEqualTo("/data/file3/file3.txt"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/octet-stream")
                    .withHeader("Content-Length", "25")
                    .withStatus(200)));
        
        // Clear old client data
		File client = new File(clientRoot);
		try {
			if (client.exists()) {
				FileUtils.cleanDirectory(client);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
        // Create client resources
        // Intentional typo file11 to demonstrate path mismatch logic
		String clientMetaStr = "{\n" +
		        "  \"file1\": {\n" +
		        "    \"version\": \"v0.1.1\",\n" +
		        "    \"path\": \"data/file11.txt\",\n" +
		        "    \"prompt\": \"true\"\n" +
		        "  },\n" +
		        "  \"file2\": {\n" +
		        "    \"version\": \"v1.0.0\",\n" +
		        "    \"path\": \"data/file2.txt\",\n" +
		        "    \"prompt\": \"false\"\n" +
		        "  },\n" +
		        "  \"file4\": {\n" +
		        "    \"version\": \"v1.0.0\",\n" +
		        "    \"path\": \"data/file4.txt\",\n" +
		        "    \"prompt\": \"false\"\n" +
		        "  }\n" +
		        "}";
        JsonObject clientMeta = JsonParser.parseString(clientMetaStr).getAsJsonObject();
        File clientMetaFile = new File(clientRoot + "getfile.json");
        try {
			FileUtils.writeStringToFile(clientMetaFile, new Gson().toJson(clientMeta), "UTF-8");
			FileUtils.writeStringToFile(
					new File(clientRoot+"data/file11.txt"), "Hi! I'm file1 at v0.1.1.\n", "UTF-8");
			FileUtils.writeStringToFile(
					new File(clientRoot+"data/file2.txt"), "Hi! I'm file2 at v1.0.0.\n", "UTF-8");
			FileUtils.writeStringToFile(
					new File(clientRoot+"data/file4.txt"), "Hi! I'm file4 at v1.0.0.\n", "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
        URI serverMetaURI = URI.create("http://localhost:8088/meta.json");
		getfile = new GetFile(
					/*name=*/this.getClass().getName(),
					clientMetaFile,
					serverMetaURI,
					/*showProgress=*/false);
		meta = getfile.meta;
		backupManager = getfile.getBackupManager();
	}
	
	@AfterEach
    public void tearDown() {
		System.out.println("GetFileTest.tearDown()");
		if (wireMockServer != null && wireMockServer.isRunning()) {
	        wireMockServer.stop();
	    }
	}

	/**
	 * Get the latest version of a file on the server.
	 */
	@Test
	public void latestVersion() {
		assertEquals(meta.getServerMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getServerMeta("file2", "version"), "v1.3.1");
		assertEquals(meta.getServerMeta("file3", "version"), "v0.1.2");
	}

	/**
	 * Get the current version of a file on the client.
	 */
	@Test
	public void currentVersion() {
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file4", "version"), "v1.0.0");
	}
	
	/**
	 * Ensure outdated files are successfully updated and can be rolled back
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@Test
	public void updateAll() throws IOException, InterruptedException, ExecutionException {
		// Ensure initial state of local meta
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file4", "version"), "v1.0.0");
		// Initial state of file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		// Update files and meta from server
		backupManager.backup();
		getfile.updateAll().get();
		// Local meta should be updated
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(meta.getClientMeta("file4", "version"), "");
		// Updated file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Rollback to previous state
		backupManager.rollback();
		// Local meta should be back at initial state
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file4", "version"), "v1.0.0");
		// also reverted file data to initial state
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
	}
	
	/**
	 * Attempting to update multiple times shouldn't corrupt backups
	 * @throws IOException
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@Test
	public void multipleUpdateAll() throws IOException, InterruptedException, ExecutionException {
		// Ensure initial state of local meta
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		// Initial state of file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		// Update files and meta from server
		backupManager.backup();
		getfile.updateAll().get();
		// Local meta should be updated
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		// Updated file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Update as many times as we want. No change to state or backups.
		getfile.updateAll().get();
		getfile.updateAll().get();
		getfile.updateAll().get();
		// Local meta should be updated
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Rollback to previous state
		backupManager.rollback();
		// Local meta should be back at initial state
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		// also reverted file data to initial state
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
	}
	
	
	/**
	 * Behavior of rollback before first update
	 * @throws IOException
	 */
	@Test
	public void justRollback() throws IOException {
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		backupManager.rollback();
		backupManager.rollback();
		backupManager.rollback();
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
	}
	
	/**
	 * Downloads a new file not found in client meta when found on server
	 * @throws IOException
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@Test
	public void newFile() throws IOException, InterruptedException, ExecutionException {
		assertEquals(meta.getClientMeta("file3", "version"), "");
		assertEquals(meta.getServerMeta("file3", "version"), "v0.1.2");
		assertFalse(new File(clientRoot+"data/file3").exists());
		backupManager.backup();
		getfile.updateAll().get();
		assertEquals(meta.getClientMeta("file3", "version"), "v0.1.2");
		assertEquals(meta.getServerMeta("file3", "version"), "v0.1.2");
		assertTrue(new File(clientRoot+"data/file3").exists());
		assertTrue(new File(clientRoot+"data/file3/file3.txt").exists());
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file3/file3.txt"), "utf-8"),
				"Hi! I'm file3 at v0.1.2!\n");
		backupManager.rollback();
		assertFalse(new File(clientRoot+"data/file3").exists());
		assertFalse(new File(clientRoot+"data/file3/file3.txt").exists());
	}
	
	/**
	 * Upload just one file at a time and see only that file rolled back
	 * @throws IOException
	 */
	@Test
	public void updateIndividualFiles()
			throws IOException, InterruptedException, ExecutionException {
		// Just update file3
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		backupManager.backup();
		getfile.updateFile("file3").get();
		// file3 is updated but file2 is still outdated.
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "v0.1.2");
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		// Update file3 again
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		backupManager.backup();
		getfile.updateFile("file3").get();
		// file3 is updated but file2 is still outdated.
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "v0.1.2");
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		// Update file2
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		backupManager.backup();
		getfile.updateFile("file2").get();
		// file3 not downloaded and file2 is updated
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		// Update both file1 and file2
		backupManager.backup();
		getfile.updateFile("file1").get();
		getfile.updateFile("file2").get();
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		// Update both file2 and file3
		backupManager.backup();
		getfile.updateFile("file2").get();
		getfile.updateFile("file3").get();
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(meta.getClientMeta("file3", "version"), "v0.1.2");
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(meta.getClientMeta("file3", "version"), "");
		// Update both and rollback to just file2 updated
		getfile.updateFile("file2").get();
		backupManager.backup();
		getfile.updateFile("file3").get();
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(meta.getClientMeta("file3", "version"), "v0.1.2");
		backupManager.rollback();
		assertEquals(meta.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(meta.getClientMeta("file3", "version"), "");
	}	
	
	/**
	 * Verify ability to get size of files on server
	 */
	@Test
	public void serverFileSizes() {
		assertEquals(getfile.tracker.getFileSize("file1"), 25);
		assertEquals(getfile.tracker.getFileSize("file2"), 25);
		assertEquals(getfile.tracker.getFileSize("file3"), 25);
	}
	
}
