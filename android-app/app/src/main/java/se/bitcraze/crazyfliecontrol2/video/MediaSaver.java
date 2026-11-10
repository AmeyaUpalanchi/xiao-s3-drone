package se.bitcraze.crazyfliecontrol2.video;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes photos and videos to the shared gallery (Pictures/ & Movies/MaxImagination). */
public final class MediaSaver {

    public static final String ALBUM = "MaxImagination";

    private MediaSaver() {}

    public static String timestampName(String prefix, String ext) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return prefix + "_" + ts + "." + ext;
    }

    /** Saves a JPEG and returns a human-readable location. */
    public static String savePhoto(Context ctx, byte[] jpeg) throws IOException {
        String name = timestampName("DRONE", "jpg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM);
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) {
                throw new IOException("MediaStore insert failed");
            }
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) {
                    throw new IOException("cannot open " + uri);
                }
                os.write(jpeg);
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            return "Pictures/" + ALBUM + "/" + name;
        } else {
            File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), ALBUM);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, name);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(jpeg);
            }
            MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
            return f.getAbsolutePath();
        }
    }

    /** A video destination opened for writing. */
    public static final class VideoTarget {
        public final String displayPath;
        final Uri uri;
        final File file;
        public final ParcelFileDescriptor pfd;

        VideoTarget(String displayPath, Uri uri, File file, ParcelFileDescriptor pfd) {
            this.displayPath = displayPath;
            this.uri = uri;
            this.file = file;
            this.pfd = pfd;
        }
    }

    public static VideoTarget createVideo(Context ctx) throws IOException {
        String name = timestampName("DRONE", "mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + ALBUM);
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) {
                throw new IOException("MediaStore insert failed");
            }
            ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "rw");
            if (pfd == null) {
                cr.delete(uri, null, null);
                throw new IOException("cannot open " + uri);
            }
            return new VideoTarget("Movies/" + ALBUM + "/" + name, uri, null, pfd);
        } else {
            File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), ALBUM);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, name);
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
            return new VideoTarget(f.getAbsolutePath(), null, f, pfd);
        }
    }

    public static void finishVideo(Context ctx, VideoTarget t, boolean keep) {
        try {
            t.pfd.close();
        } catch (IOException ignored) {
        }
        if (t.uri != null) {
            ContentResolver cr = ctx.getContentResolver();
            if (keep) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.IS_PENDING, 0);
                cr.update(t.uri, v, null, null);
            } else {
                cr.delete(t.uri, null, null);
            }
        } else if (t.file != null) {
            if (keep) {
                MediaScannerConnection.scanFile(ctx, new String[]{t.file.getAbsolutePath()}, new String[]{"video/mp4"}, null);
            } else {
                //noinspection ResultOfMethodCallIgnored
                t.file.delete();
            }
        }
    }
}
