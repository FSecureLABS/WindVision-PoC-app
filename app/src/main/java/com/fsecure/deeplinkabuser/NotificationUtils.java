package com.fsecure.deeplinkabuser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationUtils {
    public static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Updates";                                                             // some more deceiving strings, as they would appear in the Notification Settings
            String description = "Wind Vision account updates";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("Updates", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    public static void createHackNotification(Context context, HackedInfo hackedInfo) {
        String notificationText = "Your app PIN code is: " + hackedInfo.getPinCode();
        for (int i = 0; i < 4; i++) {
            notificationText += "\n\tdevice seen: " + hackedInfo.getDevices().get(i);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "Updates")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Your Wind Vision Account Has Been Hacked!")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(null,true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(99, builder.build());
    }
}
