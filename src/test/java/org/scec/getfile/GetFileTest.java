package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import org.junit.jupiter.api.BeforeEach;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Test GetFile for correct JSON parsing and server response
 */
public class GetFileTest {

	private GetFile getfile;
	private WireMockServer wireMockServer;
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
        
        // Create client resources
        String clientMetaStr = """
                {
                  "file1": {
                    "version": "v0.1.1",
                    "prompt": "true"
                  },
                  "file2": {
                    "version": "v1.0.0",
                    "prompt": "false"
                  }
                }
                """;
        JsonObject clientMeta = JsonParser.parseString(clientMetaStr).getAsJsonObject();
        File clientMetaFile = new File(clientRoot+"getfile.json");
        try {
			FileUtils.writeStringToFile(clientMetaFile, new Gson().toJson(clientMeta), "UTF-8");
			FileUtils.writeStringToFile(
					new File(clientRoot+"data/file1.txt"), "Hi! I'm file1 at v0.1.1.\n", "UTF-8");
			FileUtils.writeStringToFile(
					new File(clientRoot+"data/file2.txt"), "Hi! I'm file2 at v1.0.0.\n", "UTF-8");
			File f3 = new File(clientRoot+"data/file3");
			if (f3.exists()) {
				f3.delete();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
        

		// Set up GetFile instance after server initialization
		getfile = new GetFile(/*serverPath=*/"http://localhost:8088/",
				/*clientPath=*/clientRoot);

	}
	
	@AfterEach
    public void tearDown() {
		System.out.println("GetFileTest.tearDown()");
		if (wireMockServer != null && wireMockServer.isRunning()) {
	        wireMockServer.stop();
	    }
		File client = new File(clientRoot);
		try {
			if (client.exists()) {
				FileUtils.cleanDirectory(client);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get the latest version of a file on the server.
	 */
	@Test
	public void latestVersion() {
		assertEquals(getfile.getServerMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getServerMeta("file2", "version"), "v1.3.1");
		assertEquals(getfile.getServerMeta("file3", "version"), "v0.1.2");
	}

	/**
	 * Get the current version of a file on the client.
	 */
	@Test
	public void currentVersion() {
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
	}
	
	/**
	 * Ensure outdated files are successfully updated and can be rolled back
	 * @throws IOException 
	 */
	@Test
	public void updateAll() throws IOException {
		// Ensure initial state of local meta
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		// Initial state of file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		// Update files and meta from server
		getfile.backup();
		getfile.updateAll();
		// Local meta should be updated
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		// Updated file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Rollback to previous state
		getfile.rollback();
		// Local meta should be back at initial state
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		// also reverted file data to initial state
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
	}
	
	/**
	 * Attempting to update multiple times shouldn't corrupt backups
	 * @throws IOException
	 */
	@Test
	public void multipleUpdateAll() throws IOException {
		// Ensure initial state of local meta
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		// Initial state of file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		// Update files and meta from server
		getfile.backup();
		getfile.updateAll();
		// Local meta should be updated
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		// Updated file data
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Update as many times as we want. No change to state or backups.
		getfile.updateAll();
		getfile.updateAll();
		getfile.updateAll();
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Rollback to previous state
		getfile.rollback();
		// Local meta should be back at initial state
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
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
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		getfile.rollback();
		getfile.rollback();
		getfile.rollback();
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
	}
	
	/**
	 * Downloads a new file not found in client meta when found on server
	 * @throws IOException
	 */
	@Test
	public void newFile() throws IOException {
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		assertEquals(getfile.getServerMeta("file3", "version"), "v0.1.2");
		assertFalse(new File(clientRoot+"data/file3").exists());
		getfile.updateAll();
		assertEquals(getfile.getClientMeta("file3", "version"), "v0.1.2");
		assertEquals(getfile.getServerMeta("file3", "version"), "v0.1.2");
		assertTrue(new File(clientRoot+"data/file3").exists());
		assertTrue(new File(clientRoot+"data/file3/file3.txt").exists());
		assertEquals(FileUtils.readFileToString(
				new File(clientRoot+"data/file3/file3.txt"), "utf-8"),
				"Hi! I'm file3 at v0.1.2!\n");
		getfile.rollback();
		// Rollbacks don't delete folders created for new files
		assertFalse(new File(clientRoot+"data/file3").exists());
		assertFalse(new File(clientRoot+"data/file3/file3.txt").exists());
	}
	
	/**
	 * Upload just one file at a time and see only that file rolled back
	 * @throws IOException
	 */
	@Test
	public void updateIndividualFiles() throws IOException {
		// Just update file3
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		getfile.backup();
		getfile.updateFile("file3");
		// file3 is updated but file2 is still outdated.
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "v0.1.2");
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		// Update file3 again
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		getfile.backup();
		getfile.updateFile("file3");
		// file3 is updated but file2 is still outdated.
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "v0.1.2");
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		// Update file2
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		getfile.backup();
		getfile.updateFile("file2");
		// file3 not downloaded and file2 is updated
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		// Update both file1 and file2
		getfile.backup();
		getfile.updateFile("file1");
		getfile.updateFile("file2");
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		// Update both file2 and file3
		getfile.backup();
		getfile.updateFile("file2");
		getfile.updateFile("file3");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(getfile.getClientMeta("file3", "version"), "v0.1.2");
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
		// Update both and rollback to just file2 updated
		getfile.updateFile("file2");
		getfile.backup();
		getfile.updateFile("file3");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(getfile.getClientMeta("file3", "version"), "v0.1.2");
		getfile.rollback();
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		assertEquals(getfile.getClientMeta("file3", "version"), "");
	}
	
	/** TODO: Implement these tests
	 *   ChangedPath: Behavior when an existing file on server has its path changed (data and metadata)
	 */
}
