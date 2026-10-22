package com.vyuhrachna.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.WebResourceResponse;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** પસંદ કરેલા ફોલ્ડરની (પેટા-ફોલ્ડર સહિત) બધી ફાઇલોની યાદી + /pick/<id> થી ફાઇલ પૂરી પાડે */
public class FolderReader implements WebViewAssetLoader.PathHandler {
    static final Map<String, Uri> PICKED = new ConcurrentHashMap<>();
    static final Map<String, String> MIME = new ConcurrentHashMap<>();
    static final Set<String> EXT = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "xlsm", "csv", "ods", "txt", "json", "html", "htm", "png", "jpg", "jpeg", "webp", "bmp"));
    private final Context ctx;

    FolderReader(Context c) {
        ctx = c.getApplicationContext();
    }

    /** ફોલ્ડરનું નામ */
    static String treeName(Context c, Uri tree) {
        try {
            Uri d = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            try (Cursor cu = c.getContentResolver().query(d, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cu != null && cu.moveToFirst()) return cu.getString(0);
            }
        } catch (Exception ignored) {}
        return "ફોલ્ડર";
    }

    /** આખા ફોલ્ડરની યાદી JSON માં: [{id,name,path,size,mime}] */
    static JSONArray list(Context c, Uri tree) throws Exception {
        PICKED.clear();
        MIME.clear();
        JSONArray out = new JSONArray();
        String root = DocumentsContract.getTreeDocumentId(tree);
        walk(c.getContentResolver(), tree, root, treeName(c, tree) + "/", out, 0);
        return out;
    }

    private static void walk(ContentResolver cr, Uri tree, String docId, String path, JSONArray out, int depth) throws Exception {
        if (depth > 30 || out.length() > 5000) return;
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
        try (Cursor cu = cr.query(kids, cols, null, null, null)) {
            if (cu == null) return;
            while (cu.moveToNext()) {
                String id = cu.getString(0), name = cu.getString(1), mime = cu.getString(2);
                long size = cu.isNull(3) ? 0 : cu.getLong(3);
                if (name == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(cr, tree, id, path + name + "/", out, depth + 1);
                    continue;
                }
                int dot = name.lastIndexOf('.');
                String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
                if (!EXT.contains(ext) || name.startsWith(".") || name.startsWith("~$")) continue;
                String key = "f" + PICKED.size();
                PICKED.put(key, DocumentsContract.buildDocumentUriUsingTree(tree, id));
                MIME.put(key, mime == null ? "application/octet-stream" : mime);
                JSONObject o = new JSONObject();
                o.put("id", key).put("name", name).put("path", path + name).put("size", size).put("mime", mime);
                out.put(o);
            }
        }
    }

    @Override
    public WebResourceResponse handle(String path) {
        Uri u = PICKED.get(path);
        if (u == null) return null;
        try {
            InputStream in = ctx.getContentResolver().openInputStream(u);
            if (in == null) return null;
            return new WebResourceResponse(MIME.get(path), null, in);
        } catch (Exception e) {
            return null;
        }
    }
}
