package com.harmony.h4sb;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/** 加载模块 APK 里的矢量资源（鸿蒙 4 提取出的 VectorDrawable）。 */
public final class ModuleRes {

    public static final String PKG = "com.harmony.h4sb";
    private static ModuleRes sInstance;

    private final Resources res;

    private ModuleRes(Resources r) {
        this.res = r;
    }

    public static synchronized ModuleRes get(Context host) {
        if (sInstance != null) {
            return sInstance;
        }
        if (PKG.equals(host.getPackageName())) {
            sInstance = new ModuleRes(host.getResources());
            return sInstance;
        }
        try {
            Context c = host.createPackageContext(PKG, Context.CONTEXT_IGNORE_SECURITY);
            sInstance = new ModuleRes(c.getResources());
            return sInstance;
        } catch (Throwable t) {
            XposedBridge.log(ModuleEntry.TAG + ": createPackageContext failed: " + t);
        }
        try {
            String apk = host.getPackageManager().getApplicationInfo(PKG, 0).sourceDir;
            AssetManager am = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            addAssetPath.invoke(am, apk);
            Resources hostRes = host.getResources();
            sInstance = new ModuleRes(new Resources(am,
                    hostRes.getDisplayMetrics(), hostRes.getConfiguration()));
            return sInstance;
        } catch (Throwable t) {
            XposedBridge.log(ModuleEntry.TAG + ": addAssetPath failed: " + t);
            return null;
        }
    }

    public Drawable get(int id) {
        if (res == null) {
            return null;
        }
        try {
            return res.getDrawable(id, null);
        } catch (Throwable t) {
            return null;
        }
    }
}
