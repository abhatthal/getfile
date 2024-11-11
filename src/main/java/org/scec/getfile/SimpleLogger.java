package org.scec.getfile;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * A simple logging utility that logs messages with the class name and method name
 * from which the log was called. The message is printed to the specified output stream.
 */
public class SimpleLogger {

	/**
	 * Logs a message to the specified output stream, prefixed with the calling class
	 * and method names.
	 * 
	 * @param message		The message to log.
	 * @param outputStream	The output stream to write the log to.
	 */
	public static void LOG(OutputStream outputStream, String message) {
		String className = getCallingClassName();
		String methodName = getCallingMethodName();
		PrintStream printStream = new PrintStream(outputStream);
		printStream.println(className + "." + methodName + ": " + message);
	}

	/**
	 * Gets the name of the class from which the log was called.
	 * @return The class name of the caller.
	 */
	private static String getCallingClassName() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		// Skip the first few elements (up to and including Thread.getStackTrace)
		for (int i = 2; i < stackTrace.length; i++) {
			StackTraceElement element = stackTrace[i];
			if (!element.getClassName().equals(SimpleLogger.class.getName())) {
				return element.getClassName();
			}
		}
		return "UnknownClass";
	}

	/**
	 * Gets the name of the method from which the log was called.
	 * @return The method name of the caller.
	 */
	private static String getCallingMethodName() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		// Skip the first few elements (up to and including Thread.getStackTrace)
		for (int i = 2; i < stackTrace.length; i++) {
			StackTraceElement element = stackTrace[i];
			if (!element.getClassName().equals(SimpleLogger.class.getName())) {
				return element.getMethodName();
			}
		}
		return "UnknownMethod";
	}
}

