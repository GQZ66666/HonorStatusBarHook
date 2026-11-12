package com.harmony.h4sb;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiagProvider extends ContentProvider {

    private static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX = 400;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("report".equals(method) && extras != null) {
            synchronized (LOG) {
                for (String key : extras.keySet()) {
                    Object v = extras.get(key);
                    if (v != null) {
                        LOG.add(key + "=" + v);
                    }
                }
                while (LOG.size() > MAX) {
                    LOG.remove(0);
                }
            }
            return null;
        }
        if ("diag".equals(method)) {
            StringBuilder sb = new StringBuilder();
            synchronized (LOG) {
                for (String line : LOG) {
                    sb.append(line).append('\n');
                }
            }
            Bundle b = new Bundle();
            b.putString("log", sb.toString());
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
