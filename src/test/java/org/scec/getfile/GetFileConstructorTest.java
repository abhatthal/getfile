package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for GetFile constructor with correct server URI
 */
public class GetFileConstructorTest extends BaseWireMockTest {

    private File clientMetaFile;

    @BeforeEach
    public void setUp() {
        // Create minimal client metadata for constructor tests
        clientMetaFile = new File(clientRoot + "getfile.json");
    }

    @Test
    public void constructorWithCorrectURI() {
        // Test that constructor accepts the correct server URI
        GetFile getFile = new GetFile(
            "ConstructorTest",
            clientMetaFile,
            getServerMetaURI(),
            false
        );
        assertNotNull(getFile);
        assertNotNull(getFile.meta);
    }

    @Test
    public void constructorValidatesServerURI() {
        // Test URI validation in constructor
        assertThrows(NullPointerException.class, () -> {
            new GetFile(
                    "ConstructorTest",
                    clientMetaFile,
                    (URI)null,
                    false
            );
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new GetFile(
                    "ConstructorTest",
                    clientMetaFile,
                    (List<URI>)null,
                    false
            );
        });
        List<URI> emptyList = List.of();
        assertThrows(IllegalArgumentException.class, () -> {
            new GetFile(
                    "ConstructorTest",
                    clientMetaFile,
                    emptyList,
                    false
            );
        });
    }

    @Test
    public void constructorFindsValidURI() {
        // Should select the first server to establish a connection
        List<URI> serverURIs = List.of(
                URI.create("invalid://uri"),
                getServerBaseURI(), // Valid URL, but no file
                URI.create("http://localhost:8081/meta.json"), // Incorrect port
                getServerMetaURI(), // This is the valid URI
                URI.create("http://localhost:8080/data/file1.txt") // Valid but will be ignored
        );
        GetFile getFile = new GetFile(
                "ConstructorTest",
                clientMetaFile,
                serverURIs,
                false
        );
        assertNotNull(getFile);
        assertNotNull(getFile.meta);
    }
}
