package org.scec.getfile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
//import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class GetFileTest {
	static GetFile getfile;

	@BeforeAll
	public static void setUp() {
		getfile = new GetFile("src/test/resources/getfile.json");
	}

	// https://docs.gradle.org/current/samples/sample_building_java_libraries.html
	@Test
	public void getServer() {
		assertEquals(getfile.getServer(),
				"http://localhost:8080/");
	}

	@Test
	public void getMeta() {
		assertEquals(getfile.getMeta(),
				"http://localhost:8080/demo/meta.json");
	}

	// TODO: Use WireMock to mock file server at localhost:8080
	// https://stackoverflow.com/questions/606352/how-to-mock-a-web-server-for-unit-testing-in-java
}
