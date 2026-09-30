package com.zayonsystems.perpasig;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * PER Pasig native photo helper.
 *  - saveImage:   saves a stamped JPEG into the phone Gallery (Pictures/PER Pasig).
 *  - shareImages: opens the Android share sheet with one or many photos (Messenger, Viber, email...).
 * Called from the web app's Geotag Camera through window.Capacitor.
 */
@CapacitorPlugin(
    name = "PerMedia",
    permissions = { @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "storage") }
)
public class PerMediaPlugin extends Plugin {

    // ---------- Save to Gallery ----------
    @PluginMethod
    public void saveImage(PluginCall call) {
        // Android 10+ saves through MediaStore and needs no storage permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && getPermissionState("storage") != PermissionState.GRANTED) {
            requestPermissionForAlias("storage", call, "storagePermissionCallback");
            return;
        }
        doSave(call);
    }

    @PermissionCallback
    private void storagePermissionCallback(PluginCall call) {
        if (getPermissionState("storage") == PermissionState.GRANTED) {
            doSave(call);
        } else {
            call.reject("Storage permission denied");
        }
    }

    private void doSave(PluginCall call) {
        String b64 = call.getString("base64");
        String name = call.getString("filename", "PERPasig_photo.jpg");
        String album = call.getString("album", "PER Pasig");
        if (b64 == null || b64.isEmpty()) {
            call.reject("base64 is required");
            return;
        }
        try {
            byte[] bytes = decode(b64);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContext().getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + album);
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
                if (uri == null) throw new IOException("Gallery refused the photo");
                OutputStream os = resolver.openOutputStream(uri);
                if (os == null) throw new IOException("Cannot open Gallery file");
                try {
                    os.write(bytes);
                } finally {
                    os.close();
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album);
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create folder " + dir);
                File file = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    fos.write(bytes);
                } finally {
                    fos.close();
                }
                MediaScannerConnection.scanFile(getContext(), new String[] { file.getAbsolutePath() }, new String[] { "image/jpeg" }, null);
                uri = Uri.fromFile(file);
            }
            JSObject ret = new JSObject();
            ret.put("uri", uri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Save failed: " + e.getMessage(), e);
        }
    }

    // ---------- Share sheet ----------
    @PluginMethod
    public void shareImages(PluginCall call) {
        JSArray files = call.getArray("files");
        String title = call.getString("title", "Share photos");
        if (files == null || files.length() == 0) {
            call.reject("No photos to share");
            return;
        }
        try {
            String authority = getContext().getPackageName() + ".fileprovider";
            File shareDir = new File(getContext().getCacheDir(), "share");
            if (!shareDir.exists() && !shareDir.mkdirs()) throw new IOException("Cannot create share folder");
            ArrayList<Uri> uris = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject item = files.getJSONObject(i);
                String u = item.optString("uri", "");
                String b64 = item.optString("base64", "");
                String name = item.optString("filename", "PERPasig_photo_" + (i + 1) + ".jpg");
                if (u.startsWith("content://")) {
                    uris.add(Uri.parse(u));
                } else if (u.startsWith("file://")) {
                    uris.add(FileProvider.getUriForFile(getContext(), authority, new File(Uri.parse(u).getPath())));
                } else if (!b64.isEmpty()) {
                    File out = new File(shareDir, name);
                    FileOutputStream fos = new FileOutputStream(out);
                    try {
                        fos.write(decode(b64));
                    } finally {
                        fos.close();
                    }
                    uris.add(FileProvider.getUriForFile(getContext(), authority, out));
                }
            }
            if (uris.isEmpty()) {
                call.reject("No photos to share");
                return;
            }
            Intent send;
            if (uris.size() == 1) {
                send = new Intent(Intent.ACTION_SEND);
                send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            send.setType("image/jpeg");
            ClipData clip = ClipData.newRawUri("", uris.get(0));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
            send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, title);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(chooser);
            JSObject ret = new JSObject();
            ret.put("count", uris.size());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Share failed: " + e.getMessage(), e);
        }
    }

    // ---------- App version (shown in the app's status bar) ----------
    @PluginMethod
    public void getAppInfo(PluginCall call) {
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
            JSObject ret = new JSObject();
            ret.put("version", pi.versionName);
            ret.put("build", code);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("App info unavailable", e);
        }
    }

    private static byte[] decode(String b64) {
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) b64 = b64.substring(comma + 1);
        return Base64.decode(b64, Base64.DEFAULT);
    }
}
