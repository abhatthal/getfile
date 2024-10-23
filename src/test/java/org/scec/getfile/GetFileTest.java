package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;

//import java.io.IOException;
//import java.net.HttpURLConnection;
//import java.net.MalformedURLException;
//import java.net.URI;
//import java.net.URISyntaxException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Test GetFile for correct JSON parsing and server response
 */
public class GetFileTest {

	private static GetFile getfile;
	private static WireMockServer wireMockServer;

	@BeforeAll
	public static void setUp() {
		// Set up GetFile instance
		getfile = new GetFile("src/test/resources/getfile.json");

		// Initialize WireMock server on port 8080
		// This server is our host for updated files and file metadata
        wireMockServer =
        		new WireMockServer(WireMockConfiguration.wireMockConfig().port(8080));
        wireMockServer.start();
        
        // Configure WireMock to mock endpoint that serves a JSON file
        wireMockServer.stubFor(get("/meta.json")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("src/test/resources/meta.json")));
        wireMockServer.stubFor(get("/meta.json.md5")
                .willReturn(aResponse()
                        .withBodyFile("src/test/resources/meta.json.md5")));
	}
	
	@AfterAll
    public static void tearDown() {
        wireMockServer.stop();
    }

	// https://docs.gradle.org/current/samples/sample_building_java_libraries.html
	@Test
	public void getServer() {
		assertEquals(getfile.getServer(),
				"http://localhost:8080/");
	}
	
	@Test
	public void getMeta() throws IOException, URISyntaxException {
		assertNotNull(getfile.getMeta());
	}
	
}
