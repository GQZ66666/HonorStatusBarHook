package com.harmony.h4sb;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.widget.ImageView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 把状态栏的 Wi-Fi / 移动信号换成鸿蒙 4 的矢量图标。
 * 挂 StatusBarIconView.set(StatusBarIcon)，读 slot + 资源名判断种类/等级。
 */
public final class IconHook {

    private static final String ICON_VIEW = "com.android.systemui.statusbar.StatusBarIconView";

    private static final int[] WIFI = {
            R.drawable.hm_wifi_0, R.drawable.hm_wifi_1, R.drawable.hm_wifi_2,
            R.drawable.hm_wifi_3, R.drawable.hm_wifi_4
    };
    private static final int[] SIGNAL = {
            R.drawable.hm_signal_0, R.drawable.hm_signal_1, R.drawable.hm_signal_2,
            R.drawable.hm_signal_3, R.drawable.hm_signal_4
    };
    private static final int[] SIGNAL_DATA = {
            R.drawable.hm_signal_0_data, R.drawable.hm_signal_1_data,
            R.drawable.hm_signal_2_data, R.drawable.hm_signal_3_data,
            R.drawable.hm_signal_4_data
    };

    private final Context ctx;
    private final ModuleRes res;
    private static final java.util.concurrent.atomic.AtomicBoolean sFirst =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public IconHook(Context ctx) {
        this.ctx = ctx;
        this.res = ModuleRes.get(ctx);
    }

    public void install(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClassIfExists(ICON_VIEW, cl);
        if (cls == null) {
            XposedBridge.log(ModuleEntry.TAG + ": StatusBarIconView not found");
            return;
        }
        XposedBridge.hookAllMethods(cls, "set", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    onSet(param);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });

        // Honor 的 Wi-Fi / 信号是 HwSignalClusterView 里的 ImageView，走 setImageResource
        XposedBridge.hookAllMethods(ImageView.class, "setImageResource", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    onImageResource(param);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });
        XposedBridge.log(ModuleEntry.TAG + ": StatusBarIconView + ImageView hooked");
    }

    private void onImageResource(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof ImageView) || param.args.length < 1) {
            return;
        }
        Object arg = param.args[0];
        if (!(arg instanceof Integer)) {
            return;
        }
        int resId = (Integer) arg;
        ImageView iv = (ImageView) param.thisObject;
        String entry = null;
        try {
            entry = iv.getResources().getResourceEntryName(resId);
        } catch (Throwable ignored) {
        }
        if (entry == null) {
            return;
        }
        String e = entry.toLowerCase();
        if (!e.contains("wifi") && !e.contains("signal")) {
            return;
        }
        int rid = map(null, entry);
        if (rid == 0 || res == null) {
            return;
        }
        Drawable d = res.get(rid);
        if (d == null) {
            return;
        }
        iv.setImageDrawable(d);
        ColorStateList tint = iv.getImageTintList();
        if (tint != null) {
            d.setTintList(tint);
        } else {
            d.setTint(0xFFFFFFFF);
        }
    }

    private void onSet(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof ImageView) || param.args.length < 1) {
            return;
        }
        Object info = param.args[0];
        Object iconObj = field(info, "icon");
        if (!(iconObj instanceof Icon)) {
            return;
        }
        Icon icon = (Icon) iconObj;
        int id = icon.getResId();
        String pkg = icon.getResPackage();
        if (id == 0 || pkg == null) {
            return;
        }
        String entry = null;
        try {
            entry = ctx.getPackageManager().getResourcesForApplication(pkg)
                    .getResourceEntryName(id);
        } catch (Throwable ignored) {
        }
        String slot = field(info, "slot") instanceof String ? (String) field(info, "slot") : null;
        if (sFirst.compareAndSet(false, true)) {
            Diag.report(ctx, "iconset", "slot=" + slot + " entry=" + entry);
        }
        int rid = map(slot, entry);
        if (rid == 0 || res == null) {
            return;
        }
        Drawable d = res.get(rid);
        if (d == null) {
            return;
        }
        ImageView iv = (ImageView) param.thisObject;
        iv.setImageDrawable(d);
        ColorStateList tint = iv.getImageTintList();
        if (tint != null) {
            d.setTintList(tint);
        } else {
            d.setTint(0xFFFFFFFF);
        }
    }

    private static int map(String slot, String entry) {
        String e = entry == null ? "" : entry.toLowerCase();
        String s = slot == null ? "" : slot.toLowerCase();
        boolean wifi = "wifi".equals(s) || "wlan".equals(s) || e.contains("wifi");
        boolean mobile = "mobile".equals(s) || "signal".equals(s) || e.contains("signal");
        int level = lastDigit(e);
        if (level < 0 || level > 4) {
            level = 4;
        }
        if (wifi) {
            return WIFI[level];
        }
        if (mobile) {
            return e.contains("fully") ? SIGNAL_DATA[level] : SIGNAL[level];
        }
        return 0;
    }

    private static int lastDigit(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
        }
        return -1;
    }

    private static Object field(Object obj, String name) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
