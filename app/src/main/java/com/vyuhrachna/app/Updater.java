package com.vyuhrachna.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** નવું વર્ઝન ચેક / ડાઉનલોડ (GitHub Pages પરથી) */
public final class Updater {
    private Updater() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("vyuh_upd", Context.MODE_PRIVATE);
    }

    static File updDir(Context c) {
        return new File(c.getFilesDir(), "upd");
    }

    static int bundledVersion(Context c) {
        try (InputStream in = c.getAssets().open("www/version.json")) {
            return new JSONObject(new String(readAll(in), "UTF-8")).optInt("version", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean updActive(Context c) {
        return prefs(c).getInt("updVersion", 0) > bundledVersion(c);
    }

    /** નવી APK માં જૂના ડાઉનલોડ કરતાં નવું વર્ઝન હોય તો જૂનું કાઢી નાખો */
    static void cleanup(Context c) {
        if (!updActive(c)) {
            deleteRec(updDir(c));
            prefs(c).edit().remove("updVersion").apply();
        }
    }

    static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] ch = f.listFiles();
        if (ch != null) for (File x : ch) deleteRec(x);
        f.delete();
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    static byte[] get(String url) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                continue;
            }
            if (code != 200) throw new Exception("HTTP " + code + " — " + url);
            try (InputStream in = c.getInputStream()) {
                return readAll(in);
            }
        }
        throw new Exception("redirects");
    }

    static String base(String b) {
        return b.endsWith("/") ? b : b + "/";
    }

    /** બધી ફાઇલ પહેલાં tmp માં, પછી એકસાથે upd માં — અડધું ડાઉનલોડ થાય તો જૂનું ચાલુ રહે */
    static void download(Context c, String baseUrl, JSONArray files, int ver) throws Exception {
        String b = base(baseUrl);
        File tmp = new File(c.getFilesDir(), "upd_tmp");
        deleteRec(tmp);
        tmp.mkdirs();
        for (int i = 0; i < files.length(); i++) {
            String name = files.optString(i);
            if (name.isEmpty() || name.contains("..")) continue;
            byte[] data = get(b + name + "?t=" + System.currentTimeMillis());
            File out = new File(tmp, name);
            File p = out.getParentFile();
            if (p != null) p.mkdirs();
            try (FileOutputStream fo = new FileOutputStream(out)) {
                fo.write(data);
            }
        }
        File upd = updDir(c);
        deleteRec(upd);
        if (!tmp.renameTo(upd)) throw new Exception("rename");
        prefs(c).edit().putInt("updVersion", ver).putInt("curVersion", ver).apply();
    }

    static void setInfo(Context c, String url, int cur) {
        prefs(c).edit().putString("url", base(url)).putInt("curVersion", cur).apply();
        schedule(c);
    }

    static void schedule(Context c) {
        try {
            Constraints cons = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(UpdateWorker.class, 2, TimeUnit.HOURS).setConstraints(cons).build();
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("vyuh_update_check", ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (Exception ignored) {}
    }
}
