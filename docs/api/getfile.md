# GetFile Usage
## public GetFile(File clientMetaFile, URI serverMetaURI)
Construct a GetFile instance with the path to your local metadata configuration
and the link to the hosted server metadata.
```
	GetFile gf = new GetFile(
			/*clientMetaFile=*/new File("getfile.json"),
			/*serverMetaURI=*/URI.create("http://localhost:8088/meta.json"));
```
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

## public void updateAll()
Iterate over all the files found in the serverMeta and invoke
updateFile with the unique file key. Files are determined to be new by
comparing the serverMeta versions with the clientMeta versions. If an entry for
a file is not found in the clientMeta, then a new entry is made and the file is
downloaded.
```
gf.updateAll();
```

## public void updateFile(String file)
The file key uniquely identifies a file on the server and maps to the
corresponding version and where to download. This value is found in the
serverMeta and should also be found in the clientMeta, otherwise it’s assumed
to not exist and will update with a new entry. Note that this file key may be
different from the name of the file and could even include spaces and
characters not typically permissible for a file name.
```
gf.updateFile("data");
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

