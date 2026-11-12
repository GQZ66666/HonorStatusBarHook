package com.harmony.h4sb;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * MagicOS 11 真正绑定状态栏图标的两条现代管线（与参考模块一致）：
 *   IconViewBinder.bind(Icon, ImageView)              -> Wi-Fi / 图标
 *   SignalIconBinder.bind(..., MobileDateModel, ...)  -> 信号格 strengthIcon
 * 图标资源 id 分别为 Icon$Resource.res 与 MobileDateModel.strengthIcon。
 */
public final class BinderHook {

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
    private static final java.util.concurrent.atomic.AtomicBoolean sFirstWifi =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean sFirstSignal =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public BinderHook(Context ctx) {
        this.ctx = ctx;
        this.res = ModuleRes.get(ctx);
    }

    public void install(ClassLoader cl) {
        Class<?> iconBinder = XposedHelpers.findClassIfExists(
                "com.android.systemui.common.ui.binder.IconViewBinder", cl);
        if (iconBinder != null) {
            hookStatic(iconBinder, "bind", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        onIconBind(param);
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            XposedBridge.log(ModuleEntry.TAG + ": IconViewBinder hooked");
        }

        Class<?> signalBinder = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.pipeline.mobile.ui.binder.SignalIconBinder", cl);
        if (signalBinder != null) {
            hookStatic(signalBinder, "bind", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        onSignalBind(param);
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            XposedBridge.log(ModuleEntry.TAG + ": SignalIconBinder hooked");
        }
    }

    private static void hookStatic(Class<?> c, String name, XC_MethodHook hook) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                XposedBridge.hookMethod(m, hook);
            }
        }
    }

    private void onIconBind(XC_MethodHook.MethodHookParam param) {
        if (param.args.length < 2 || !(param.args[1] instanceof ImageView)) {
            return;
        }
        Object icon = param.args[0];
        if (icon == null) {
            return;
        }
        int resId = getInt(icon, "res");
        if (resId == 0) {
            return;
        }
        ImageView view = (ImageView) param.args[1];
        String entry = entry(view, resId);
        int level = level(entry, "wifi");
        if (level < 0) {
            level = level(entry, "signal");
        }
        if (level < 0) {
            return;
        }
        if (sFirstWifi.compareAndSet(false, true)) {
            Diag.report(ctx, "binder", "icon " + entry + " -> " + level);
        }
        apply(view, entry.contains("wifi") ? WIFI[level] : SIGNAL[level], null);
    }

    private void onSignalBind(XC_MethodHook.MethodHookParam param) {
        if (param.args.length < 3 || !(param.args[1] instanceof View)) {
            return;
        }
        Object model = param.args[2];
        int resId = getInt(model, "strengthIcon");
        if (resId == 0) {
            return;
        }
        View root = (View) param.args[1];
        ImageView view = findImage(root);
        if (view == null) {
            return;
        }
        String entry = entry(view, resId);
        int level = level(entry, "signal");
        if (level < 0) {
            return;
        }
        if (sFirstSignal.compareAndSet(false, true)) {
            Diag.report(ctx, "binder", "signal " + entry + " -> " + level);
        }
        apply(view, SIGNAL[level], null);
    }

    private void apply(ImageView view, int rid, Integer tint) {
        if (res == null) {
            return;
        }
        Drawable d = res.get(rid);
        if (d == null) {
            return;
        }
        view.setImageDrawable(d);
        ColorStateList list = view.getImageTintList();
        if (list != null) {
            d.setTintList(list);
        } else if (tint != null) {
            d.setTint(tint | 0xFF000000);
        } else {
            d.setTint(0xFFFFFFFF);
        }
    }

    private static ImageView findImage(View v) {
        if (v instanceof ImageView) {
            return (ImageView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ImageView r = findImage(g.getChildAt(i));
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static String entry(View v, int resId) {
        try {
            return v.getResources().getResourceEntryName(resId);
        } catch (Throwable t) {
            return "";
        }
    }

    private static int level(String entry, String keyword) {
        if (entry == null) {
            return -1;
        }
        String e = entry.toLowerCase();
        if (!e.contains(keyword)) {
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

    private static int getInt(Object obj, String name) {
        Object v = field(obj, name);
        return v instanceof Integer ? (Integer) v : 0;
    }

    private static Object field(Object obj, String name) {
        if (obj == null) {
            return null;
        }
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
