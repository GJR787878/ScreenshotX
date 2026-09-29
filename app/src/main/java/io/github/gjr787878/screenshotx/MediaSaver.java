package io.github.gjr787878.screenshotx;

import android.content.ContentValues;
import android.content.Context;
import android.os.Environment;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** 把截图文件直接写入系统相册（MediaStore，Pictures/Screenshots），无需存储权限、不二次压缩。 */
public class MediaSaver {

    public static void saveToGallery(Context ctx, String path) throws Exception {
        File src = new File(path);
        String name = "ScreenshotX_" + System.currentTimeMillis() + ".png";
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Screenshots");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = ctx.getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new RuntimeException("MediaStore insert failed");
        try (FileInputStream in = new FileInputStream(src);
             OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new RuntimeException("openOutputStream null");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        ctx.getContentResolver().update(uri, v, null, null);
        }

    /** 把录屏 mp4 写入系统相册（Movies/Screenshots）。 */
    public static void saveVideo(Context ctx, String path) throws Exception {
        File src = new File(path);
        String name = "ScreenshotX_" + System.currentTimeMillis() + ".mp4";
        ContentValues v = new ContentValues();
        v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/Screenshots");
        v.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = ctx.getContentResolver()
                .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new RuntimeException("MediaStore insert failed");
        try (FileInputStream in = new FileInputStream(src);
             OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new RuntimeException("openOutputStream null");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        v.clear();
        v.put(MediaStore.Video.Media.IS_PENDING, 0);
        ctx.getContentResolver().update(uri, v, null, null);
    }
}