package ch.boye.GDriveUpload;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.*;
import java.net.URISyntaxException;

class GDriveUpload {
    private static ConsoleLog log = ConsoleLog.getInstance();
    private static GDriveUploadOptions uploadOptions = GDriveUploadOptions.getInstance();

    public static void main(String[] args) {
        log.log("GDriveUpload");

        if (args.length == 0) {
           uploadOptions.showHelp();
        }

        uploadOptions.parseArgs(args);

        if (uploadOptions.getLocalFilename() != null) {
            uploadFile();
        }

        if (uploadOptions.getMetadataId() != null) {
            getMetadata();
        }

    }

    private static void uploadFile() {
        try {
            String google_location;
            DriveResumableUpload upload;

            String filestatus = uploadOptions.getLocalFilename() + ".GDriveUpload";
            File fstatus = new File(filestatus);
            if (fstatus.exists()) {
                BufferedReader in = new BufferedReader(new FileReader(filestatus));
                uploadOptions.setMd5(in.readLine());
                log.log("Got md5 from status file: " + uploadOptions.getMd5());
                google_location = in.readLine();
                log.log("Got location from status file: " + google_location);
                in.close();
                upload = new DriveResumableUpload(google_location);
            } else {
                if (uploadOptions.getMd5() == null) {
                    // No md5sum set. need to calculate it.
                    FileInputStream fis = new FileInputStream(new File(uploadOptions.getLocalFilename()));
                    uploadOptions.setMd5(org.apache.commons.codec.digest.DigestUtils.md5Hex(fis));
                    fis.close();
                }
                // Completely new upload. location: null
                upload = new DriveResumableUpload(null);

                // Write location and md5 to file for later resume
                BufferedWriter out = new BufferedWriter(new FileWriter(filestatus));
                out.write(uploadOptions.getMd5() + "\n" + upload.getLocation() + "\n");
                out.close();
            }
            long currentBytePosition = upload.getCurrentByte();
            File file = new File(uploadOptions.getLocalFilename());
            if (currentBytePosition > -1 && currentBytePosition < uploadOptions.getLocalFileSize()) {
                byte[] chunk;
                int retries = 0;
                while (retries < 5) {
                    FileInputStream stream = new FileInputStream(file);
                    if (currentBytePosition>0) {
                        stream.skip(currentBytePosition);
                    }
                    chunk = new byte[uploadOptions.getChunkSizeInBytes()];
                    int bytes_read = stream.read(chunk, 0, uploadOptions.getChunkSizeInBytes());
                    stream.close();
                    if (bytes_read > 0) {
                        int status = upload.uploadChunk(chunk, currentBytePosition, bytes_read);
                        if (status == 308) {
                            // If Status is 308 RESUME INCOMPLETE there's no retry done.
                            retries = 0;
                        } else if (status >= 500 && status < 600) {
                            // Good practice: Exponential backoff
                            try {
                                long seconds = Math.round(Math.pow(2, retries + 1));
                                log.log("Exponential backoff. Waiting " + seconds + " seconds.");
                                Thread.sleep(seconds*1000);
                            } catch(InterruptedException ex) {
                                log.log(ex.toString());
                                Thread.currentThread().interrupt();
                            }
                        } else if (status == 401) {
                            // No Authentication anymore
                            upload.updateAccessToken();
                        } else if (status == 200 || status == 201) {
                            upload.checkMD5();
                            log.log("local md5sum: " + uploadOptions.getMd5());
                            log.log("File upload complete.");
                            log.log("Deleting status file");
                            fstatus.delete();
                            System.exit(1);
                        }
                    }
                    ++retries;
                    currentBytePosition = upload.getCurrentByte();
                }
            } else if (currentBytePosition == uploadOptions.getLocalFileSize()) {
                // In the case there's no response of the last chunk but the file is completely uploaded
                // trying a resume on that complete upload will end up in this if clause.
                upload.checkMD5();
                log.log("local md5sum: " + uploadOptions.getMd5());
                log.log("File upload complete.");
                System.exit(1);
            } else {
                // Some BUG occured. lastbyte = -1.
                log.log("Error querying status of resumable upload.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }


    public static void getMetadata() {
        try {
            DriveAuth auth = new DriveAuth(false); // ALWAYS use new API to retrieve metadata.
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpGet httpGet= new HttpGet("https://www.googleapis.com/drive/v2/files/" + uploadOptions.getMetadataId() + "?access_token="+auth.getAccessToken());
            log.log(httpGet, null);
            CloseableHttpResponse response = httpclient.execute(httpGet);
            BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
            EntityUtils.consume(response.getEntity());
            log.log(response, entity);
            try {
                if (entity != null) {
                    String retSrc = EntityUtils.toString(entity);
                    JSONObject result = new JSONObject(retSrc);
                    System.out.println(result.toString(2));
                    System.out.println();
                    if (uploadOptions.getMetadataTags() != null) {
                        for (String tag : uploadOptions.getMetadataTags()) {
                            log.log(tag + ": " + result.getString(tag));
                        }
                    }
                }
            } finally {
                response.close();
            }
            httpclient.close();
        } catch (IOException e) {
            log.log(e.toString());
        }
    }
}