package com.vyuhrachna.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** SMS ગયો / નિષ્ફળ — એપ બંધ / અટકી હોય તો પણ સ્થિતિ સચવાય (પછી JS લઈ લે) */
public class SentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !i.getBooleanExtra("last", true)) return;
        String num = i.getStringExtra("num");
        int code = getResultCode();
        String st = code == Activity.RESULT_OK ? "sent" : "failed";
        Store.addStatus(c.getApplicationContext(), num == null ? "" : num, st, String.valueOf(code));
        MainActivity a = MainActivity.current;
        if (a != null) a.onSmsStatusNative(num == null ? "" : num, st, String.valueOf(code));
    }
}
