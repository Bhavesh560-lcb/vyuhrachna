package com.vyuhrachna.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static volatile MainActivity current;

    private static final String START_URL = "https://appassets.androidplatform.net/app/index.html";
    private static final String ACTION_SENT = "com.vyuhrachna.app.SMS_SENT";
    private static final int REQ_FILE = 11;
    private static final int REQ_TREE = 7311;
    private static final int REQ_PERM = 12;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int reqCounter = 100;
    private boolean pageReady = false;
    private String pendingShare;
    private String pendingTab;

    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!i.getBooleanExtra("last", true)) return;
            String num = i.getStringExtra("num");
            int code = getResultCode();
            String st = code == Activity.RESULT_OK ? "sent" : "failed";
            js("window.onSmsStatus&&onSmsStatus(" + JSONObject.quote(num == null ? "" : num) + "," + JSONObject.quote(st) + "," + JSONObject.quote(String.valueOf(code)) + ")");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        Store.ensureChannels(this);
        Updater.cleanup(this);
        SmsScheduler.reschedule(this);
        Updater.schedule(this);

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // બે આંગળીથી ઝૂમ ઇન / આઉટ (+ − બટન વગર)
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/app/", new LocalPathHandler(this))
                .addPathHandler("/pick/", new FolderReader(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String sc = u.getScheme() == null ? "" : u.getScheme();
                boolean internal = "appassets.androidplatform.net".equals(u.getHost());
                if (!internal) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                flushPendingToPage();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "ફાઇલ પસંદ કરો"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new AppBridge(), "AndroidApp");
        web.addJavascriptInterface(new SmsBridge(), "AndroidSMS");

        ContextCompat.registerReceiver(this, sentReceiver, new IntentFilter(ACTION_SENT), ContextCompat.RECEIVER_NOT_EXPORTED);

        readIntent(getIntent());
        askBasicPermissions();
        web.loadUrl(START_URL);
    }

    /** શરૂઆતમાં ફક્ત નોટિફિકેશન — SMS ની મંજૂરી ફક્ત SMS તપાસ ખોલે ત્યારે (એડમીન) */
    private void askBasicPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        String[] base = {Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE};
        for (String p : base) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    private boolean has(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void readIntent(Intent i) {
        if (i == null) return;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            String t = i.getStringExtra(Intent.EXTRA_TEXT);
            if (t != null && !t.trim().isEmpty()) pendingShare = t;
        }
        String tab = i.getStringExtra("openTab");
        if (tab != null) pendingTab = tab;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        if (pageReady) flushPendingToPage();
    }

    private void flushPendingToPage() {
        if (pendingShare != null) {
            js("window.handleNativeShare&&handleNativeShare(" + JSONObject.quote(pendingShare) + ")");
            pendingShare = null;
        }
        if (pendingTab != null) {
            if ("update".equals(pendingTab)) js("window.checkUpdate&&checkUpdate(true)");
            else js("window.showTab&&showTab(" + JSONObject.quote(pendingTab) + ")");
            pendingTab = null;
        }
        js("window.onNativeSmsPending&&onNativeSmsPending()");
    }

    void onSchedSent() {
        js("window.onSchedSent&&onSchedSent()");
    }

    void onNewReplies() {
        if (pageReady) js("window.onNativeSmsPending&&onNativeSmsPending()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = this;
        if (pageReady) js("window.onNativeSmsPending&&onNativeSmsPending()");
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(sentReceiver);
        } catch (Exception ignored) {}
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE) {
            final Uri tree = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            if (tree == null) {
                js("window.onNativeFolderCancel&&onNativeFolderCancel()");
                return;
            }
            new Thread(() -> {
                String json;
                try {
                    json = FolderReader.list(MainActivity.this, tree).toString();
                } catch (Exception e) {
                    try {
                        json = new JSONObject().put("error", String.valueOf(e.getMessage())).toString();
                    } catch (Exception x) {
                        json = "{}";
                    }
                }
                js("window.onNativeFolder&&onNativeFolder(" + JSONObject.quote(json) + "," + JSONObject.quote(FolderReader.treeName(MainActivity.this, tree)) + ")");
            }).start();
            return;
        }
        if (requestCode == REQ_FILE && fileCb != null) {
            Uri[] res = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    res = new Uri[n];
                    for (int k = 0; k < n; k++) res[k] = data.getClipData().getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    res = new Uri[]{data.getData()};
                }
            }
            fileCb.onReceiveValue(res);
            fileCb = null;
        }
    }

    void js(final String code) {
        handler.post(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    private void sendOne(String to, String body, String num, int subId) {
        try {
            SmsManager sm;
            if (Build.VERSION.SDK_INT >= 31) {
                SmsManager base = getSystemService(SmsManager.class);
                sm = subId > 0 ? base.createForSubscriptionId(subId) : base;
            } else {
                sm = subId > 0 ? SmsManager.getSmsManagerForSubscriptionId(subId) : SmsManager.getDefault();
            }
            ArrayList<String> parts = sm.divideMessage(body);
            ArrayList<PendingIntent> sent = new ArrayList<>();
            for (int k = 0; k < parts.size(); k++) {
                Intent it = new Intent(ACTION_SENT);
                it.setPackage(getPackageName());
                it.putExtra("num", num);
                it.putExtra("last", k == parts.size() - 1);
                sent.add(PendingIntent.getBroadcast(this, reqCounter++, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            sm.sendMultipartTextMessage(to, null, parts, sent, null);
            Store.rememberSent(this, num);
            js("window.onSmsStatus&&onSmsStatus(" + JSONObject.quote(num) + ",'queued','')");
        } catch (Exception e) {
            js("window.onSmsStatus&&onSmsStatus(" + JSONObject.quote(num) + ",'error'," + JSONObject.quote(String.valueOf(e.getMessage())) + ")");
        }
    }

    /** ફાઇલ સેવ / શેર / લિંક */
    public class AppBridge {
        @JavascriptInterface
        public String version() {
            return "1.0";
        }

        @JavascriptInterface
        public void pickFolder() {
            handler.post(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(i, REQ_TREE);
                } catch (Exception e) {
                    js("window.onNativeFolderCancel&&onNativeFolderCancel();toast('આ ફોનમાં ફોલ્ડર પસંદ કરવાની સુવિધા નથી','err')");
                }
            });
        }

        @JavascriptInterface
        public int nativeVersion() {
            try {
                android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public void setUpdateInfo(String url, int cur) {
            if (url != null && url.startsWith("https://")) Updater.setInfo(MainActivity.this, url, cur);
        }

        @JavascriptInterface
        public void updNotify(String title, String text) {
            Store.notify(MainActivity.this, Store.CH_UPD, title, text, true);
        }

        @JavascriptInterface
        public void applyUpdate(final String baseUrl, final String filesJson, final int ver) {
            if (baseUrl == null || !baseUrl.startsWith("https://")) return;
            new Thread(() -> {
                try {
                    Updater.download(MainActivity.this, baseUrl, new JSONArray(filesJson), ver);
                    handler.post(() -> {
                        if (web != null) {
                            web.clearCache(false);
                            web.loadUrl(START_URL);
                        }
                    });
                } catch (Exception e) {
                    js("window.onNativeUpdate&&onNativeUpdate(false," + JSONObject.quote(String.valueOf(e.getMessage())) + ")");
                }
            }).start();
        }

        @JavascriptInterface
        public void openUrl(String url) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String saveBase64(String name, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vyuhrachna");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u == null) return "err:insert";
                    try (OutputStream os = getContentResolver().openOutputStream(u)) {
                        if (os == null) return "err:stream";
                        os.write(data);
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vyuhrachna");
                    if (!dir.exists()) dir.mkdirs();
                    try (FileOutputStream os = new FileOutputStream(new File(dir, name))) {
                        os.write(data);
                    }
                }
                return "ok";
            } catch (Exception e) {
                return "err:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public String shareFile(String name, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(data);
                }
                final Uri u = FileProvider.getUriForFile(MainActivity.this, "com.vyuhrachna.app.files", f);
                final Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, u);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                handler.post(() -> {
                    try {
                        startActivity(Intent.createChooser(send, "શેર કરો"));
                    } catch (Exception ignored) {}
                });
                return "ok";
            } catch (Exception e) {
                return "err:" + e.getMessage();
            }
        }
    }

    /** SMS મોકલવા અને રિપ્લાય વાંચવા */
    public class SmsBridge {
        @JavascriptInterface
        public String ready() {
            return has(Manifest.permission.SEND_SMS) && has(Manifest.permission.RECEIVE_SMS) ? "1" : "0";
        }

        @JavascriptInterface
        public void requestPerms() {
            handler.post(MainActivity.this::askPermissions);
        }

        @JavascriptInterface
        public void setConfig(String json) {
            Store.setCfg(MainActivity.this, json);
        }

        @JavascriptInterface
        public String getSims() {
            JSONArray out = new JSONArray();
            try {
                if (!has(Manifest.permission.READ_PHONE_STATE)) return out.toString();
                SubscriptionManager sm = getSystemService(SubscriptionManager.class);
                if (sm == null) return out.toString();
                List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
                if (list == null) return out.toString();
                for (SubscriptionInfo si : list) {
                    JSONObject o = new JSONObject();
                    o.put("id", si.getSubscriptionId());
                    o.put("slot", si.getSimSlotIndex() + 1);
                    o.put("name", String.valueOf(si.getDisplayName()));
                    o.put("carrier", String.valueOf(si.getCarrierName()));
                    out.put(o);
                }
            } catch (Exception ignored) {}
            return out.toString();
        }

        @JavascriptInterface
        public void sendBatch(String json, int delayMs, int subId) {
            if (!has(Manifest.permission.SEND_SMS)) {
                js("window.onSmsStatus&&onSmsStatus('','error','SEND_SMS મંજૂરી નથી')");
                handler.post(MainActivity.this::askPermissions);
                return;
            }
            try {
                JSONArray a = new JSONArray(json);
                long gap = Math.max(2000, delayMs);
                for (int i = 0; i < a.length(); i++) {
                    final JSONObject o = a.getJSONObject(i);
                    handler.postDelayed(() -> sendOne(o.optString("to"), o.optString("body"), o.optString("num"), subId), i * gap);
                }
            } catch (Exception e) {
                js("window.onSmsStatus&&onSmsStatus('','error'," + JSONObject.quote(String.valueOf(e.getMessage())) + ")");
            }
        }

        @JavascriptInterface
        public void setSchedule(String json, int subId) {
            SmsScheduler.set(MainActivity.this, json, subId);
        }

        @JavascriptInterface
        public String getSchedule() {
            return SmsScheduler.load(MainActivity.this).toString();
        }

        @JavascriptInterface
        public String takeSentLog() {
            return Store.takeSentLog(MainActivity.this);
        }

        @JavascriptInterface
        public String canFullScreen() {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
                    return nm != null && nm.canUseFullScreenIntent() ? "1" : "0";
                }
            } catch (Exception ignored) {}
            return "1";
        }

        @JavascriptInterface
        public void askFullScreen() {
            handler.post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 34) startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void testAlert() {
            Store.notify(MainActivity.this, Store.CH_GJ, "🚨 ટેસ્ટ એલર્ટ", "આ ફક્ત ટેસ્ટ છે — આ રીતે ગુજરાત એલર્ટ આવશે", true);
            AlertActivity.show(MainActivity.this, "🚨 ટેસ્ટ એલર્ટ", "આ ફક્ત ટેસ્ટ છે — આરોપી ગુજરાતમાં આવે ત્યારે આ રીતે સ્ક્રીન પર આવશે અને રીંગ વાગશે.");
        }

        @JavascriptInterface
        public String canExact() {
            return SmsScheduler.canExact(MainActivity.this) ? "1" : "0";
        }

        @JavascriptInterface
        public void askExact() {
            handler.post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 31) {
                        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void askBattery() {
            handler.post(() -> {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception ignored) {}
                }
            });
        }

        /** કંપનીનું Sender ID શોધવા — ફક્ત મોકલનારનું નામ અને નાની ઝલક, કંઈ સેવ નથી થતું */
        @JavascriptInterface
        public String readSenders(String sinceMs) {
            JSONArray out = new JSONArray();
            if (!has(Manifest.permission.READ_SMS)) return out.toString();
            long since = 0;
            try {
                since = Long.parseLong(sinceMs);
            } catch (Exception ignored) {}
            java.util.LinkedHashMap<String, JSONObject> map = new java.util.LinkedHashMap<>();
            try (Cursor c = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{"address", "body", "date"}, "date>?", new String[]{String.valueOf(since)}, "date DESC")) {
                if (c != null) {
                    while (c.moveToNext() && map.size() < 60) {
                        String from = c.getString(0);
                        if (from == null) continue;
                        JSONObject o = map.get(from);
                        if (o == null) {
                            o = new JSONObject();
                            String b = c.getString(1);
                            o.put("from", from).put("n", 0).put("snip", b == null ? "" : b.substring(0, Math.min(50, b.length())));
                            map.put(from, o);
                        }
                        o.put("n", o.optInt("n") + 1);
                    }
                }
            } catch (Exception ignored) {}
            for (JSONObject o : map.values()) out.put(o);
            return out.toString();
        }

        @JavascriptInterface
        public String getPending() {
            return Store.takePending(MainActivity.this);
        }

        @JavascriptInterface
        public String readInbox(String sinceMs) {
            JSONArray out = new JSONArray();
            if (!has(Manifest.permission.READ_SMS)) return out.toString();
            long since = 0;
            try {
                since = Long.parseLong(sinceMs);
            } catch (Exception ignored) {}
            try (Cursor c = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{"address", "body", "date"}, "date>?", new String[]{String.valueOf(since)}, "date ASC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        String from = c.getString(0);
                        String body = c.getString(1);
                        long ts = c.getLong(2);
                        if (!Store.isReply(MainActivity.this, from, body)) continue;
                        JSONObject o = new JSONObject();
                        o.put("from", from);
                        o.put("body", body);
                        o.put("ts", ts);
                        out.put(o);
                    }
                }
            } catch (Exception ignored) {}
            return out.toString();
        }
    }
}
