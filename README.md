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
* [server_operations](docs/server_operations.md) - File server CRUD
* [testing](docs/testing.md) - Get started with unit tests in Gradle
 
## Future Plans
* Add the ability to skip versions
* Implement per-file prompting.

## Demo
Demonstrations of how to leverage the GetFile lib in your application.

https://github.com/abhatthal/getfile-demo

## Building
You can build your own thin or Fat JAR file from source to use as a dependency in your application.
* `gradle build`
* Copy Fat JAR `libs/getfile-all.jar` into your project lib directory
* Thin JAR is also available at `libs/getfile.jar`, but you must manually specify dependencies
* In a Gradle project, you would add using `implementation files('libs/getfile-all.jar')`

