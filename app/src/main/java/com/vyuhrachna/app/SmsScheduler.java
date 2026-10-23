package com.vyuhrachna.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** નંબર મુજબ દર X મિનિટે કંપનીને લોકેશન SMS — એપ બંધ હોય તો પણ (AlarmManager) */
public final class SmsScheduler {
    private SmsScheduler() {}

    static final String ACTION = "com.vyuhrachna.app.SCHED_SMS";

    static JSONArray load(Context c) {
        try {
            return new JSONArray(Store.prefs(c).getString("sched", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void save(Context c, JSONArray a) {
        Store.prefs(c).edit().putString("sched", a.toString()).apply();
    }

    /** JS માંથી નવું લિસ્ટ — પહેલાનો "next" સમય જાળવી રાખે */
    static synchronized void set(Context c, String json, int subId) {
        try {
            JSONArray old = load(c);
            JSONArray in = new JSONArray(json);
            JSONArray out = new JSONArray();
            long now = System.currentTimeMillis();
            for (int i = 0; i < in.length(); i++) {
                JSONObject o = in.getJSONObject(i);
                int every = o.optInt("every", 0);
                if (every <= 0 || o.optString("to").isEmpty()) continue;
                long next = 0;
                for (int k = 0; k < old.length(); k++) {
                    JSONObject p = old.getJSONObject(k);
                    if (p.optString("num").equals(o.optString("num")) && p.optInt("every") == every) next = p.optLong("next", 0);
                }
                if (next <= 0) next = now + 60_000L; // પહેલું SMS 1 મિનિટ પછી
                o.put("next", next);
                o.put("subId", subId);
                out.put(o);
            }
            save(c, out);
        } catch (Exception ignored) {}
        reschedule(c);
    }

    static PendingIntent pi(Context c) {
        Intent it = new Intent(c, SchedReceiver.class);
        it.setAction(ACTION);
        return PendingIntent.getBroadcast(c, 4401, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static boolean canExact(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    static synchronized void reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pi(c));
        if (!Store.enabled(c)) return;
        JSONArray a = load(c);
        long min = Long.MAX_VALUE;
        for (int i = 0; i < a.length(); i++) {
            long n = a.optJSONObject(i).optLong("next", 0);
            if (n > 0 && n < min) min = n;
        }
        if (min == Long.MAX_VALUE) return;
        long at = Math.max(min, System.currentTimeMillis() + 5_000L);
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c));
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c));
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c));
        }
    }

    /** સમય થયેલા બધા નંબરને SMS */
    static synchronized void runDue(Context c) {
        if (!Store.enabled(c)) return;
        JSONArray a = load(c);
        long now = System.currentTimeMillis();
        boolean sentAny = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long next = o.optLong("next", 0);
            if (next > now + 20_000L) continue;
            String num = o.optString("num"), to = o.optString("to"), body = o.optString("body");
            int every = Math.max(1, o.optInt("every", 60));
            boolean ok = true;
            String err = "";
            try {
                send(c, to, body, o.optInt("subId", 0));
                Store.rememberSent(c, num);
            } catch (Exception e) {
                ok = false;
                err = String.valueOf(e.getMessage());
            }
            Store.addSentLog(c, num, to, body, ok, err);
            sentAny = true;
            long nn = next + every * 60_000L;
            if (nn <= now) nn = now + every * 60_000L;
            try {
                o.put("next", nn);
                o.put("last", now);
            } catch (Exception ignored) {}
        }
        save(c, a);
        reschedule(c);
        if (sentAny) {
            MainActivity m = MainActivity.current;
            if (m != null) m.onSchedSent();
        }
    }

    static void send(Context c, String to, String body, int subId) {
        SmsManager sm;
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager base = c.getSystemService(SmsManager.class);
            sm = subId > 0 ? base.createForSubscriptionId(subId) : base;
        } else {
            sm = subId > 0 ? SmsManager.getSmsManagerForSubscriptionId(subId) : SmsManager.getDefault();
        }
        ArrayList<String> parts = sm.divideMessage(body);
        sm.sendMultipartTextMessage(to, null, parts, null, null);
    }
}
