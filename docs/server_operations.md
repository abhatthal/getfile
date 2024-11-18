# Server Operations
Outline CRUD operations for server filesystem.

It's not as simple as just uploading, modifying, or deleting a file on the server.
Since GetFile needs to track file versions and validate downloads, we need to
maintain file metadata and precomputed md5 checksums. This document outlines
how we can do this as necessary for the GetFile client-side logic parsing.

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
update the MD5sums accordingly. Deleting the server file entry altogether will
result in the client deleting the corresponding file and its client file entry.

Checks for deleted file entries only occur when the `updateAll` method is invoked.
It would otherwise be unnecessarily expensive to iterate over all files for an
`updateFile` invocation. It also would be illogical to delete files unrelated to
the given `updateFile(file)` call.

