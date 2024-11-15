# GetFile

GetFile is a simple yet powerful Java library to keep files up to date on client systems.
Host your own files (i.e. data, code, anything really) over HTTP(S) and attribute a version to each file on the client and server.


When invoked in your application, GetFile will
* compare file versions on client against server
* optionally prompt or silently continue on a per-file basis
* download newer files when available
* validate downloads for corruption
* provide logic to rollback updates (See sections below for integration with your app)


GetFile is a subtle nod to GetDown (https://github.com/threerings/getdown), which is used to update Java applications themselves. GetDown wraps the application and allows you to update seamlessly to newer versions hosted on your own server. This requires updating the entire application, code and data included, and must wrap around a main class. GetFile was created for greater flexibility versioning any files, inside the codebase or anywhere on the client's computer, without wrapping a main class but instead being invoked by the application directly.

## Demo
See the following demonstration of running GetFile with a local server.
https://github.com/abhatthal/getfile-demo

## Documation
[Usage Docs](https://docs.google.com/document/d/16REHLKR8EnmaNA8ecnroxkNgfLPCNU7ZImCjIMHjJts/edit?usp=sharing): Gradle build targets, project structure, and how to use GetFile methods.

## Downloads
TODO: Provide library jar release for download and instructions for
      setting dependency (Gradle, Maven, Ant) and invocation at main.

## Building
You can build your own thin or Fat JAR file from source to use as a dependency in your application.
* `gradle shadowJar`
* Copy Fat JAR `build/libs/getfile-all.jar` into your project lib directory
* Thin JAR is also available at `build/libs/getfile.jar`, but you must manually specify dependencies
* In a Gradle project, you would add using `implementation files('lib/getfile-all.jar')`

## Adding Files
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

## Updating Files
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
You can delete older versions as we run out of space, or keep them for as long as you'd like.
The client will overwrite the previous model1 (Either None, v0.1.1, or v0.1.2) at `$ClientRoot/models/model1/model.zip` with v0.1.3.

File trees generated via https://tree.nathanfriend.com/

## Deleting Files
TODO: Explain how to remove files from server and stop clients from searching for these changes.

TODO: Update library to prompt users to delete local files when removed from server and update local configuration accordingly.

## Notable Apps
TODO: Mention how OpenSHA utilizes GetFile and provide a link to the corresponding code.
