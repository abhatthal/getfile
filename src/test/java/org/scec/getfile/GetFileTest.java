package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.JsonObject;

/**
 * Test GetFile for correct JSON parsing and server response
 */
public class GetFileTest {

	private GetFile getfile;
	private WireMockServer wireMockServer;

	@BeforeEach
	public void setUp() {
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
		getfile = new GetFile(/*server=*/"http://localhost:8088/",
				/*getfileJson=*/"src/test/resources/getfile.json");

	}
	
	@AfterEach
    public void tearDown() {
		if (wireMockServer != null && wireMockServer.isRunning()) {
	        wireMockServer.stop();
	    }
	}

	/**
	 * Get the latest version of a file on the server.
	 */
	@Test
	public void latestVersion() {
		assertEquals(getfile.latestVersion("file1"), "v0.1.1");
		assertEquals(getfile.latestVersion("file2"), "v1.3.1");
	}

	/**
	 * Get the latest version of a file on the server.
	 */
	@Test
	public void currentVersion() {
		assertEquals(getfile.currentVersion("file1"), "v0.1.1");
		assertEquals(getfile.currentVersion("file2"), "v1.0.0");
	}
}
