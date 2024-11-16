## BackupManager Usage
The BackupManager keeps snapshots of the client data and corresponding metadata
to rollback to. Each manager tracks a single snapshot. Each GetFile instance
can have multiple BackupManagers. Each identifier must be unique or else
multiple instances will refer to the same backup files. We only allow
instantiating via the factory GetFile.getBackupManager to prevent duplicate
identifiers from occurring on a given GetFile instance. This also simplifies
the process of handling metadata between classes with the internal
MetadataHandler. We will not go into further detail on how the BackupManager
constructor works here, as it is never directly invoked by end-users.

## public void backup()
Creates backup files of all the files in the current client metadata for the
given GetFile instance, as well as the client metadata itself. Each backup has
a unique suffix as specified via the identifier to prevent conflicts across
multiple BackupManagers.
```
bm.backup();
```

## public boolean backupExists()
Returns true if backup() has already been invoked for the given BackupManager.
This method leverages the backup files to check for backups across sessions.
```
bm.backupExists();
```

## public int rollback()
Rolls back to the client state when the backup method was invoked. Does nothing
if there is no rollback found. This method will work across sessions. i.e. If a
backup was made, you restarted your computer and started the client application
to rollback, it would still work. Any files that were newly downloaded and
directories that were created in the process would be subsequently deleted. Do
note that rollback logic requires that the server path to the file still exist.
If a file entry was removed from the server (which shouldn’t occur), then we’d
be unable to successfully rollback that file.
```
bm1.rollback();
```

