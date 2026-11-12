package com.harmony.h4sb;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DiagProvider extends ContentProvider {

    private static final Map<String, String> DATA = new LinkedHashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("report".equals(method) && extras != null) {
            synchronized (DATA) {
                for (String key : extras.keySet()) {
                    Object v = extras.get(key);
                    if (v != null) {
                        DATA.put(key, String.valueOf(v));
                    }
                }
            }
            return null;
        }
        if ("diag".equals(method)) {
            Bundle b = new Bundle();
            synchronized (DATA) {
                for (Map.Entry<String, String> e : DATA.entrySet()) {
                    b.putString(e.getKey(), e.getValue());
                }
            }
            return b;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
