package com.vyuhrachna.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

/** એપ બંધ હોય ત્યારે પણ દર 2 કલાકે નવું વર્ઝન ચેક કરી નોટિફિકેશન મોકલે */
public class UpdateWorker extends Worker {
    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = Updater.prefs(c);
        String url = p.getString("url", "");
        if (url == null || url.isEmpty()) return Result.success();
        try {
            JSONObject v = new JSONObject(new String(Updater.get(url + "version.json?t=" + System.currentTimeMillis()), "UTF-8"));
            int nv = v.optInt("version", 0);
            int cur = Math.max(p.getInt("curVersion", 0), Math.max(Updater.bundledVersion(c), p.getInt("updVersion", 0)));
            int last = p.getInt("lastNotified", 0);
            if (nv > cur && nv > last) {
                String note = "";
                if (v.optJSONArray("notes") != null && v.optJSONArray("notes").length() > 0) note = " — " + v.optJSONArray("notes").optString(0);
                Store.notify(c, Store.CH_UPD, "🔔 વ્યૂહરચનાનું નવું વર્ઝન આવ્યું (v" + nv + ")", "એપ ખોલીને અપડેટ કરો" + note, true);
                p.edit().putInt("lastNotified", nv).apply();
            }
        } catch (Exception e) {
            return Result.retry();
        }
        return Result.success();
    }
}
