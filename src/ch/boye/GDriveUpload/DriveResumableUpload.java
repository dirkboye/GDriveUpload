package ch.boye.GDriveUpload;

import java.net.URI;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.json.XML;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

public class DriveResumableUpload {

    private DriveAuth auth;
    private long file_size;
    private String mime_type;
    private String location;
    private URI uri;
    private ConsoleLog log = ConsoleLog.getInstance();
    private String google_filename;
    private boolean use_old_api;

    public DriveResumableUpload(String upload_location) throws IOException, URISyntaxException {
        auth = new DriveAuth();
        mime_type = GDriveUploadOptions.getInstance().getMimeType();
        file_size = GDriveUploadOptions.getInstance().getLocalFileSize();
        google_filename = GDriveUploadOptions.getInstance().getGoogleTitle();
        use_old_api = GDriveUploadOptions.getInstance().getUseOldAPI();
        if (upload_location == null) {
            location = createResumableUpload();
        } else {
            location = upload_location;
        }
        URIBuilder urib = new URIBuilder(location);
        uri = urib.build();
    }

    public void checkMD5() throws IOException {
        log.log("Querying metadata of completed upload...");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        BasicHttpRequest httpreq = new BasicHttpRequest("PUT", location);
        httpreq.addHeader("Authorization", auth.getAuthHeader());
        httpreq.addHeader("Content-Length", "0");
        httpreq.addHeader("Content-Range", "bytes */" + getFileSizeString());
        log.log(httpreq, null);
        CloseableHttpResponse response = httpclient.execute(URIUtils.extractHost(uri), httpreq);
        BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
        EntityUtils.consume(response.getEntity());
        log.log(response, entity);
        String retSrc = EntityUtils.toString(entity);
        if (use_old_api) {
            // Old API will return XML!
            JSONObject result = XML.toJSONObject(retSrc);
            log.log("id          : " + result.getJSONObject("entry").getString("gd:resourceId").replace("file:", ""));
            log.log("title       : " + result.getJSONObject("entry").getString("title"));
            log.log("link        : " + result.getJSONObject("entry").getJSONArray("link").getJSONObject(0).getString("href"));
            log.log("md5Checksum : " + result.getJSONObject("entry").getString("docs:md5Checksum"));
        } else {
            JSONObject result = new JSONObject(retSrc);
            log.log("id          : " + result.getString("id"));
            log.log("title       : " + result.getString("title"));
            log.log("link        : " + result.getString("webContentLink"));
            log.log("md5Checksum : " + result.getString("md5Checksum"));
        }
    }

    public boolean updateAccessToken() throws UnsupportedEncodingException, IOException {
        return auth.updateAccessToken();
    }

    private String getFileSizeString() {
        return Long.toString(file_size);
    }

    public String getLocation() {
        return location;
    }

    public long getCurrentByte() throws IOException {
        log.log1("Querying status of resumable upload...");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        BasicHttpRequest httpreq = new BasicHttpRequest("PUT", location);
        httpreq.addHeader("Authorization", auth.getAuthHeader());
        httpreq.addHeader("Content-Length", "0");
        httpreq.addHeader("Content-Range", "bytes */" + getFileSizeString());
        log.log(httpreq, null);
        CloseableHttpResponse response = httpclient.execute(URIUtils.extractHost(uri), httpreq);
        BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
        EntityUtils.consume(response.getEntity());
        log.log(response, entity);
        long lastbyte = -1;
        try {
            if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 201) {
                lastbyte = file_size;
            }
            if (response.getStatusLine().getStatusCode() == 308) {
                if (response.getHeaders("Range").length > 0) {
                    String range = response.getHeaders("Range")[0].getValue();
                    String[] parts = range.split("-");
                    lastbyte = Long.parseLong(parts[1]) + 1;
                } else {
                    // nothing uploaded, but file is there to start upload!
                    lastbyte = 0;
                }
            }
        } finally {
            response.close();
        }
        httpclient.close();

        return lastbyte;
    }

    public int uploadChunk(byte[] bytecontent, long start_range, int bytes_in_array) throws IOException {
        log.log(String.format("% 5.1f%% complete. Uploading next chunk.", start_range*100.0/file_size));
        String byterange = "bytes " + Long.toString(start_range) + "-" +
                Long.toString(start_range+bytes_in_array-1) + "/" + getFileSizeString();
        if (start_range+bytes_in_array-1 >= file_size) {
            log.log("Trying to push more than remaining bytes. Aborting.");
            System.exit(0);
        }
        log.log1("Uploading " + byterange);
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(location);
        httpPut.addHeader("Authorization", auth.getAuthHeader());
        httpPut.addHeader("Content-Range", byterange);
        if (bytes_in_array != bytecontent.length) {
            log.log1("Seems to be the last part of the file.");
            byte[] contentpart = new byte[bytes_in_array];
            for (int i=0; i<bytes_in_array;++i) {
                contentpart[i] = bytecontent[i];
            }
            httpPut.setEntity(new ByteArrayEntity(contentpart));
        } else {
            httpPut.setEntity(new ByteArrayEntity(bytecontent));
        }
        log.log(httpPut, null);
        CloseableHttpResponse response = httpclient.execute(httpPut);
        BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
        EntityUtils.consume(response.getEntity());
        log.log(response, entity);
        int status_code = response.getStatusLine().getStatusCode();
        response.close();
        httpclient.close();
        return status_code;
    }

    private String createResumableUpload() throws IOException {
        log.log("Creating resumable upload...");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String postUri = "https://www.googleapis.com/upload/drive/v2/files?uploadType=resumable";
        if (use_old_api) {
            postUri = "https://docs.google.com/feeds/upload/create-session/default/private/full?convert=false";
        }
        HttpPost httpPost = new HttpPost(postUri);
        httpPost.addHeader("Authorization", auth.getAuthHeader());
        httpPost.addHeader("X-Upload-Content-Type", mime_type);
        httpPost.addHeader("X-Upload-Content-Length", getFileSizeString());
        String entity_string = new JSONObject().put("title", google_filename).toString();
        BasicHeader entity_header = new BasicHeader(HTTP.CONTENT_TYPE, "application/json");
        if (use_old_api) {
            entity_string = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:docs=\"http://schemas.google.com/docs/2007\">" +
                    "<category scheme=\"http://schemas.google.com/g/2005#kind\" term=\"http://schemas.google.com/docs/2007#document\"/>" +
                    "<title>" + google_filename + "</title></entry>";
            httpPost.addHeader("GData-Version","3");
            //httpPost.addHeader("Content-Type", "application/atom+xml");
            entity_header = new BasicHeader(HTTP.CONTENT_TYPE, "application/atom+xml");
        } else {
            //httpPost.addHeader("Content-Type", "application/json");
        }
        StringEntity se = new StringEntity( entity_string );
        se.setContentType(entity_header);
        httpPost.setEntity(se);
        log.log(httpPost, null);
        CloseableHttpResponse response = httpclient.execute(httpPost);
        BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
        EntityUtils.consume(response.getEntity());
        log.log(response,entity);
        String location = "";
        try {
            if (response.getStatusLine().getStatusCode() == 200) {
                location = response.getHeaders("Location")[0].getValue();
                log.log1("Location: " + location);
            }
        } finally {
            response.close();
        }
        httpclient.close();
        return location;
    }
}
