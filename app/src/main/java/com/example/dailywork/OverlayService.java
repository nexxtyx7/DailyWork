
package com.example.dailywork;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import org.json.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {

    public static final String ACTION_PULSE =
        "com.example.dailywork.PULSE";

    private WindowManager wm;
    private FrameLayout bar;
    private LinearLayout content, userRow;
    private TextView nameView, subView, tasksView, dot, dateView;
    private ImageView avatar;
    private ScrollView tasksScroll;
    private WindowManager.LayoutParams params;

    private boolean expanded = false;
    private boolean added = false;
    private boolean moveMode = false;
    private boolean moved = false;

    private Handler dragHandler =
        new Handler(Looper.getMainLooper());

    private Runnable longPressRunnable;
    private ValueAnimator currentAnimator;

    private float downRawX, downRawY;
    private int startX, startY;

    private int intensity = 65;
    private boolean glass = false;

    private String effect = "soft";
    private String studentName = "Aluno";
    private String photoPath = "";
    private String tasks = "Tudo em dia.";
    private String selectedDate = hoje();

    private JSONArray allDuties = new JSONArray();

    private int collapsedWidth;
    private int expandedWidth;
    private int collapsedHeight;
    private int expandedHeight;

    private static final String CHANNEL_ID =
        "dailywork_overlay";

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private static String hoje() {
        return new SimpleDateFormat(
            "yyyy-MM-dd", Locale.US
        ).format(new Date());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        criarNotificacao();

        if (Settings.canDrawOverlays(this)) {
            iniciarOverlay();
        } else {
            stopSelf();
        }
    }

    private void criarNotificacao() {

        NotificationManager manager =
            (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE
            );

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                new NotificationChannel(
                    CHANNEL_ID,
                    "DailyWork ativo",
                    NotificationManager.IMPORTANCE_LOW
                )
            );
        }

        Notification notification;

        if (Build.VERSION.SDK_INT >= 26) {
            notification = new Notification.Builder(
                this, CHANNEL_ID
            )
                .setContentTitle("DailyWork")
                .setContentText("Barrinha de lembretes ativa")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("DailyWork")
                .setContentText("Barrinha de lembretes ativa")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
        }

        startForeground(1, notification);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void iniciarOverlay() {

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        collapsedWidth = dp(108);
        collapsedHeight = dp(37);

        expandedWidth =
            getResources().getDisplayMetrics().widthPixels - dp(24);

        expandedHeight = dp(220);

        bar = new FrameLayout(this);
        bar.setBackground(rounded(Color.rgb(3, 5, 10), 25));
        bar.setElevation(dp(8));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(8));

        userRow = new LinearLayout(this);
        userRow.setGravity(Gravity.CENTER_VERTICAL);
        userRow.setOrientation(LinearLayout.HORIZONTAL);

        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(
            rounded(Color.rgb(97, 127, 203), 50)
        );
        avatar.setClipToOutline(true);

        userRow.addView(
            avatar,
            new LinearLayout.LayoutParams(dp(27), dp(27))
        );

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);

        nameView = new TextView(this);
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(10);
        nameView.setTypeface(null, 1);
        nameView.setText(studentName);

        subView = new TextView(this);
        subView.setText("DailyWork · lembretes");
        subView.setTextColor(Color.rgb(174, 187, 212));
        subView.setTextSize(8);

        labels.addView(nameView);
        labels.addView(subView);
        userRow.addView(labels);

        dateView = new TextView(this);
        dateView.setTextColor(Color.rgb(115, 175, 255));
        dateView.setTextSize(11);
        dateView.setTypeface(null, 1);
        dateView.setGravity(Gravity.CENTER_VERTICAL);
        dateView.setPadding(dp(2), dp(9), dp(2), dp(9));
        dateView.setBackgroundColor(Color.TRANSPARENT);
        dateView.setOnClickListener(v -> abrirCalendario());

        tasksScroll = new ScrollView(this);
        tasksScroll.setFillViewport(false);
        tasksScroll.setVerticalScrollBarEnabled(true);

        tasksView = new TextView(this);
        tasksView.setTextColor(Color.rgb(226, 234, 255));
        tasksView.setTextSize(11);
        tasksView.setPadding(dp(3), dp(10), dp(3), dp(10));
        tasksView.setText(tasks);
        tasksView.setLineSpacing(dp(3), 1f);

        tasksScroll.addView(
            tasksView,
            new ScrollView.LayoutParams(-1, -2)
        );

        dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(12);
        dot.setTextColor(Color.rgb(124, 227, 189));
        dot.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams dotParams =
            new FrameLayout.LayoutParams(
                dp(20),
                dp(30),
                Gravity.LEFT | Gravity.CENTER_VERTICAL
            );

        dotParams.leftMargin = dp(8);

        bar.addView(
            content,
            new FrameLayout.LayoutParams(-1, -1)
        );

        bar.addView(dot, dotParams);

        content.addView(userRow);

        LinearLayout.LayoutParams dateParams =
            new LinearLayout.LayoutParams(-1, -2);
        dateParams.topMargin = dp(12);
        content.addView(dateView, dateParams);

        LinearLayout.LayoutParams scrollParams =
            new LinearLayout.LayoutParams(-1, 0, 1f);
        content.addView(tasksScroll, scrollParams);

        userRow.setVisibility(View.GONE);
        dateView.setVisibility(View.GONE);
        tasksScroll.setVisibility(View.GONE);

        int type =
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            collapsedWidth,
            collapsedHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        SharedPreferences positionPrefs =
            getSharedPreferences("DailyWorkPosition", MODE_PRIVATE);

        boolean hasPosition =
            positionPrefs.getBoolean("saved", false);

        params.gravity = Gravity.TOP |
            (hasPosition ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL);

        params.x = hasPosition
            ? positionPrefs.getInt("x", 0)
            : 0;

        params.y = hasPosition
            ? positionPrefs.getInt("y", dp(7))
            : dp(7);

        configurarMovimentacao();

        wm.addView(bar, params);
        added = true;

        atualizarDataETarefas();
        aplicarAparencia();
    }

    private void configurarMovimentacao() {

        bar.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    moveMode = false;
                    moved = false;

                    downRawX = event.getRawX();
                    downRawY = event.getRawY();

                    startX = params.x;
                    startY = params.y;

                    longPressRunnable = () -> {
                        moveMode = true;

                        bar.setBackground(
                            rounded(Color.rgb(20, 45, 70), 25)
                        );
                    };

                    dragHandler.postDelayed(
                        longPressRunnable, 3000
                    );

                    return true;

                case MotionEvent.ACTION_MOVE:

                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;

                    int limit =
                        ViewConfiguration.get(this)
                            .getScaledTouchSlop();

                    if (!moveMode &&
                        (Math.abs(dx) > limit ||
                         Math.abs(dy) > limit)) {
                        moved = true;
                    }

                    if (moveMode) {

                        moved = true;

                        params.gravity = Gravity.TOP | Gravity.LEFT;

                        int maxX =
                            getResources().getDisplayMetrics().widthPixels
                                - params.width;

                        int maxY =
                            getResources().getDisplayMetrics().heightPixels
                                - params.height;

                        params.x = Math.max(
                            0,
                            Math.min(maxX, startX + (int) dx)
                        );

                        params.y = Math.max(
                            0,
                            Math.min(maxY, startY + (int) dy)
                        );

                        try {
                            wm.updateViewLayout(bar, params);
                        } catch (Exception ignored) {}
                    }

                    return true;

                case MotionEvent.ACTION_UP:

                    dragHandler.removeCallbacks(longPressRunnable);

                    if (moveMode) {

                        getSharedPreferences(
                            "DailyWorkPosition", MODE_PRIVATE
                        )
                            .edit()
                            .putInt("x", params.x)
                            .putInt("y", params.y)
                            .putBoolean("saved", true)
                            .apply();

                        aplicarAparencia();

                    } else if (!moved) {
                        alternar();
                    }

                    moveMode = false;
                    return true;

                case MotionEvent.ACTION_CANCEL:

                    dragHandler.removeCallbacks(longPressRunnable);
                    moveMode = false;
                    return true;
            }

            return true;
        });
    }

    private void alternar() {

        if (currentAnimator != null &&
            currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }

        expanded = !expanded;

        userRow.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        dateView.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        tasksScroll.setVisibility(
            expanded ? View.VISIBLE : View.GONE
        );

        dot.setVisibility(
            expanded ? View.GONE : View.VISIBLE
        );

        int targetW = expanded
            ? expandedWidth
            : collapsedWidth;

        int targetH = expanded
            ? expandedHeight
            : collapsedHeight;

        int startW = params.width;
        int startH = params.height;

        currentAnimator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator.setDuration(380);

        currentAnimator.setInterpolator(
            new DecelerateInterpolator()
        );

        currentAnimator.addUpdateListener(a -> {

            float f = (float) a.getAnimatedValue();

            params.width =
                (int) (startW + (targetW - startW) * f);

            params.height =
                (int) (startH + (targetH - startH) * f);

            if (added) {
                try {
                    wm.updateViewLayout(bar, params);
                } catch (Exception ignored) {}
            }
        });

        currentAnimator.start();
        aplicarAparencia();
    }

    private void pulsarBarrinha() {

        if (bar == null || !added) return;

        bar.animate().cancel();
        bar.setScaleX(1f);
        bar.setScaleY(1f);

        bar.animate()
            .scaleX(1.10f)
            .scaleY(1.10f)
            .setDuration(180)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {

                if (bar != null && added) {
                    bar.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260)
                        .start();
                }
            })
            .start();
    }

    private void abrirCalendario() {

        Calendar calendar = Calendar.getInstance();

        try {
            SimpleDateFormat parser =
                new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            Date date = parser.parse(selectedDate);

            if (date != null) {
                calendar.setTime(date);
            }
        } catch (Exception ignored) {}

        DatePickerDialog dialog = new DatePickerDialog(
            this,
            (view, year, month, day) -> {

                selectedDate = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    day
                );

                atualizarDataETarefas();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );

        try {
            if (dialog.getWindow() != null) {
                int type =
                    Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

                dialog.getWindow().setType(type);
            }

            dialog.show();

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Não foi possível abrir o calendário.",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void atualizarDataETarefas() {

        if (dateView == null || tasksView == null) return;

        try {
            SimpleDateFormat parser =
                new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            Date date = parser.parse(selectedDate);

            SimpleDateFormat formatter =
                new SimpleDateFormat(
                    "EEEE, dd 'de' MMMM",
                    new Locale("pt", "BR")
                );

            String label = formatter.format(date);

            label = label.substring(0, 1).toUpperCase(
                new Locale("pt", "BR")
            ) + label.substring(1);

            dateView.setText("" + label);

        } catch (Exception e) {
            dateView.setText("" + selectedDate);
        }

        StringBuilder result = new StringBuilder();
        int count = 0;

        for (int i = 0; i < allDuties.length(); i++) {

            JSONObject duty = allDuties.optJSONObject(i);

            if (duty == null) continue;

            String date = duty.optString("date", "");

            if (!selectedDate.equals(date)) continue;

            String title = duty.optString(
                "title", "Dever sem título"
            );

            String subject = duty.optString("subject", "");

            if (count > 0) {
                result.append("\n\n");
            }

            result.append("• ");

            if (!subject.isEmpty()) {
                result.append(subject).append(" — ");
            }

            result.append(title);

            count++;
        }

        if (count == 0) {
            result.append("Nada programado para este dia.");
        }

        tasksView.setText(result.toString());
    }

    private void aplicarAparencia() {

        int alpha = glass
            ? (effect.equals("clear") ? 31 :
               effect.equals("crystal") ? 46 + intensity / 5 :
               31 + intensity / 2)
            : 252;

        GradientDrawable bg = rounded(
            Color.argb(
                Math.min(255, alpha),
                glass ? 205 : 3,
                glass ? 225 : 5,
                glass ? 255 : 10
            ),
            expanded ? 22 : 25
        );

        int borderAlpha = glass
            ? 46 + intensity / 2
            : 43;

        bg.setStroke(
            dp(1),
            Color.argb(
                Math.min(255, borderAlpha),
                220, 238, 255
            )
        );

        if (bar != null) {
            bar.setBackground(bg);
        }

        if (avatar != null) {
            if (!photoPath.isEmpty()) {

                Bitmap bitmap =
                    BitmapFactory.decodeFile(photoPath);

                if (bitmap != null) {
                    avatar.setImageBitmap(bitmap);
                }

            } else {
                avatar.setImageDrawable(null);
            }
        }

        if (nameView != null) {
            nameView.setText(studentName);
        }

        if (tasksView != null && allDuties.length() == 0) {
            tasksView.setText(tasks);
        }
    }

    private void atualizar(Intent intent) {

        if (intent == null) return;

        String json = intent.getStringExtra("config");

        if (json == null) return;

        try {

            JSONObject data = new JSONObject(json);

            studentName = data.optString("name", "Aluno");
            glass = data.optBoolean("glass", false);
            intensity = data.optInt("intensity", 65);
            effect = data.optString("effect", "soft");
            tasks = data.optString("tasks", "Tudo em dia.");
            photoPath = data.optString("photoPath", "");

            JSONArray receivedDuties =
                data.optJSONArray("duties");

            if (receivedDuties != null) {
                allDuties = receivedDuties;
            }

            if (bar != null) {
                atualizarDataETarefas();
                aplicarAparencia();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(
        Intent intent,
        int flags,
        int startId
    ) {

        if (intent != null &&
            ACTION_PULSE.equals(intent.getAction())) {

            pulsarBarrinha();

        } else {
            atualizar(intent);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        dragHandler.removeCallbacksAndMessages(null);

        if (currentAnimator != null) {
            currentAnimator.cancel();
            currentAnimator = null;
        }

        if (bar != null && added) {
            try {
                wm.removeView(bar);
            } catch (Exception ignored) {}
        }

        added = false;
        bar = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
