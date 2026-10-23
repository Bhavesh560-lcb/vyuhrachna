package com.vyuhrachna.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SMS સેટિંગ, બાકી રિપ્લાય અને નોટિફિકેશન સંભાળે છે. */
public final class Store {
    private Store() {}

    static final String CH_GJ = "gj_alert";
    static final String CH_REP = "sms_reply";
    static final String CH_UPD = "app_update";
    private static final Pattern CGI = Pattern.compile("\\b(40[45])[\\s\\-/]*(\\d{2,3})[\\s\\-/]*(\\d{1,6})[\\s\\-/]*(\\d{1,9})\\b");
    private static final Pattern MOB = Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?([6-9]\\d{9})(?!\\d)");

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("vyuh", Context.MODE_PRIVATE);
    }

    static JSONObject cfg(Context c) {
        try {
            return new JSONObject(prefs(c).getString("cfg", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void setCfg(Context c, String json) {
        prefs(c).edit().putString("cfg", json == null ? "{}" : json).apply();
    }

    static String norm(String x) {
        return x == null ? "" : x.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** કંપનીના નંબર / Sender ID સાથે મેળ ખાય છે? */
    static boolean senderMatch(Context c, String from) {
        JSONArray s = cfg(c).optJSONArray("senders");
        if (s == null) return false;
        String f = norm(from);
        if (f.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            String k = norm(s.optString(i));
            if (k.length() < 3) continue;
            if (k.matches("\\d+")) {
                // નંબર: પૂરો સરખો, અથવા આગળ +91 / 0 જેવો નાનો ફેર જ
                if (f.equals(k) || (f.endsWith(k) && f.length() - k.length() <= 3)) return true;
            } else if (f.equals(k) || f.contains(k)) {
                return true; // Sender ID: AD-AIRTEL, VM-AIRTEL-S વગેરે
            }
        }
        return false;
    }

    /** મેસેજમાં તાજેતરમાં મોકલેલો આરોપીનો નંબર છે? */
    static boolean bodyHasSentNumber(Context c, String body) {
        if (body == null) return false;
        try {
            JSONObject sent = new JSONObject(prefs(c).getString("sentNums", "{}"));
            long limit = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
            Matcher m = MOB.matcher(body);
            while (m.find()) {
                String n = m.group(1);
                if (sent.optLong(n, 0) > limit) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** ફક્ત ટીક કરેલા / આપમેળે મોકલાતા નંબરનો રિપ્લાય */
    static boolean watchMatch(Context c, String body) {
        JSONArray w = cfg(c).optJSONArray("watch");
        if (w == null || w.length() == 0) return false;
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (int i = 0; i < w.length(); i++) set.add(w.optString(i));
        boolean any = false;
        Matcher m = MOB.matcher(body == null ? "" : body);
        while (m.find()) {
            any = true;
            if (set.contains(m.group(1))) return true;
        }
        if (any) return false;
        // નંબર વગરનો રિપ્લાય: છેલ્લા 2 કલાકમાં ટીક કરેલા નંબર માટે SMS મોકલ્યો હોય તો જ
        try {
            JSONObject sent = new JSONObject(prefs(c).getString("sentNums", "{}"));
            long lim = System.currentTimeMillis() - 2L * 3600 * 1000;
            for (String n : set) if (sent.optLong(n, 0) > lim) return true;
        } catch (Exception ignored) {}
        return false;
    }

    static boolean enabled(Context c) {
        return cfg(c).optBoolean("enabled", true);
    }

    /** ફક્ત સેટ કરેલા કંપનીના નંબર / Sender ID ના SMS — બીજા કોઈ અંગત SMS નહીં */
    static boolean isReply(Context c, String from, String body) {
        if (!enabled(c)) return false;
        if (cfg(c).optBoolean("strict", true)) return senderMatch(c, from) && watchMatch(c, body);
        return senderMatch(c, from) || bodyHasSentNumber(c, body);
    }

    /** આપમેળે મોકલેલા SMS નો લોગ — એપ ખૂલે ત્યારે JS લઈ લે છે */
    static synchronized void addSentLog(Context c, String num, String to, String body, boolean ok, String err) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString("sentLog", "[]"));
            JSONObject o = new JSONObject();
            o.put("num", num).put("to", to).put("body", body).put("ok", ok).put("err", err == null ? "" : err).put("ts", System.currentTimeMillis());
            a.put(o);
            while (a.length() > 500) a.remove(0);
            prefs(c).edit().putString("sentLog", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized String takeSentLog(Context c) {
        String s = prefs(c).getString("sentLog", "[]");
        prefs(c).edit().putString("sentLog", "[]").apply();
        return s;
    }

    static synchronized void rememberSent(Context c, String num) {
        if (num == null || num.isEmpty()) return;
        try {
            JSONObject sent = new JSONObject(prefs(c).getString("sentNums", "{}"));
            sent.put(num, System.currentTimeMillis());
            prefs(c).edit().putString("sentNums", sent.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized void addPending(Context c, String from, String body, long ts) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString("pending", "[]"));
            JSONObject o = new JSONObject();
            o.put("from", from);
            o.put("body", body);
            o.put("ts", ts);
            a.put(o);
            prefs(c).edit().putString("pending", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    static synchronized String takePending(Context c) {
        String s = prefs(c).getString("pending", "[]");
        prefs(c).edit().putString("pending", "[]").apply();
        return s;
    }

    /** ગુજરાતમાં છે? કારણ પાછું આપે, નહીં તો null */
    static final double[][] GJ_POLY = {{68.1,23.55},{68.45,24.05},{68.75,24.3},{69.6,24.3},{70.1,24.25},{70.6,24.42},{71.1,24.45},{71.5,24.65},{72.3,24.62},{72.9,24.55},{73.25,24.35},{73.45,23.95},{73.7,23.55},{74.0,23.35},{74.3,23.05},{74.48,22.85},{74.25,22.4},{74.05,22.05},{73.85,21.75},{73.85,21.5},{73.75,21.15},{73.65,20.85},{73.55,20.55},{73.4,20.25},{72.95,20.1},{72.65,20.15},{72.55,20.6},{72.5,21.1},{72.3,21.4},{71.6,20.85},{71.0,20.6},{70.3,20.75},{69.5,21.45},{68.85,22.15},{68.85,22.6},{68.4,23.1}};
    static final String GJ_CODES_DEF = "405-857, 404-98, 404-05, 404-24, 404-57";
    private static final Pattern LAT = Pattern.compile("(?i)\\bLAT(?:ITUDE)?\\s*[:\\-=]?\\s*(-?\\d{1,2}\\.\\d+)");
    private static final Pattern LON = Pattern.compile("(?i)\\bLONG?(?:ITUDE)?\\s*[:\\-=]?\\s*(-?\\d{2,3}\\.\\d+)");
    private static final Pattern LL = Pattern.compile("(-?\\d{1,2}\\.\\d{3,})\\s*[,\\s]\\s*(-?\\d{2,3}\\.\\d{3,})");
    private static final Pattern CELL = Pattern.compile("(?i)(?:CELL\\s*ID|CELLID)\\s*[:\\-]?\\s*(\\w+)");

    static boolean inGujarat(double lat, double lon) {
        boolean in = false;
        for (int i = 0, j = GJ_POLY.length - 1; i < GJ_POLY.length; j = i++) {
            double xi = GJ_POLY[i][0], yi = GJ_POLY[i][1], xj = GJ_POLY[j][0], yj = GJ_POLY[j][1];
            if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) in = !in;
        }
        return in;
    }

    static String gujaratReason(Context c, String body) {
        if (body == null) return null;
        JSONObject cfg = cfg(c);
        // IMSI / IMEI / MSISDN લાઇન લોકેશન નથી
        StringBuilder sb = new StringBuilder();
        for (String l : body.split("\n")) if (!l.trim().toUpperCase(Locale.ROOT).matches("^(IMSI|IMEI|MSISDN|REQ).*")) sb.append(l).append('\n');
        String tl = sb.toString();
        // 1. Lat/Long
        Double la = null, lo = null;
        Matcher a = LAT.matcher(body), b = LON.matcher(body);
        if (a.find() && b.find()) { la = Double.valueOf(a.group(1)); lo = Double.valueOf(b.group(1)); }
        else { Matcher m = LL.matcher(body); if (m.find()) { la = Double.valueOf(m.group(1)); lo = Double.valueOf(m.group(2)); } }
        if (la != null) return inGujarat(la, lo) ? "Lat/Long ગુજરાતમાં" : null;
        // 2. Cell ID નું MCC-MNC
        String codes = cfg.optString("gjCodes", "");
        if (codes.trim().isEmpty()) codes = GJ_CODES_DEF;
        Matcher cm = CELL.matcher(tl);
        if (cm.find()) {
            String x = cm.group(1).replaceAll("[^0-9A-Fa-f]", "");
            String mm = x.startsWith("405") && x.length() >= 6 ? x.substring(0, 6) : x.startsWith("404") && x.length() >= 5 ? x.substring(0, 5) : "";
            if (!mm.isEmpty()) for (String k : codes.split("[,\\n]")) { String kk = k.replaceAll("\\D", ""); if (!kk.isEmpty() && kk.equals(mm)) return "ટાવર " + k.trim() + " (ગુજરાત)"; }
        }
        body = tl;
        String up = body.toUpperCase(Locale.ROOT);
        Matcher m = CGI.matcher(body);
        if (m.find() && !codes.trim().isEmpty()) {
            String cgi = m.group(1) + "-" + m.group(2) + "-" + m.group(3) + "-" + m.group(4);
            for (String k : codes.split("[,\\n]")) {
                String kk = k.replaceAll("\\s", "");
                if (!kk.isEmpty() && cgi.startsWith(kk)) return "MCC-MNC " + kk;
            }
        }
        String keys = cfg.optString("gjKeys", "GUJARAT");
        for (String k : keys.split(",")) {
            String kk = k.trim();
            if (kk.isEmpty()) continue;
            String KK = kk.toUpperCase(Locale.ROOT);
            if (KK.matches("[A-Z ]+")) {
                if (Pattern.compile("\\b" + Pattern.quote(KK).replace(" ", "\\E\\s*\\Q") + "\\b").matcher(up).find()) return kk;
            } else if (body.contains(kk)) {
                return kk;
            }
        }
        return null;
    }

    static String firstMobile(String body) {
        if (body == null) return "";
        Matcher m = MOB.matcher(body);
        return m.find() ? m.group(1) : "";
    }

    /** SmsReceiver અહીં બોલાવે છે */
    static void incoming(Context c, String from, String body, long ts) {
        if (!isReply(c, from, body)) return;
        addPending(c, from, body, ts);
        String why = gujaratReason(c, body);
        String num = firstMobile(body);
        if (why != null) {
            notify(c, CH_GJ, "🚨 આરોપી ગુજરાતમાં! " + num, body, true);
        } else {
            notify(c, CH_REP, "📩 કંપનીનો રિપ્લાય " + num, body, false);
        }
        MainActivity a = MainActivity.current;
        if (a != null) a.onNewReplies();
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel gj = new NotificationChannel(CH_GJ, "ગુજરાત એલર્ટ", NotificationManager.IMPORTANCE_HIGH);
            gj.setDescription("આરોપીનું લોકેશન ગુજરાતમાં આવે ત્યારે");
            gj.enableVibration(true);
            gj.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 800});
            nm.createNotificationChannel(gj);
            NotificationChannel rp = new NotificationChannel(CH_REP, "કંપનીના રિપ્લાય", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(rp);
            NotificationChannel up = new NotificationChannel(CH_UPD, "એપ અપડેટ", NotificationManager.IMPORTANCE_HIGH);
            up.setDescription("વ્યૂહરચનાનું નવું વર્ઝન આવે ત્યારે");
            nm.createNotificationChannel(up);
        }
    }

    static void notify(Context c, String ch, String title, String text, boolean high) {
        try {
            ensureChannels(c);
            Intent open = new Intent(c, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            open.putExtra("openTab", CH_UPD.equals(ch) ? "update" : "sms");
            PendingIntent pi = PendingIntent.getActivity(c, (int) (System.currentTimeMillis() & 0xffff), open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, ch)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setPriority(high ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_DEFAULT);
            if (high && !CH_UPD.equals(ch)) {
                b.setCategory(NotificationCompat.CATEGORY_ALARM);
                b.setVibrate(new long[]{0, 400, 200, 400, 200, 800});
                b.setColor(0xFFDC2626);
            }
            NotificationManagerCompat.from(c).notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (SecurityException ignored) {
            // નોટિફિકેશનની મંજૂરી નથી
        } catch (Exception ignored) {}
    }
}
