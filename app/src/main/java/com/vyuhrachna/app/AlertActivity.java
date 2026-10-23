package com.vyuhrachna.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 🚨 ગુજરાત એલર્ટ — ફોન લૉક હોય / એપ બંધ હોય તો પણ આખી સ્ક્રીન પર, રીંગ સાથે */
public class AlertActivity extends Activity {
    private MediaPlayer mp;
    private Vibrator vib;
    private final Handler h = new Handler(Looper.getMainLooper());

    static void show(Context c, String title, String text) {
        try {
            Intent i = new Intent(c, AlertActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("title", title);
            i.putExtra("text", text);
            c.startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        build(getIntent());
        ring();
        h.postDelayed(this::stopRing, 60_000L); // 1 મિનિટ પછી રીંગ આપમેળે બંધ
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        build(intent);
        ring();
    }

    private void build(Intent in) {
        String title = in == null ? "" : in.getStringExtra("title");
        String text = in == null ? "" : in.getStringExtra("text");
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 3, pad, pad);
        root.setBackgroundColor(Color.parseColor("#7F1D1D"));
        TextView big = new TextView(this);
        big.setText("🚨");
        big.setTextSize(72);
        big.setGravity(Gravity.CENTER);
        root.addView(big);
        TextView t = new TextView(this);
        t.setText(title == null || title.isEmpty() ? "🚨 આરોપી ગુજરાતમાં!" : title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        root.addView(t);
        ScrollView sv = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(text == null ? "" : text);
        body.setTextColor(Color.parseColor("#FEE2E2"));
        body.setTextSize(16);
        body.setPadding(0, pad, 0, pad);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Button stop = btn("🔕 રીંગ બંધ કરો", "#FFFFFF", "#7F1D1D");
        stop.setOnClickListener(v -> {
            stopRing();
            finish();
        });
        root.addView(stop);
        Button open = btn("📱 એપમાં વિગત જુઓ", "#F5C451", "#3B0764");
        open.setOnClickListener(v -> {
            stopRing();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("openTab", "sms");
            startActivity(i);
            finish();
        });
        root.addView(open);
        setContentView(root);
    }

    private Button btn(String label, String bg, String fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor(fg));
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bg));
        d.setCornerRadius(40);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(lp);
        b.setPadding(20, 30, 20, 30);
        return b;
    }

    private void ring() {
        stopRing();
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(this, u);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
        } catch (Exception ignored) {}
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] p = {0, 600, 300, 600, 300, 1200};
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(p, 0));
                else vib.vibrate(p, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopRing() {
        try {
            if (mp != null) {
                mp.stop();
                mp.release();
            }
        } catch (Exception ignored) {}
        mp = null;
        try {
            if (vib != null) vib.cancel();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        stopRing();
        super.onDestroy();
    }
}
