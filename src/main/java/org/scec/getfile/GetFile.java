package org.scec.getfile;

//import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//import com.google.gson.stream.JsonReader;
//import com.google.gson.stream.JsonToken;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
//import java.io.FileReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.InputStreamReader;


/**
 * A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	public GetFile(String jsonFile) {
		JsonObject json = parseJSON(jsonFile);	
		server_ = json.get("server").toString().replaceAll("\"", "");
		metadata_file_ = server_.concat(
				json.get("app_meta").toString()).replaceAll("\"", "");

		// For each file
		//  * if file["version"] != latestVersion
		//    * if not file["uploadType"]==automatic
		//      then promptDownload
		//	  * downloadFile using validateFile with n tries
	}
	
	private JsonObject parseJSON(String jsonFile) {
		// https://stackoverflow.com/a/62106829
		JsonObject json = null;
		try (Reader reader = new InputStreamReader(
				new FileInputStream(jsonFile), "UTF-8")) {
		    json = (JsonObject) JsonParser.parseReader(reader);
		} catch (FileNotFoundException e) {
			System.err.println("GetFile.parseJSON FileNotFound");
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			System.err.println("GetFile.parseJSON UnsupportedEncoding");
			throw new RuntimeException(e);
		} catch (IOException e) {
			System.err.println("GetFile.parseJSON IOException");
			throw new RuntimeException(e);
		}
		return json;
	}
	
	private String latestVersion() {
		// Get the latestVersion of this file on the server
		return "";
	}
	
	private void downloadFile() {
		
	}
	
	private void promptDownload() {
		
	}
	
	private void validateFile() {
		
	}

	public String getServer() {
		// Server to get latest files and metadata
		return server_;
	}
	private String server_;
	
	public String getMeta() {
		// Get full path to the metadata file on the server
		return metadata_file_;
	}
	private String metadata_file_;
	
}
