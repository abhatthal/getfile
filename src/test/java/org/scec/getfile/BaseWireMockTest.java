package org.scec.getfile;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.io.FileUtils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Base class for all GetFile tests that require a WireMock server.
 * Provides shared server setup, teardown, and common utilities.
 */
public abstract class BaseWireMockTest {

    private WireMockServer wireMockServer;
    protected final String clientRoot = "src/test/resources/client_root/";
    private URI serverBaseURI;
    private final int wireMockPort = 8088;

    /**
     * Set up WireMock server with common configuration and stubs
     */
    @BeforeEach
    public void setUpWireMock() {
        System.out.println(this.getClass().getSimpleName() + ".setUpWireMock()");

        // Initialize WireMock server
        wireMockServer = new WireMockServer(
                WireMockConfiguration.wireMockConfig()
                        .bindAddress("localhost")
                        .port(wireMockPort)
                        .withRootDirectory("src/test/resources")
        );
        wireMockServer.start();
        serverBaseURI = URI.create("http://localhost:" + wireMockPort);

        // Configure common stubs
        configureCommonStubs();

        // Clean client directory
        cleanClientDirectory();
    }

    /**
     * Configure default WireMock stubs that are common to all tests
     */
    protected void configureCommonStubs() {
        // Meta.json endpoint
        wireMockServer.stubFor(get("/meta.json")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBodyFile("meta.json")));

        // Meta.json MD5 endpoint
        wireMockServer.stubFor(get("/meta.json.md5")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBodyFile("meta.json.md5")));

        // File endpoints
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
    }

    /**
     * Clean the client test directory before each test
     */
    protected void cleanClientDirectory() {
        File clientDir = new File(clientRoot);
        try {
            if (clientDir.exists()) {
                FileUtils.cleanDirectory(clientDir);
            }
        } catch (IOException e) {
            System.err.println("Failed to clean client directory: " + e.getMessage());
        }
    }

    /**
     * Get the correct server meta URI for GetFile constructor
     */
    protected URI getServerMetaURI() {
        return serverBaseURI.resolve("/meta.json");
    }

    /**
     * Get the base server URI
     */
    protected URI getServerBaseURI() {
        return serverBaseURI;
    }

    /**
     * Stop WireMock server after each test
     */
    @AfterEach
    public void tearDownWireMock() {
        System.out.println(this.getClass().getSimpleName() + ".tearDownWireMock()");
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }
}
