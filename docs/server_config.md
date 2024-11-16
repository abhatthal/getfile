# Server Configuration

All logic is done on the client-side, so the only requirements of our file
server are to provide data structured in a way that GetFile can interpret.

The core requirements of any valid GetFile server configuration are as follows:
 * There must be 1 hosted metadata JSON file per GetFile instance.
 * Each file entry in this JSON file must specify the version to download and path to file
 * The paths to file must be relative to the location of the metadata.

Paths may refer directly to the file we wish to track, or they may refer to
symbolic links to those files. In most cases, refering to the symbolic links
provides flexibility to make structural changes and retain multiple versions.

It is imperative that version numbers are NOT stored in the path of the file.
The files are downloaded on the client at the same path as specified on the
server, so duplicate files would occur if paths were to change across versions.

## Example

## Filesystem
The following example filesystem has two separate projects with files tracked through GetFile.

The first project has one large JSON file tracking all of its files. The second project is much more complex, and benefits from breaking the data into multiple JSON files. 

.
└── projects/
    ├── project1/
    │   ├── meta.json (tracks project1 models)
    │   ├── meta.json.md5
    │   └── models/
    │       ├── model1/
    │       │   ├── model.zip (symlink => v0.1.2/model.zip)
    │       │   ├── model.zip.md5
    │       │   ├── v0.1.1/
    │       │   │   └── model.zip
    │       │   └── v0.1.2/
    │       │       └── model.zip
    │       └── model2/
    │           ├── model.zip (symlink => v1.0.0/model.zip)
    │           ├── model.zip.md5
    │           └── v1.0.0/
    │               └── model.zip
    └── project2/
        └── planes/
            ├── boeing.json (tracks boeing plane versions)
            ├── boeing.json.md5
            ├── boeing/
            │   ├── 737/
            │   │   ├── 737.md (symlink => v2.3.0/737.md)
            │   │   ├── 737.md.md5
            │   │   ├── 737.dat (symlink => v2.3.0/737.dat)
            │   │   ├── 737.dat.md5
            │   │   ├── README.md (symlink => v2.3.0/README.md)
            │   │   ├── README.md.md5
            │   │   ├── old-file (symlink => v0.3.4/old-file)
            │   │   ├── old-file.md5
            │   │   ├── v0.3.4/
            │   │   │   ├── 737.md
            │   │   │   ├── 737.dat
            │   │   │   └── old-file
            │   │   └── v2.3.0/
            │   │       ├── 737.md
            │   │       ├── 737.dat
            │   │       └── README.md
            │   └── 767/
            │       ├── 767.md (symlink => v0.1.0/767.md)
            │       ├── 767.md.md5
            │       └── v0.1.0/
            │           └── 767.md
            ├── airbus.json (tracks airbus plane versions)
            ├── airbus.json.md5
            └── airbus/
                └── a220/
                    ├── finances.xlsx (symlink => v24.11)
                    ├── finances.xlsx.md5
                    └── v24.11/
                        ├── finances.xlsx
                        └── [

File tree generated via [tree.nathanfriend.com](https://shorturl.at/YbDRk)

It is the responsibility of the client to specify the clientPath where server data should be downloaded and to invoke all the desired GetFile instances, with one per each serverMeta file. You can choose to load some or all of the serverMetas with updateAll, or only invoke a portion of any particular meta by directly calling updateFile with the corresponding GetFile instance.

## projects/project1/meta.json
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

## projects/project2/planes/boeing.json
```
{
	"737.md": {
		"version": "v2.3.0",
		"path": "boeing/737/737.md"
	},
	"737 Data": {
		"version": "v2.3.0",
		"path": "boeing/737/737.dat"
	},
	"737 Readme": {
		"version": "v2.3.0",
		"path": "boeing/737/README.md"
	},
	"737 OldFile": {
		"version": "v0.3.4",
		"path": "boeing/737/old-file"
	},
	"767.md": {
		"version": "v0.3.4",
		"path": "boeing/767/767.md"
	}
}
```

## projects/project2/planes/airbus.json
```
{
	"A220-300 Finances": {
		"version": "v24.11",
		"path": "airbus/a220/finances.xlsx"
	}
}
```

Each serverMeta will have its own GetFile instance on the client, with a unique clientMetaFile and root clientPath. Any other structuring on the client could result in files of the same name overwriting each other.

