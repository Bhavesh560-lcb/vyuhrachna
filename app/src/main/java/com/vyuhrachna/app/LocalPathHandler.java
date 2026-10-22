package com.vyuhrachna.app;

import android.content.Context;
import android.webkit.WebResourceResponse;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** /app/... — પહેલાં ડાઉનલોડ થયેલું નવું વર્ઝન (upd ફોલ્ડર), નહીં તો APK ની અંદરનું (assets/www) */
public class LocalPathHandler implements WebViewAssetLoader.PathHandler {
    private final Context ctx;

    LocalPathHandler(Context c) {
        ctx = c.getApplicationContext();
    }

    static String mime(String p) {
        p = p.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".wasm")) return "application/wasm";
        return "application/octet-stream";
    }

    @Override
    public WebResourceResponse handle(String path) {
        if (path == null || path.isEmpty()) path = "index.html";
        if (path.contains("..")) return null;
        try {
            InputStream in;
            File f = new File(Updater.updDir(ctx), path);
            if (Updater.updActive(ctx) && f.isFile()) in = new FileInputStream(f);
            else in = ctx.getAssets().open("www/" + path);
            String m = mime(path);
            boolean text = m.startsWith("text/") || m.contains("javascript") || m.contains("json");
            WebResourceResponse r = new WebResourceResponse(m, text ? "utf-8" : null, in);
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-cache");
            r.setResponseHeaders(h);
            return r;
        } catch (IOException e) {
            return null;
        }
    }
}
