
package com.example.dailywork;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.*;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_CODE = 100;
    private static final int FILE_CHOOSER_REQUEST = 200;
    private static final int ALARM_PERMISSION_CODE = 400;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new DailyWorkBridge(), "DailyWorkNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                String script =
                    "(function() {" +
                    "let last='';" +
                    "function sync() {" +
                    "try {" +
                    "let p=JSON.parse(localStorage.getItem('dw_profile')||'{}');" +
                    "let s=JSON.parse(localStorage.getItem('dw_student_settings')||'{}');" +
                    "let duties=JSON.parse(localStorage.getItem('dw_duties')||'[]');" +
                    "let today=new Date().toLocaleDateString('en-CA');" +
                    "let items=duties.filter(d=>d.date===today);" +
                    "let tasks=items.slice(0,3).map(d=>'• '+d.title+(d.subject?' · '+d.subject:'' )).join('\\n')||'Nenhum dever para hoje.';" +
                    "let c={name:p.name||'Aluno',photo:p.photo||'',glass:!!s.glass,intensity:Number(s.intensity||65),effect:s.effect||'soft',tasks:tasks,duties:duties};" +
                    "let value=JSON.stringify(c);" +
                    "if(value!==last){last=value;DailyWorkNative.updateConfig(value);}" +
                    "}catch(e){}" +
                    "}" +
                    "sync();setInterval(sync,1000);" +
                    "})();";

                view.evaluateJavascript(script, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
            ) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");

                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "Selecionar foto"),
                        FILE_CHOOSER_REQUEST
                    );
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }

                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/dailywork-alunos.html");

        verificarPermissao();
    }

    public class DailyWorkBridge {

        @JavascriptInterface
        public void updateConfig(String json) {
            try {
                JSONObject data = new JSONObject(json);

                String photo = data.optString("photo", "");
                String photoPath = "";

                if (photo.startsWith("data:image")) {
                    int comma = photo.indexOf(",");
                    if (comma > 0) {
                        byte[] bytes = Base64.decode(
                            photo.substring(comma + 1), Base64.DEFAULT
                        );

                        File file = new File(getFilesDir(), "dailywork-avatar");
                        FileOutputStream output = new FileOutputStream(file);
                        output.write(bytes);
                        output.close();

                        photoPath = file.getAbsolutePath();
                    }
                }

                data.remove("photo");
                data.put("photoPath", photoPath);

                Intent intent = new Intent(MainActivity.this, OverlayService.class);
                intent.putExtra("config", data.toString());

                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void scheduleReminders(String json, boolean vibrate, boolean sound) {
            ReminderReceiver.sync(MainActivity.this, json, vibrate, sound);
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                runOnUiThread(() -> {
                    if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                        requestPermissions(
                            new String[]{"android.permission.POST_NOTIFICATIONS"},
                            300
                        );
                    }
                });
            }
        }

        @JavascriptInterface
        public void vibrate() {
            runOnUiThread(() -> {
                try {
                    Vibrator vibrator =
                        (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                    if (vibrator != null && vibrator.hasVibrator()) {
                        long[] pattern = {0, 220, 80, 220, 80, 320};

                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(
                                VibrationEffect.createWaveform(pattern, -1)
                            );
                        } else {
                            vibrator.vibrate(pattern, -1);
                        }
                    }

                    Intent pulse = new Intent(MainActivity.this, OverlayService.class);
                    pulse.setAction(OverlayService.ACTION_PULSE);

                    if (Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(pulse);
                    } else {
                        startService(pulse);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void verificarPermissao() {
        if (Settings.canDrawOverlays(this)) {
            iniciarBarrinha();
            solicitarPermissaoAlarmes();
        } else {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE);
        }
    }

    private void solicitarPermissaoAlarmes() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am =
                (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent intent = new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())
                    );
                    startActivityForResult(intent, ALARM_PERMISSION_CODE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void iniciarBarrinha() {
        if (Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(this, OverlayService.class);

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;

                if (resultCode == RESULT_OK &&
                    data != null &&
                    data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }

                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }

        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                iniciarBarrinha();
            }

            solicitarPermissaoAlarmes();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
