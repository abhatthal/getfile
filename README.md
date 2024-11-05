# GetFile

GetFile is a simple yet powerful Java library to keep files up to date on client systems.
Host your own files (i.e. data, code, anything really) over HTTP(S) and attribute a version to each file on the client and server (See sections below for demonstration).


When invoked in your application, GetFile will
* compare file versions on client against server
* optionally prompt or silently continue on a per-file basis
* download newer files when available
* validate downloads for corruption
* provide logic to rollback updates (See sections below for integration with your app)


GetFile is a subtle nod to GetDown (https://github.com/threerings/getdown), which is used to update Java applications themselves. GetDown wraps the application and allows you to update seamlessly to newer versions hosted on your own server. This requires updating the entire application, code and data included, and must wrap around a main class. GetFile was created for greater flexibility versioning any files, inside the codebase or anywhere on the client's computer, without wrapping a main class but instead being invoked by the application directly.


## Download
TODO: Provide library jar release for download and instructions for
      setting dependency (Gradle, Maven, Ant) and invocation at main.
      
TODO: Link to example JSON configuration and API documentation.

## Building
You can build your own thin or Fat JAR file from source to use as a dependency in your application.
* `gradle shadowJar`
* Copy Fat JAR `build/libs/getfile-all.jar` into your project lib directory
* Thin JAR is also available at `build/libs/getfile.jar`, but you must manually specify dependencies
* In a Gradle project, you would add using `implementation files('lib/getfile-all.jar')`

## Adding Files
TODO: Finish this section
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
* Update client's `getfile.json` with new file at `v0.0.0` and set install path.
  As all versions are > v0.0.0, this ensures the new file will be downloaded.

## Updating Files
TODO: Explain how to modify an existing entry on the server for clients to recognize changes.

## Deleting Files
TODO: Explain how to remove files from server and stop clients from searching for these changes.

TODO: Update library to prompt users to delete local files when removed from server and update local configuration accordingly.

## Example
TODO: Finish this section

Show config used for testing, with JSON files on server and client.

Explain what each parameter does in detail.

## Notable Apps
TODO: Mention how OpenSHA utilizes GetFile and provide a link to the corresponding code.
