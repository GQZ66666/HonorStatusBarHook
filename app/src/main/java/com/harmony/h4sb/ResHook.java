package com.harmony.h4sb;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 参考模块「U7 图标替换」的实际做法：不碰任何 View/binder，
 * 直接 hook Resources.getDrawable / getDrawableForDensity，
 * 按资源名把荣耀的 stat_sys_wifi_* 和 stat_sys_signal_* 换成鸿蒙矢量。
 *
 * 这样无论图标走 StatusBarIconView、SignalIconBinder、IconViewBinder
 * 还是老的 SignalClusterView，最终拿到的都是替换后的 Drawable。
 */
public final class ResHook {

    private static final int[] WIFI = {
            R.drawable.hm_wifi_0, R.drawable.hm_wifi_1, R.drawable.hm_wifi_2,
            R.drawable.hm_wifi_3, R.drawable.hm_wifi_4
    };
    private static final int[] SIGNAL = {
            R.drawable.hm_signal_0, R.drawable.hm_signal_1, R.drawable.hm_signal_2,
            R.drawable.hm_signal_3, R.drawable.hm_signal_4
    };

    private final Context ctx;
    private final ModuleRes res;
    private static final ThreadLocal<Boolean> IN_REPLACE = new ThreadLocal<>();
    private static final AtomicInteger HITS = new AtomicInteger();

    public ResHook(Context ctx) {
        this.ctx = ctx;
        this.res = ModuleRes.get(ctx);
    }

    public void install(ClassLoader cl) {
        XposedBridge.hookAllMethods(Resources.class, "getDrawable", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                replace(param);
            }
        });
        XposedBridge.hookAllMethods(Resources.class, "getDrawableForDensity", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                replace(param);
            }
        });
        XposedBridge.log(ModuleEntry.TAG + ": Resources.getDrawable hooked");
    }

    private void replace(XC_MethodHook.MethodHookParam param) {
        try {
            if (Boolean.TRUE.equals(IN_REPLACE.get()) || res == null) {
                return;
            }
            if (!(param.thisObject instanceof Resources) || param.args.length < 1
                    || !(param.args[0] instanceof Integer)) {
                return;
            }
            if (!(param.getResult() instanceof Drawable)) {
                return;
            }
            Resources resources = (Resources) param.thisObject;
            int resId = (Integer) param.args[0];
            String entry;
            try {
                entry = resources.getResourceEntryName(resId);
            } catch (Throwable t) {
                return;
            }
            if (entry == null || entry.startsWith("hm_")) {
                return;
            }
            int rid = map(entry);
            if (rid == 0) {
                return;
            }
            Drawable d;
            IN_REPLACE.set(Boolean.TRUE);
            try {
                d = res.get(rid);
            } finally {
                IN_REPLACE.set(Boolean.FALSE);
            }
            if (d == null) {
                return;
            }
            param.setResult(d);
            if (HITS.incrementAndGet() <= 6) {
                Diag.report(ctx, "res", entry + " -> hm");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static int map(String entry) {
        String e = entry.toLowerCase();
        int level = level(e, "wifi");
        if (level >= 0) {
            return WIFI[level];
        }
        level = level(e, "signal");
        if (level >= 0) {
            return SIGNAL[level];
        }
        return 0;
    }

    private static int level(String e, String keyword) {
        if (!e.contains(keyword) || e.contains("null") || e.contains("none")) {
            return -1;
        }
        for (int i = e.length() - 1; i >= 0; i--) {
            char c = e.charAt(i);
            if (c >= '0' && c <= '9') {
                int v = c - '0';
                if (e.contains("five")) {
                    return Math.min(4, Math.round(v * 4f / 5f));
                }
                return Math.min(v, 4);
            }
        }
        return -1;
    }
}
