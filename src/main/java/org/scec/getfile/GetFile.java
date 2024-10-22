package org.scec.getfile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;


/* A GetFile instance contains all the logic required
 * to download and validate a file if a new version exists.
 * All tracked files must be versioned.
 */
public class GetFile {
	public GetFile(String JsonString) {
		// Read a JSON of files to check for downloads
		try {
			JsonReader reader = new JsonReader(new FileReader(JsonString));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Read "server"
		// For each file
		//  * if file["version"] != latestVersion
		//    * if not file["uploadType"]==automatic
		//      then promptDownload
		//	  * downloadFile using validateFile with n tries
		
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
}
