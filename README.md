# GetFile

TODO: Overview

## How to add new versioned files
TODO
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


## How to integrate in your application
TODO: Provide library jar release for download and instructions for
      setting dependency and invocation at main.
