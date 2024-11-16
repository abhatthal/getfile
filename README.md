# GetFile

GetFile is a simple yet powerful Java library to keep files up to date on
client systems.  Host your own files (i.e. data, code, anything really) over
HTTP(S) and attribute a version to each file on the client and server.


When invoked in your application, GetFile will
* compare file versions on client against server
* optionally prompt or silently continue on a per-file basis
* download newer files when available
* validate downloads for corruption
* provide logic to rollback updates


GetFile is a subtle nod to GetDown (https://github.com/threerings/getdown),
which is used to update Java applications themselves. GetDown wraps the
application and allows you to update seamlessly to newer versions hosted on
your own server. This requires updating the entire application, code and data
included, and must wrap around a main class. GetFile was created for greater
flexibility versioning any files, inside the codebase or anywhere on the
client's computer, without wrapping a main class but instead being invoked by
the application directly.

## Download
[libs/getfile-all.jar](https://github.com/abhatthal/getfile/raw/refs/heads/main/libs/getfile-all.jar) (4.2MB)

## Documentation
* [api/getfile](docs/api/getfile.md) - How to use GetFile class with examples
* [api/backupmanager](docs/api/backupmanager.md) - How to use BackupManager with examples
* [javadoc](docs/javadoc.md) - How to build API documentation from code
* [project_structure](docs/project_structure.md) - High-level of how the source code is organized
* [server_config](docs/server_config.md) - How to structure server data for GetFile with examples
* [testing](docs/testing.md) - Get started with unit tests in Gradle
 
TODO: Move server operation docs in README into docs/

## Future Plans
* Add the ability to skip versions
* Implement per-file prompting.
* Enable server file entry deletion
  * Need to duplicate serverPath in a clientPath located inside the respective client metadata file.
  * Add logic for what to do when an existing clientPath does not match the specified serverPath.
    In this case we want to move the file to the new serverPath.
    Current behavior is to duplicate the file if a new version is released with a different serverPath.
  * Can deprecate entries in current version by making new version with empty file and parsing logic.

## Demo
See the following demonstration of running GetFile with a local server.
https://github.com/abhatthal/getfile-demo

## Building
You can build your own thin or Fat JAR file from source to use as a dependency in your application.
* `gradle shadowJar`
* Copy Fat JAR `build/libs/getfile-all.jar` into your project lib directory
* Thin JAR is also available at `build/libs/getfile.jar`, but you must manually specify dependencies
* In a Gradle project, you would add using `implementation files('lib/getfile-all.jar')`

## Adding Files to Server
* Create `file` on server
* Compute MD5sum on server in same directory as file
```
md5sum -z file | awk '{print $1}' | tr -d '\n' > file.md5
```
* Create entry on server `meta.json` with version and path of new file
* Compute MD5sum on server for meta.json.
```
md5sum -z meta.json | awk '{print $1}' | tr -d '\n' > meta.json.md5
```
I strongly suggest using a symbolic link structure rather than directly providing paths in the server metadata.
See the update section for details on the structure of the server metadata and filesystem.

## Updating Files on Server
Consider the following example server metadata `meta.json`
```
{
	"model1": {
		"version": "v0.1.2",
		"path": "models/model1/model.zip"
	},
	"model2": {
		"version": "v1.0.0",
		"path": "models/model2/model.zip"
	}
}
```
With the following server filesystem:
```
.
├── meta.json
├── meta.json.md5
└── models/
    ├── model1/
    │   ├── model.zip (symlink => v0.1.2/model.zip)
    │   ├── model.zip.md5
    │   ├── v0.1.1/
    │   │   └── model.zip
    │   └── v0.1.2/
    │       └── model.zip
    └── model2/
        ├── model.zip (symlink => v1.0.0/model.zip)
        ├── model.zip.md5
        └── v1.0.0/
            └── model.zip
```
I strongly recommend structuring data on the server such that metadata paths link to symbolic links. This provides flexibility to change the underlying path where data is stored without impacting client download paths and enables updating file versions without overwriting existing data. You can link directly to the data, but that requires overwriting the existing data or moving them to a new location. Paths shouldn't be modified directly as it causes the client to download files to a new location, which could impact behavior in client code.

In such a system, you can update model1 as follows:
```
{
	"model1": {
		"version": "v0.1.3",
		"path": "models/model1/model.zip"
	},
	"model2": {
		"version": "v1.0.0",
		"path": "models/model2/model.zip"
	}
}

.
├── meta.json
├── meta.json.md5
└── models/
    ├── model1/
    │   ├── model.zip (symlink => v0.1.3/model.zip)
    │   ├── model.zip.md5
    │   ├── v0.1.1/
    │   │   └── model.zip
    │   ├── v0.1.2/
    │   │   └── model.zip
    │   └── v0.1.3/
    │       └── model.zip
    └── model2/
        ├── model.zip (symlink => v1.0.0/model.zip)
        ├── model.zip.md5
        └── v1.0.0/
            └── model.zip
```
Make sure to regenerate the MD5sums `(model1.zip.md5, meta.json.md5)` or
downloads will fail due to inability to validate checksums.  You can delete
older versions as we run out of space, or keep them for as long as you'd like.
The client will overwrite the previous model1 (Either None, v0.1.1, or v0.1.2)
at `${ClientRoot}/models/model1/model.zip` with v0.1.3.

File trees generated via https://tree.nathanfriend.com/

## Deleting Files from Server
Continuing with the above example filesystem and metadata structure, we can
simply delete the folder containing the old data.  If we are deleting the
current latest version (i.e. rollback to older version), then take care to
update the MD5sums accordingly.

If you intend to remove an entry altogether (Not recommended), you would delete
the entry from the corresponding server metadata file and delete all the
corresponding data files. This will stop clients from searching for these
files, however it won't be deleted on the client's system. Additionally, if you
change the path of an existing entry and increment the version, you'll have
duplicates at a new path on the client. This could also break any existing
client snapshots that depend on a file's path.

If dealing with very large files, the current solution is to manually delete
such files. Otherwise, ignoring them shouldn't have any consequence. The
challenge in always deleting the files, is that the path data is stored on the
server. We could deprecate entries or otherwise mark them as deleted, but
removing them altogether would cause us to lose the corresponding path to
delete at. We will add file entry deletion support in a future release.

