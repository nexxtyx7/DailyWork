
package com.example.dailywork;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.*;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String PREF = "dw_alarm";

    static void sync(Context c, String json, boolean vibrate, boolean sound) {
        try {
            SharedPreferences prefs =
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE);

            JSONArray old = new JSONArray(
                prefs.getString("items", "[]")
            );

            AlarmManager am =
                (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);

            for (int i = 0; i < old.length(); i++) {
                cancel(c, am, old.getJSONObject(i).optString("id"));
            }

            prefs.edit()
                .putString("items", json)
                .putBoolean("vibrate", vibrate)
                .putBoolean("sound", sound)
                .apply();

            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);

                if (r.optBoolean("enabled", true)) {
                    schedule(
                        c,
                        am,
                        r.optString("id"),
                        r.optString("name"),
                        r.optString("time")
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static PendingIntent pending(
        Context c,
        String id,
        String name
    ) {
        Intent intent = new Intent(c, ReminderReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("name", name);

        return PendingIntent.getBroadcast(
            c,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void cancel(
        Context c,
        AlarmManager am,
        String id
    ) {
        if (id == null || id.isEmpty()) return;

        PendingIntent pi = pending(c, id, "");

        am.cancel(pi);
        pi.cancel();
    }

    private static void schedule(
        Context c,
        AlarmManager am,
        String id,
        String name,
        String time
    ) {
        try {
            if (id == null || id.isEmpty()) return;

            String[] parts = time.split(":");

            if (parts.length < 2) return;

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            if (hour < 0 || hour > 23 ||
                minute < 0 || minute > 59) return;

            Calendar cal = Calendar.getInstance();

            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (!cal.after(Calendar.getInstance())) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            PendingIntent pi = pending(c, id, name);

            if (Build.VERSION.SDK_INT >= 23) {
                if (Build.VERSION.SDK_INT >= 31 &&
                    !am.canScheduleExactAlarms()) {

                    am.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.getTimeInMillis(),
                        pi
                    );

                } else {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.getTimeInMillis(),
                        pi
                    );
                }
            } else {
                am.setExact(
                    AlarmManager.RTC_WAKEUP,
                    cal.getTimeInMillis(),
                    pi
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void restore(Context c) {
        try {
            SharedPreferences prefs =
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE);

            JSONArray arr = new JSONArray(
                prefs.getString("items", "[]")
            );

            AlarmManager am =
                (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);

                if (r.optBoolean("enabled", true)) {
                    schedule(
                        c,
                        am,
                        r.optString("id"),
                        r.optString("name"),
                        r.optString("time")
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibrar(Context c) {
        try {
            Vibrator vibrator =
                (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator == null || !vibrator.hasVibrator()) return;

            long[] pattern = {0, 220, 80, 220, 80, 320};

            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                );
            } else {
                vibrator.vibrate(pattern, -1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pulsarBarrinha(Context c) {
        try {
            Intent intent = new Intent(c, OverlayService.class);
            intent.setAction(OverlayService.ACTION_PULSE);

            c.startService(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarNotificacao(
        Context c,
        String id,
        String name,
        boolean vibrate,
        boolean sound
    ) {
        try {
            NotificationManager nm =
                (NotificationManager) c.getSystemService(
                    Context.NOTIFICATION_SERVICE
                );

            if (Build.VERSION.SDK_INT >= 33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (Build.VERSION.SDK_INT >= 24 &&
                !nm.areNotificationsEnabled()) {
                return;
            }

            String channelId =
                "dw_reminder_" + (vibrate ? "v1" : "v0")
                    + "_" + (sound ? "s1" : "s0");

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel =
                    new NotificationChannel(
                        channelId,
                        "Lembretes DailyWork",
                        NotificationManager.IMPORTANCE_HIGH
                    );

                channel.enableVibration(false);

                if (sound) {
                    Uri uri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION
                    );

                    AudioAttributes attributes =
                        new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build();

                    channel.setSound(uri, attributes);
                } else {
                    channel.setSound(null, null);
                }

                nm.createNotificationChannel(channel);
            }

            Notification.Builder builder;

            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(c, channelId);
            } else {
                builder = new Notification.Builder(c);
            }

            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("DailyWork · " + name)
                .setContentText("Está na hora de conferir seus deveres.")
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);

            if (Build.VERSION.SDK_INT < 26) {
                int defaults = 0;

                if (sound) defaults |= Notification.DEFAULT_SOUND;
                if (vibrate) defaults |= Notification.DEFAULT_VIBRATE;

                builder.setDefaults(defaults);
            }

            nm.notify(id.hashCode(), builder.build());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restore(c);
            return;
        }

        String id = intent.getStringExtra("id");

        if (id == null) return;

        SharedPreferences prefs =
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        try {
            JSONArray arr = new JSONArray(
                prefs.getString("items", "[]")
            );

            JSONObject reminder = null;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);

                if (r.optString("id").equals(id) &&
                    r.optBoolean("enabled", true)) {
                    reminder = r;
                    break;
                }
            }

            if (reminder == null) return;

            String name = reminder.optString("name", "Lembrete");
            String time = reminder.optString("time", "");

            AlarmManager am =
                (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);

            // Agenda novamente para o próximo dia.
            schedule(c, am, id, name, time);

            boolean vibrate = prefs.getBoolean("vibrate", true);
            boolean sound = prefs.getBoolean("sound", true);

            if (vibrate) {
                vibrar(c);
            }

            // Envia a animação para a barrinha.
            pulsarBarrinha(c);

            // Exibe a notificação.
            mostrarNotificacao(c, id, name, vibrate, sound);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
