package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

/**
 * Test GetFile for correct JSON parsing and server response
 */
public class GetFileTest {

	private GetFile getfile;
	private WireMockServer wireMockServer;

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

		// Set up GetFile instance after server initialization
		getfile = new GetFile(/*serverPath=*/"http://localhost:8088/",
				/*clientPath=*/"src/test/resources/");

	}
	
	@AfterEach
    public void tearDown() {
		System.out.println("GetFileTest.tearDown()");
		if (wireMockServer != null && wireMockServer.isRunning()) {
	        wireMockServer.stop();
	    }
		File cache = new File("src/test/resources/meta.json");
		if (cache.exists()) {
			try {
				FileUtils.delete(cache);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Get the latest version of a file on the server.
	 */
	@Test
	public void latestVersion() {
		assertEquals(getfile.getServerMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getServerMeta("file2", "version"), "v1.3.1");
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
	public void updateAndRollback() throws IOException {
		// Ensure initial state of local meta
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		// Initial state of file data
		assertEquals(FileUtils.readFileToString(
				new File("src/test/resources/data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
		// Update files and meta from server
		getfile.updateAll();
		// Local meta should be updated
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.3.1");
		// Updated file data
		assertEquals(FileUtils.readFileToString(
				new File("src/test/resources/data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.3.1.\n");
		// Rollback to previous state
		getfile.rollback();
		// Local meta should be back at initial state
		assertEquals(getfile.getClientMeta("file1", "version"), "v0.1.1");
		assertEquals(getfile.getClientMeta("file2", "version"), "v1.0.0");
		// also reverted file data to initial state
		assertEquals(FileUtils.readFileToString(
				new File("src/test/resources/data/file2.txt"), "utf-8"),
				"Hi! I'm file2 at v1.0.0.\n");
	}
	
	/** TODO: Implement these tests
	 *   justRollback: Test behavior of rollback before first update
	 *   MultiUpdate: Updating multiple times shouldn't corrupt backups
	 *   NewServerFile: Create new entry when new file entry on server
	 *   MissingServerFile: Currently ignores file. May want to prompt for deletion in future
	 *   ChangedPath: Behavior when an existing file on server has its path changed (data and metadata)
	 */
}
