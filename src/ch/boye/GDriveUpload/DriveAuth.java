package ch.boye.GDriveUpload;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class DriveAuth {
    private String client_id;
    private String client_secret;
    private String redirect_uri;
    private String scope;

    private String access_token = "";
    private String token_type = "";
    private String refresh_token = "";

    private int max_retries = 3;

    private ConsoleLog log = ConsoleLog.getInstance();

    public DriveAuth() throws IOException {
        // Use setting from Options.
        this(GDriveUploadOptions.getInstance().getUseOldAPI());
    }

    public DriveAuth(boolean use_old_api) throws IOException {
        scope = "https://www.googleapis.com/auth/drive";
        if (use_old_api) {
            // Old API scope to ensure resumable upload can last longer than 1 hour.
            scope = "https://docs.google.com/feeds";
        }
        if (loadClientIDFromFile()) {
            loadRefreshTokenFromFile();
            int retry = 0;
            boolean tokensOK = false;
            while (!tokensOK && retry < max_retries) {
                tokensOK = updateAccessToken();
                ++retry;
            }
            if (!tokensOK) {
                log.log("Authentication aborted after " + max_retries + " retries.");
                System.exit(0);
            }
        } else {
            log.log("Please download Client ID JSON for native application from https://console.developers.google.com");
            log.log("Copy it to " + System.getProperty("user.home") + "/.GDriveUpload/GDriveUpload.json");
            System.exit(0);
        }
    }

    private boolean loadClientIDFromFile() {
        String json_filename = System.getProperty("user.home") + "/.GDriveUpload/GDriveUpload.json";
        File f = new File(json_filename);
        if (f.exists()) {
            try {
                InputStream is = new FileInputStream(json_filename);
                String jsonTxt = IOUtils.toString(is);
                JSONObject json = new JSONObject(jsonTxt).getJSONObject("installed");
                client_secret = json.getString("client_secret");
                client_id = json.getString("client_id");
                redirect_uri = json.getJSONArray("redirect_uris").getString(0);
                return true;
            } catch (FileNotFoundException e) {
                log.log(e.toString());
            } catch (IOException e) {
                log.log(e.toString());
            }
        }
        return false;
     }

    public String getAccessToken() {
        return access_token;
    }
    public String getTokenType() {
        return token_type;
    }
    public String getAuthHeader() {
        return token_type + " " + access_token;
    }

    private void loadRefreshTokenFromFile() throws IOException {
        // Loads refresh_token from file for given client_id, client_secret and scope
        String filename = System.getProperty("user.home") + "/.GDriveUpload/refresh_" + client_id + client_secret + scope.replace("/","").replace(":","") + ".txt";
        File f = new File(filename);
        if(f.exists() && !f.isDirectory()) {
            try {
                BufferedReader in = new BufferedReader(new FileReader(filename));
                refresh_token = in.readLine();
                in.close();
                log.log("Got refresh_token from file.");
            } catch (FileNotFoundException e) {
                log.log(e.toString());
            }
        }
    }

    private void saveRefreshTokenToFile() throws IOException {
        File saveDir = new File(System.getProperty("user.home") + "/.GDriveUpload");
        if(!saveDir.exists()){
            saveDir.mkdirs();
        }
        String filename = System.getProperty("user.home") + "/.GDriveUpload/refresh_" + client_id + client_secret + scope.replace("/","").replace(":","") + ".txt";
        BufferedWriter out = new BufferedWriter(new FileWriter(filename));
        out.write(refresh_token);
        out.close();
    }

    public boolean updateAccessToken() throws UnsupportedEncodingException, IOException {
        // If a refresh_token is set, this class tries to retrieve an access_token.
        // If refresh_token is no longer valid it resets all tokens to an empty string.
        if (refresh_token == "") {
            // If no refresh_token is set, the user has to enter an authentication code for
            // retrieval of refresh and access_token.
            return updateAllTokens();
        }
        log.log("Updating access_token from Google");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://accounts.google.com/o/oauth2/token");
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("client_id", client_id));
        nvps.add(new BasicNameValuePair("client_secret", client_secret));
        nvps.add(new BasicNameValuePair("refresh_token", refresh_token));
        nvps.add(new BasicNameValuePair("grant_type", "refresh_token"));
        BufferedHttpEntity postentity = new BufferedHttpEntity(new UrlEncodedFormEntity(nvps));
        httpPost.setEntity(postentity);
        log.log(httpPost, postentity);
        CloseableHttpResponse response = httpclient.execute(httpPost);
        BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
        EntityUtils.consume(response.getEntity());
        log.log(response, entity);
        boolean tokensOK = false;
        try {
            if (response.getStatusLine().getStatusCode() == 200 && entity != null) {
                String retSrc = EntityUtils.toString(entity);
                JSONObject result = new JSONObject(retSrc);
                access_token = result.getString("access_token");
                token_type = result.getString("token_type");
                tokensOK = true;
            }
        } finally {
            response.close();
        }
        httpclient.close();
        if (!tokensOK) {
            refresh_token = "";
            access_token = "";
            token_type = "";
        }
        return tokensOK;
    }

    private boolean updateAllTokens() throws UnsupportedEncodingException, IOException {
        log.log("Getting all authentication tokens from Google");
        String auth_code = getAuthCode();
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://accounts.google.com/o/oauth2/token");
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("code", auth_code));
        nvps.add(new BasicNameValuePair("client_id", client_id));
        nvps.add(new BasicNameValuePair("client_secret", client_secret));
        nvps.add(new BasicNameValuePair("redirect_uri", redirect_uri));
        nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));
        BufferedHttpEntity postentity = new BufferedHttpEntity(new UrlEncodedFormEntity(nvps));
        httpPost.setEntity(postentity);
        log.log(httpPost, postentity);
        CloseableHttpResponse response = httpclient.execute(httpPost);
        BufferedHttpEntity entity = new BufferedHttpEntity(response.getEntity());
        EntityUtils.consume(response.getEntity());
        log.log(response, entity);
        boolean tokensOK = false;
        try {
            if (response.getStatusLine().getStatusCode() == 200 && entity != null) {
                String retSrc = EntityUtils.toString(entity);
                JSONObject result = new JSONObject(retSrc);
                access_token = result.getString("access_token");
                token_type = result.getString("token_type");
                refresh_token = result.getString("refresh_token");
                saveRefreshTokenToFile();
                tokensOK = true;
            }
        } finally {
            response.close();
        }
        httpclient.close();
        return tokensOK;
    }

    private String getAuthCode() throws IOException {
        String authuri="https://accounts.google.com/o/oauth2/auth?scope=" + scope +
                "&redirect_uri=" + redirect_uri + "&response_type=code" +
                "&client_id=" + client_id;
        System.out.println("Please open following link in your browser:");
        System.out.println(authuri);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter authentication code: ");
        String s = br.readLine();
        return s;
    }

}
