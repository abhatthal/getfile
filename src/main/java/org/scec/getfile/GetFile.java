package org.scec.getfile;

//import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//import com.google.gson.stream.JsonReader;
//import com.google.gson.stream.JsonToken;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
//import java.io.FileReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


/**
 * A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	public GetFile(String jsonFile) {
		JsonObject json = parseJson(jsonFile);	
		server_ = json.get("server").toString().replaceAll("\"", "");
		// TODO: Check if server is up. If not, log failure to connect and return

		// For each file
		//  * if file["version"] != latestVersion
		//    * if not file["uploadType"]==automatic
		//      then promptDownload
		//	  * downloadFile using validateFile with n tries
	}
	
	/**
	 * Read a JSON file into memory for evaluation
	 * @param jsonFile		Path to a JSON file
	 * @return
	 */
	private JsonObject parseJson(String jsonFile) {
		// https://stackoverflow.com/a/62106829
		JsonObject json = null;
		try (Reader reader = new InputStreamReader(
				new FileInputStream(jsonFile), "UTF-8")) {
		    json = (JsonObject) JsonParser.parseReader(reader);
		} catch (FileNotFoundException e) {
			System.err.println("GetFile.parseJson FileNotFound");
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			System.err.println("GetFile.parseJson UnsupportedEncoding");
			throw new RuntimeException(e);
		} catch (IOException e) {
			System.err.println("GetFile.parseJson IOException");
			throw new RuntimeException(e);
		}
		return json;
	}
	
	/**
	 * Finds the latest version of a file given the server cached metadata
	 * @param file		Key in the meta.json file. Not necessarily filename.
	 * @return 			version of the given file
	 */
	private String latestVersion(String file) {
		return ""; // TODO
	}
	
	/**
	 * Downloads a file with MD5 validation
	 * @param fileUrl				URL of file to download
	 * @param saveLocation			Where the downloaded file should be stored
	 * @throws IOException
	 * @throws URISyntaxException
	 * @return 0 if success and 1 if any failure
	 */
	private int downloadFile(String fileUrl, String saveLocation) throws IOException, URISyntaxException {
		// Download the file from the given URL
        File file = new File(".".concat(saveLocation));
        FileUtils.copyURLToFile(new URI(fileUrl).toURL(), file);
		 // Calculate the MD5 checksum of the downloaded file
        String calculatedMd5 =
        		DigestUtils.md5Hex(Files.newInputStream(file.toPath()));
		if (calculatedMd5.equalsIgnoreCase(getExpectedMd5(fileUrl))) {
			// Move hidden file to overwrite.
			FileUtils.copyFile(file, new File(saveLocation));
			file.delete();
			System.out.printf("GetFile.downloadFile updated %s from %s", saveLocation, fileUrl);
			return 0;
		}
		if (file.exists()) {
			file.delete();
		}
		System.err.printf("GetFile.downloadFile MD5 validation failed: %s", fileUrl);
		return 1;
	}
	
	private String getExpectedMd5(String fileUrl)
			throws URISyntaxException, MalformedURLException, IOException {
		URI uri = new URI(fileUrl.concat(".md5"));
		InputStream inputStream = uri.toURL().openStream();
		return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
	}
	
	/**
	 * Prompt user with JOptionPane if they want to update to latest version of file
	 */
	private void promptDownload() {
		// In the event of a manual update type, prompt the user prior to download
		// "Would you like to update `file` version to latestVersion now?"
		// "Update Now" ,"Later", "Skip this Version"
		// TODO: Implement JOptionPane solution.
		// If we want to be able to skip versions,
		// we need to keep a JsonArray of skipped versions inside getfile.json
	}
	
	/**
	 * Gets URL to the server
	 * @return private server variable
	 */
	public String getServer() {
		// Server to get latest files and metadata
		return server_;
	}
	private String server_;
	
	/**
	 * Reads the meta.json file outlining versioned files to check for updates
	 * @return JSON of all files on server and their latest versions
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public JsonObject getMeta() throws IOException, URISyntaxException {
		// Try up to `retries` times to download
		int status = 1;
		final int retries = 3;
		for (int i = 0; i < retries && status != 0; i++) {
			status = downloadFile(server_.concat("meta.json"), "meta.json");
		}
		if (status != 0) {
			return (JsonObject)null;
		}
		return parseJson("meta.json");
	}
}
