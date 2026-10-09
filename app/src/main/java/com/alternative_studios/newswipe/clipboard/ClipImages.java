package com.alternative_studios.newswipe.clipboard;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 클립보드 이미지를 앱 저장소로 옮긴다. 원본은 디코딩하지 않고 바이트 그대로 복사하고,
 * 기록 화면에 보일 미리보기(긴 변 {@link #THUMB_PX}px)를 따로 만든다.
 * 모두 백그라운드 스레드에서 부른다.
 */
public final class ClipImages {
    public static final int THUMB_PX = 256;

    private ClipImages() {
    }

    /**
     * 이미지를 복사하고 미리보기를 만든다. 성공하면 이미지 이름(내용 해시)을, 너무 크거나 읽을 수 없으면 null을 돌려준다.
     * 같은 이미지가 이미 있으면 다시 쓰지 않는다.
     */
    public static String save(ContentResolver resolver, Uri uri, File imageDir, File tmpDir) {
        if (!imageDir.isDirectory() && !imageDir.mkdirs()) return null;
        File tmp = new File(tmpDir, "clip_image.tmp");
        String id;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            id = copy(in, tmp);
        } catch (IOException | RuntimeException e) {
            id = null;
        }
        if (id == null) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        File original = ClipboardHistory.original(imageDir, id);
        if (original.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        } else if (!tmp.renameTo(original)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        File thumb = ClipboardHistory.thumbnail(imageDir, id);
        if (!thumb.exists() && !makeThumbnail(original, thumb)) {
            // 이미지로 읽을 수 없는 파일은 두지 않는다.
            //noinspection ResultOfMethodCallIgnored
            original.delete();
            return null;
        }
        return id;
    }

    /** 바이트 그대로 복사하며 해시를 구한다. 너무 크면 null. */
    private static String copy(InputStream in, File dest) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        long total = 0;
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (total > ClipboardHistory.MAX_IMAGE_BYTES) return null;
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        }
        if (total == 0) return null;
        byte[] hash = digest.digest();
        StringBuilder b = new StringBuilder(32);
        for (int i = 0; i < 16; i++) b.append(String.format("%02x", hash[i] & 0xff));
        return b.toString();
    }

    /** 긴 변이 THUMB_PX를 넘지 않는 미리보기를 만든다. 큰 이미지도 줄여 읽어 메모리를 적게 쓴다. */
    private static boolean makeThumbnail(File original, File thumb) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(original.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return false;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(original.getPath(), o);
        if (bitmap == null) return false;
        Bitmap scaled = scale(bitmap, rotation(original));
        if (scaled != bitmap) bitmap.recycle();
        File tmp = new File(thumb.getPath() + ".tmp");
        boolean ok;
        try (OutputStream out = new FileOutputStream(tmp)) {
            ok = scaled.hasAlpha()
                    ? scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    : scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (IOException e) {
            ok = false;
        } finally {
            scaled.recycle();
        }
        if (ok && tmp.renameTo(thumb)) return true;
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return false;
    }

    /** 이미지 정보(EXIF)의 회전을 적용하고 긴 변을 THUMB_PX로 맞춘다. 바꿀 것이 없으면 그대로 돌려준다. */
    private static Bitmap scale(Bitmap b, int degrees) {
        float s = Math.min(1f, (float) THUMB_PX / Math.max(b.getWidth(), b.getHeight()));
        if (s == 1f && degrees == 0) return b;
        Matrix m = new Matrix();
        m.postScale(s, s);
        if (degrees != 0) m.postRotate(degrees);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    private static int rotation(File f) {
        try {
            switch (new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;   // 이미지 정보가 없는 형식
        }
    }
}
