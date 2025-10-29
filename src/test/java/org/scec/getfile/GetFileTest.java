package org.scec.getfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FileUtils;

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
 */

/**
 * Test GetFile for correct JSON parsing and server response
 */
public class GetFileTest extends BaseWireMockTest {

	private GetFile getfile;
	private MetadataHandler meta;
	private BackupManager backupManager;

    @BeforeEach
    public void setUp() {
        System.out.println(this.getClass().getSimpleName() + ".setUp()");

        // Create client resources with intentional typo file11 to demonstrate path mismatch logic
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

        // Use the correct server URI from base class
        getfile = new GetFile(
                /*name=*/this.getClass().getName(),
                clientMetaFile,
                getServerMetaURI(),  // Using base class method
                /*showProgress=*/false);
        meta = getfile.meta;
        backupManager = getfile.getBackupManager();
    }

	/**
	 * Get the latest version of a file on the server.
	 */
	@Test
	public void latestVersion() {
		assertEquals("v0.1.1", meta.getServerMeta("file1", "version"));
		assertEquals("v1.3.1", meta.getServerMeta("file2", "version"));
		assertEquals("v0.1.2", meta.getServerMeta("file3", "version"));
	}

	/**
	 * Get the current version of a file on the client.
	 */
	@Test
	public void currentVersion() {
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file4", "version"));
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
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file4", "version"));
		// Initial state of file data
		assertEquals("Hi! I'm file2 at v1.0.0.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
		// Update files and meta from server
		backupManager.backup();
		getfile.updateAll().get();
		// Local meta should be updated
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file4", "version"));
		// Updated file data
		assertEquals("Hi! I'm file2 at v1.3.1.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
		// Rollback to previous state
		backupManager.rollback();
		// Local meta should be back at initial state
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file4", "version"));
		// also reverted file data to initial state
		assertEquals("Hi! I'm file2 at v1.0.0.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
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
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		// Initial state of file data
		assertEquals("Hi! I'm file2 at v1.0.0.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
		// Update files and meta from server
		backupManager.backup();
		getfile.updateAll().get();
		// Local meta should be updated
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		// Updated file data
		assertEquals("Hi! I'm file2 at v1.3.1.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
		// Update as many times as we want. No change to state or backups.
		getfile.updateAll().get();
		getfile.updateAll().get();
		getfile.updateAll().get();
		// Local meta should be updated
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		assertEquals("Hi! I'm file2 at v1.3.1.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
		// Rollback to previous state
		backupManager.rollback();
		// Local meta should be back at initial state
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		// also reverted file data to initial state
		assertEquals("Hi! I'm file2 at v1.0.0.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
	}


	/**
	 * Behavior of rollback before first update
	 * @throws IOException
	 */
	@Test
	public void justRollback() throws IOException {
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("Hi! I'm file2 at v1.0.0.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
		backupManager.rollback();
		backupManager.rollback();
		backupManager.rollback();
		backupManager.rollback();
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("Hi! I'm file2 at v1.0.0.\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file2.txt"), "utf-8"));
	}

	/**
	 * Downloads a new file not found in client meta when found on server
	 * @throws IOException
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	@Test
	public void newFile() throws IOException, InterruptedException, ExecutionException {
		assertEquals("", meta.getClientMeta("file3", "version"));
		assertEquals("v0.1.2", meta.getServerMeta("file3", "version"));
		assertFalse(new File(clientRoot+"data/file3").exists());
		backupManager.backup();
		getfile.updateAll().get();
		assertEquals("v0.1.2", meta.getClientMeta("file3", "version"));
		assertEquals("v0.1.2", meta.getServerMeta("file3", "version"));
		assertTrue(new File(clientRoot+"data/file3").exists());
		assertTrue(new File(clientRoot+"data/file3/file3.txt").exists());
		assertEquals("Hi! I'm file3 at v0.1.2!\n",
                FileUtils.readFileToString(
                        new File(clientRoot+"data/file3/file3.txt"), "utf-8"));
		backupManager.rollback();
		assertFalse(new File(clientRoot+"data/file3").exists());
		assertFalse(new File(clientRoot+"data/file3/file3.txt").exists());
	}

	/**
	 * Upload just one file at a time and see only that file rolled back
     */
	@Test
	public void updateIndividualFiles()
			throws InterruptedException, ExecutionException {
		// Just update file3
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		backupManager.backup();
		getfile.updateFile("file3").get();
		// file3 is updated but file2 is still outdated.
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("v0.1.2", meta.getClientMeta("file3", "version"));
		backupManager.rollback();
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		// Update file3 again
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		backupManager.backup();
		getfile.updateFile("file3").get();
		// file3 is updated but file2 is still outdated.
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("v0.1.2", meta.getClientMeta("file3", "version"));
		backupManager.rollback();
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		// Update file2
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		backupManager.backup();
		getfile.updateFile("file2").get();
		// file3 not downloaded and file2 is updated
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		backupManager.rollback();
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		// Update both file1 and file2
		backupManager.backup();
		getfile.updateFile("file1").get();
		getfile.updateFile("file2").get();
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		backupManager.rollback();
		assertEquals("v0.1.1", meta.getClientMeta("file1", "version"));
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		// Update both file2 and file3
		backupManager.backup();
		getfile.updateFile("file2").get();
		getfile.updateFile("file3").get();
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		assertEquals("v0.1.2", meta.getClientMeta("file3", "version"));
		backupManager.rollback();
		assertEquals("v1.0.0", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
		// Update both and rollback to just file2 updated
		getfile.updateFile("file2").get();
		backupManager.backup();
		getfile.updateFile("file3").get();
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		assertEquals("v0.1.2", meta.getClientMeta("file3", "version"));
		backupManager.rollback();
		assertEquals("v1.3.1", meta.getClientMeta("file2", "version"));
		assertEquals("", meta.getClientMeta("file3", "version"));
	}

	/**
	 * Verify ability to get size of files on server
	 */
	@Test
	public void serverFileSizes() {
		assertEquals(25, getfile.tracker.getFileSize("file1"));
		assertEquals(25, getfile.tracker.getFileSize("file2"));
		assertEquals(25, getfile.tracker.getFileSize("file3"));
	}

    @AfterEach
    public void tearDown() {
        System.out.println("GetFileTest.tearDown()");
        // WireMock teardown is handled by base class
    }
}
