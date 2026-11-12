package com.harmony.h4sb;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/** 系统界面里的 hook 把状态回传给模块 App，设置页直接显示。 */
public final class Diag {

    public static final String AUTHORITY = "com.harmony.h4sb.diag";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/diag");

    private Diag() {
    }

    public static void report(Context ctx, String key, String value) {
        if (ctx == null) {
            return;
        }
        try {
            Bundle b = new Bundle();
            b.putString(key, value);
            ctx.getContentResolver().call(URI, "report", null, b);
        } catch (Throwable ignored) {
        }
    }
}
