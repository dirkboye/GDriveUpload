GDriveUpload
============

Upload files to Google Drive using resumable upload and variable chunk sizes. (Tested with files > 30GB)

Application can use new Drive API or old GData API. (Old API is default, to allow for uploads lasting longer than 1 hour.)  
When the upload finishes md5 checksums of the local file and the file on Drive are displayed.


Installation:
```
gradle distZip
```
Unzip result into directory of your choice and run GDriveUpload/bin/GDriveUpload

Goto [Google Developer Console](https://console.developers.google.com) and create a JSON for native applications. Drive API has to be activated.
Copy JSON file to ~/.GDriveUpload/GDriveUpload.json

```
usage: GDriveUpload
 -chunk_size_mb <arg>   Chunk size in megabytes.
 -google_title <arg>    Title of upload on Google Drive. If not set, local
                        filename will be used.
 -help                  Show this help.
 -log <arg>             Log level. 0-3  (2 - Show Request Line, Response
                        Status Line, 3 - Show full request/response)
 -md5 <arg>             Provide md5sum so it doesn't have to be
                        calculated.
 -metadata <arg>        Get metadata of file on Google Drive. (<arg>=id)
 -metatags <arg>        Tags to show additionally in the end. ( multiple
                        <arg> allowed. {a,b,c})
 -upload <arg>          Upload local file to Google Drive.
                        (<arg>=filename)
 -use_new_api           Use new Drive API. Uploads cannot last longer than
                        1 hour with the new API.
```
