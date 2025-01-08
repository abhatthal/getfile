# GetFile Usage
## public GetFile(String name, File clientMetaFile, URI serverMetaURI, boolean showProgress, boolean ignoreErrors);
Construct a GetFile instance with the path to your local metadata configuration
and the link to the hosted server metadata.
```
	GetFile gf = new GetFile(
			/*name=*/"MyGetFileApp",
			/*clientMetaFile=*/new File("getfile.json"),
			/*serverMetaURI=*/URI.create("http://localhost:8088/meta.json"),
			/*showProgress=*/false);
```
A GetFile `name` is a string used inside GetFile user prompts. It may see additional
uses in future releases. There is no requirement for this name to be unique.

A clientMetaFile is the file locally on your system tracking the versions of
files you have installed. This is required to compare versions against the server.

If the clientMetaFile doesn't exist, a new file will be created and all files
will be downloaded.  The names of the JSON files are arbitrary, but cannot be
the same on both the client and the server or else the cached server metadata
will overwrite the client metadata.

Files are downloaded from the root of the serverMetaURI, but relative to the
location of the clientMetaFile. Directories will be created as necessary.

Consider the following example:
```
clientMetaFile = /home/user/.getfile/client_metadata.json
serverMetaURI = https://www.example.com/demos/demo1/server_metadata.json
```

If we want to download a file on the server located at
https://www.example.com/demos/demo1/data/data1.zip, then it would download to
`/home/user/.getfile/data/data1.zip`.

It is possible for a project to have multiple instances of GetFile, however I
strongly recommend each serverMetaURI and clientMetaFile have a 1-1
relationship. If two serverMeta JSON files track a file with the same path and
name, then they could overwrite each other when downloaded. Writing multiple
instances to the same directory creates a race condition and should be avoided.

The showProgress boolean allows you to disable the GUI download progress bar.

## public CompletableFuture<Map<String, File>> updateAll()
Iterate over all the files found in the serverMeta and invoke
updateFile with the unique file key. Files are determined to be new by
comparing the serverMeta versions with the clientMeta versions. If an entry for
a file is not found in the clientMeta, then a new entry is made and the file is
downloaded.

updateAll returns a mapping of the fileKey to the updated file object.
The future for the map evaluates once all the files have been updated.
The corresponding File objects are mutable and can be used to find the paths to
the files on the client system.

Example invocation to update all files and then read one of them:
```
gf.updateAll().thenAccept(updatedFiles -> {
	File file1 = updatedFiles.get("file1");
});
```

## public CompletableFuture<File> updateFile(String fileKey)
The file key uniquely identifies a file on the server and maps to the
corresponding version and where to download. This value is found in the
serverMeta and should also be found in the clientMeta, otherwise it’s assumed
to not exist and will update with a new entry. Note that this file key may be
different from the name of the file and could even include spaces and
characters not typically permissible for a file name.

updateFile returns a future to the File where the updated file will be.

Example invocation to get one file:
```
gf.updateFile("data").thenAccept(data -> {
	System.out.println("Downloaded " + data.getName());
});
```

## public BackupManager getBackupManager(String identifier)
Gets or creates an instance of BackupManager. This is the only way
to create a BackupManager, as the constructor is package-private. Each instance
of GetFile may have 0 or more BackupManagers, and each such BackupManager must
be managed through their respective GetFile instance via the getBackupManager
method. The String identifier is concatenated to the end of the backup.
The following example would operate on backup files ending in `.bak-1`.
```
BackupManager bm1 = gf.getBackupManager("1");
```

## public BackupManager getBackupManager()
Invokes getBackupManager with an empty string identifier. Such backup files will
simply end in `.bak`.
```
BackupManager bm = gf.getBackupManager();
```

## public static final String LATEST_JAR_URL
This String contains the URI to the JSON metadata for the latest version of
the GetFile Jar and Fat Jar files. It can be used with an existing GetFile instance
to get the latest version of GetFile. This enables clients to self-update.
We store this version as a static constant in the GetFile class directly to ensure
clients are always calling the correct endpoint, as it is subject to change.
See [demo3](demos/demo3) for an example of how this constant is utilized.

