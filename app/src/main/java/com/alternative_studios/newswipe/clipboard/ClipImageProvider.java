package com.alternative_studios.newswipe.clipboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * 클립보드 기록의 이미지를 붙여 넣을 앱에 읽기 전용으로 건넨다.
 * 외부에 공개하지 않고, 붙여 넣는 순간 받는 앱에만 임시 읽기 권한을 준다.
 * 주소 형식: content://(패키지).clipimages/(이미지 이름)/(형식)
 */
public final class ClipImageProvider extends ContentProvider {

    public static String authority(Context context) {
        return context.getPackageName() + ".clipimages";
    }

    public static Uri uri(Context context, String id, String mime) {
        return new Uri.Builder().scheme("content").authority(authority(context))
                .appendPath(id).appendPath(mime).build();
    }

    /** 이 앱이 만든 주소면 이미지 이름, 아니면 null. */
    public static String idOf(Context context, Uri uri) {
        if (uri == null || !authority(context).equals(uri.getAuthority())) return null;
        List<String> seg = uri.getPathSegments();
        return seg.size() == 2 && ClipboardHistory.isImageId(seg.get(0)) ? seg.get(0) : null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File file(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        String id = context == null ? null : idOf(context, uri);
        if (id == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = ClipboardHistory.original(ClipboardHistory.imageDir(context.getFilesDir()), id);
        if (!f.isFile()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public String getType(Uri uri) {
        List<String> seg = uri.getPathSegments();
        return seg.size() == 2 ? ClipboardHistory.cleanMime(seg.get(1)) : null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("읽기 전용");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = file(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String mime = getType(uri);
        String ext = mime == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        String name = "clipboard_" + f.getName().substring(0, 8) + (ext == null ? "" : "." + ext);
        if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) row[i] = name;
            else if (OpenableColumns.SIZE.equals(projection[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
