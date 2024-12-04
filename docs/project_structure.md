# Project Structure

The GetFile project is comprised of 8 classes, 2 of which are accessible
outside the packaged JAR and end-users may interact with.
Only the GetFile and BackupManager classes are declared publicly and can be
imported into projects. All other classes are package-private.

* `public GetFile` - Keep files up to date with server
* `public BackupManager` - Create and restore snapshots
* `MetadataHandler` - Keep track of file versions
* `Prompter` - Prompts user if they want to download a new file
* `CalcProgressBar` - General utility progress bar dialog. (Dup from [OpenSHA](https://github.com/opensha/opensha))
* `ProgressTracker` - Updates a CalcProgressBar with download status
* `static Downloader` - Just the logic for validated downloads
* `static SimpleLogger` - Logs "Class.Method: message" to stdout or stderr
* `static DeleteFile` - Logic for deletion of files/directories

All instances of other classes are managed through an instance of GetFile.
The MetaadataHandler is used to get latest changes and pass metadata around
to other utility classes. For latest changes, refer to source code directly or
build documentation from source code. See [javadoc](javadoc.md) for details.

