# User Interface

GetFile as a framework should be largely isolated from non-technical users of the applications that leverage GetFile.
Features like forcing downloads even when already up to date, reverting to older versions, or changing the per-file prompt settings
should not be done by manually editing metadata. It is the responsibility of the application that uses GetFile to create interfaces
that correctly handle these files. Incorrectly handling downloaded files and their metadata can result in applications believing a file
is available or at a specified version when that may not be the case, potentially resulting in a NullPointerException,
FileNotFoundException,or other runtime exception resulting in undefined behavior.

Having said that, this document briefly outlines how to correctly edit such metadata for testing purposes and as a frame of reference
for application developers who wish to correctly edit metadata.

## Forcing Downloads
You can delete the entire directory in which downloaded files and the corresponding cached server and client metadata are stored to
delete everything and have GetFile redownload instead of relying on its cache.

If you only want to delete a specific file, you must also delete the corresponding metadata. A client metadata file contains metadata
for more than one file, so you must navigate to the corresponding file entry in that metadata file and reset the marked version.
If the version is set to the empty string, or any other outdated version, this will force GetFile to redownload on the next download
invocation and overwrite the existing file. Prior to this, you could also delete the file altogether.

Consider the following example where we want to force downloads for the "full_logic_tree.zip" file.

![getfile-force-downloads](https://github.com/user-attachments/assets/92aa45d9-93b4-48d5-945e-5c388a677424)


## Toggling Prompts
This feature isn't currently supported, but we can still show how to edit file metadata such that this feature would work in the
near future.

There is an optional client metadata parameter called "prompt", which is equal to the String "true" or "false". If not provided, this
parameter defaults to "false". If a value other than "true" or "false" is provided, an error is logged to stderr, but it still
assumes a value of "false" and doesn't stop the application.

If you add a "prompt": "true" entry to a given file's metadata, then whenever a new download is available, the user will receive a
pop up message asking if they'd like to "Update Now", "Later", or "Skip this Version". When false, it simply downloads the latest
version silently in the background without prompting the user.

The following example will prompt the user when a new download is available for the full_logic_tree, but not for the rakeMean entry.
![getfile-set-prompt](https://github.com/user-attachments/assets/600e2cfe-20f1-4103-9d7a-775c97b61c46)
