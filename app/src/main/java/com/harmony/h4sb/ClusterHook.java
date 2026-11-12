package com.harmony.h4sb;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Honor 的 Wi-Fi / 信号不是 StatusBarIconView，而在 SignalClusterView：
 * - 信号格：SignalUnitNormalView.updateView(HwImageView, resId)
 * - Wi-Fi：SignalClusterView.setWifiIndicators(..., IconState, ...)
 * 这里按荣耀的资源名（stat_sys_signal_*, stat_sys_wifi2_*_signal_*）取等级替换。
 */
public final class ClusterHook {

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
    private static final java.util.concurrent.atomic.AtomicBoolean sFirstUnit =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean sFirstWifi =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final Map<View, Integer> LEVELS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.util.Set<String> ENTRIES =
            Collections.synchronizedSet(new java.util.HashSet<>());

    public ClusterHook(Context ctx) {
        this.ctx = ctx;
        this.res = ModuleRes.get(ctx);
    }

    public void install(ClassLoader cl) {
        Class<?> hwCluster = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.HwSignalClusterView", cl);
        if (hwCluster != null) {
            XposedBridge.hookAllMethods(hwCluster, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        StringBuilder sb = new StringBuilder();
                        walk((View) param.thisObject, sb, 0);
                        Diag.report(ctx, "tree", sb.toString());
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
        }

        Class<?> unit = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.SignalUnitNormalView", cl);
        if (unit != null) {
            hookUnit(unit);
            XposedBridge.log(ModuleEntry.TAG + ": SignalUnitNormalView hooked");
        }
        Class<?> uct = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.SignalUnitUCTView", cl);
        if (uct != null) {
            hookUnit(uct);
            XposedBridge.log(ModuleEntry.TAG + ": SignalUnitUCTView hooked");
        }
        Class<?> telecom = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.SignalUnitTelecomView", cl);
        if (telecom != null) {
            hookUnit(telecom);
        }

        Class<?> cluster = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.SignalClusterView", cl);
        if (cluster != null) {
            XposedBridge.hookAllMethods(cluster, "setWifiIndicators", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        onWifi(param);
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            try {
                Method dd = findInherited(cluster, "dispatchDraw", Canvas.class);
                if (dd != null) {
                    XposedBridge.hookMethod(dd, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                overlayWifi(param);
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            XposedBridge.log(ModuleEntry.TAG + ": SignalClusterView hooked");
        }
    }

    /** Wi-Fi：从 mWifiGroup 子 ImageView 的 Drawable 反查资源名，隐藏原图后自绘。 */
    private void overlayWifi(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof View) || param.args.length < 1
                || !(param.args[0] instanceof Canvas)) {
            return;
        }
        Object groupObj = field(param.thisObject, "mWifiGroup");
        if (!(groupObj instanceof ViewGroup) || res == null) {
            return;
        }
        ViewGroup group = (ViewGroup) groupObj;
        ImageView icon = findImage(group);
        if (icon == null || icon.getDrawable() == null) {
            return;
        }
        Integer id = IconHook.idOf(icon.getDrawable());
        if (id == null) {
            return;
        }
        String entry;
        try {
            entry = icon.getResources().getResourceEntryName(id);
        } catch (Throwable t) {
            return;
        }
        int level = level(entry, "wifi");
        if (level < 0) {
            return;
        }
        group.setAlpha(0f);
        Drawable d = res.get(WIFI[level]);
        if (d == null) {
            return;
        }
        d.setTint(0xFFFFFFFF);
        d.setBounds(group.getLeft(), group.getTop(), group.getRight(), group.getBottom());
        d.draw((Canvas) param.args[0]);
    }

    private void hookUnit(Class<?> unit) {
        XposedBridge.hookAllMethods(unit, "refreshView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    onRefresh(param);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });
        XposedBridge.hookAllMethods(unit, "updateView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    onUpdateView(param);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });
        try {
            Method dd = findInherited(unit, "dispatchDraw", Canvas.class);
            if (dd != null) {
                XposedBridge.hookMethod(dd, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            overlay(param);
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    /** 把系统信号图透明掉，在单位 View 的画布上按原位置画鸿蒙信号。 */
    private void overlay(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof View) || param.args.length < 1
                || !(param.args[0] instanceof Canvas)) {
            return;
        }
        View unit = (View) param.thisObject;
        Integer level = LEVELS.get(unit);
        if (level == null || res == null) {
            return;
        }
        Object ms = field(unit, "mMobileSignal");
        if (!(ms instanceof View)) {
            return;
        }
        View signal = (View) ms;
        if (signal.getWidth() <= 0) {
            return;
        }
        signal.setAlpha(0f);
        Drawable d = res.get(SIGNAL[level]);
        if (d == null) {
            return;
        }
        d.setTint(0xFFFFFFFF);
        d.setBounds(signal.getLeft(), signal.getTop(), signal.getRight(), signal.getBottom());
        d.draw((Canvas) param.args[0]);
    }

    private void onUpdateView(XC_MethodHook.MethodHookParam param) {
        if (param.args.length < 2 || !(param.args[0] instanceof ImageView)
                || !(param.args[1] instanceof Integer)) {
            return;
        }
        ImageView view = (ImageView) param.args[0];
        int resId = (Integer) param.args[1];
        String entry = entry(view, resId);
        int level = level(entry, "signal");
        if (level < 0) {
            return;
        }
        note(entry, level);
        if (param.thisObject instanceof View) {
            LEVELS.put((View) param.thisObject, level);
        }
        apply(view, SIGNAL[level], null);
    }

    private void note(String entry, int level) {
        if (entry != null && ENTRIES.add(entry) && ENTRIES.size() <= 8) {
            Diag.report(ctx, "unit", entry + " -> " + level);
        }
    }

    /** refreshView 之后，按 mMobileStrengthId 的资源名覆盖 mMobileSignal 的图。 */
    private void onRefresh(XC_MethodHook.MethodHookParam param) {
        Object obj = param.thisObject;
        Object viewObj = field(obj, "mMobileSignal");
        if (!(viewObj instanceof ImageView)) {
            return;
        }
        int resId = getInt(obj, "mMobileStrengthId");
        if (resId == 0) {
            return;
        }
        ImageView view = (ImageView) viewObj;
        String entry = entry(view, resId);
        if (entry != null && entry.toLowerCase().contains("null")) {
            return;
        }
        int level = level(entry, "signal");
        if (level >= 0) {
            note(entry, level);
        }
        if (level < 0) {
            return;
        }
        LEVELS.put((View) obj, level);
        apply(view, SIGNAL[level], null);
    }

    private void onWifi(XC_MethodHook.MethodHookParam param) {
        if (param.args.length < 3) {
            return;
        }
        Object state = param.args[1];
        Icon icon = iconOf(state);
        if (icon == null) {
            if (sFirstWifi.compareAndSet(false, true)) {
                Diag.report(ctx, "wifi", "state no icon: " + (state == null ? "null"
                        : state.getClass().getSimpleName()));
            }
            return;
        }
        String entry = entryOf(icon);
        int level = level(entry, "wifi");
        if (sFirstWifi.compareAndSet(false, true)) {
            Diag.report(ctx, "wifi", "entry=" + entry + " level=" + level);
        }
        if (level < 0) {
            return;
        }
        Object cluster = param.thisObject;
        Object group = field(cluster, "mWifiGroup");
        if (!(group instanceof ViewGroup)) {
            return;
        }
        ImageView view = findImage((ViewGroup) group);
        if (view == null) {
            return;
        }
        Integer tint = null;
        Object t = field(cluster, "mIconTint");
        if (t instanceof Integer) {
            tint = (Integer) t;
        }
        apply(view, WIFI[level], tint);
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

    private static String levelEntry(String entry, String keyword) {
        if (entry == null) {
            return null;
        }
        String e = entry.toLowerCase();
        return e.contains(keyword) ? e : null;
    }

    private static int level(String entry, String keyword) {
        String e = levelEntry(entry, keyword);
        if (e == null) {
            return -1;
        }
        for (int i = e.length() - 1; i >= 0; i--) {
            char c = e.charAt(i);
            if (c >= '0' && c <= '9') {
                int v = c - '0';
                if (e.contains("five")) {
                    // five_0..5 = 亮起 0..5 格，折算到鸿蒙的 0..4 格
                    return Math.min(4, Math.round(v * 4f / 5f));
                }
                return Math.min(v, 4);
            }
        }
        return -1;
    }

    private static String entry(View view, int resId) {
        try {
            return view.getResources().getResourceEntryName(resId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void walk(View v, StringBuilder sb, int depth) {
        if (sb.length() > 900 || depth > 8) {
            return;
        }
        String id = "";
        try {
            if (v.getId() != View.NO_ID) {
                id = v.getResources().getResourceEntryName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        sb.append(v.getClass().getSimpleName());
        if (!id.isEmpty()) {
            sb.append('#').append(id);
        }
        if (v.getVisibility() != View.VISIBLE) {
            sb.append('(').append(v.getVisibility()).append(')');
        }
        sb.append(' ');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                walk(g.getChildAt(i), sb, depth + 1);
            }
        }
    }

    private String entryOf(Icon icon) {
        try {
            int id = icon.getResId();
            String pkg = icon.getResPackage();
            if (id == 0 || pkg == null) {
                return null;
            }
            return ctx.getPackageManager().getResourcesForApplication(pkg)
                    .getResourceEntryName(id);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Icon iconOf(Object state) {
        Object v = field(state, "icon");
        if (v instanceof Icon) {
            return (Icon) v;
        }
        try {
            Object r = XposedHelpers.callMethod(state, "getIcon");
            return r instanceof Icon ? (Icon) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int getInt(Object obj, String name) {
        Object v = field(obj, name);
        return v instanceof Integer ? (Integer) v : 0;
    }

    private static ImageView findImage(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            if (c instanceof ImageView) {
                return (ImageView) c;
            }
            if (c instanceof ViewGroup) {
                ImageView r = findImage((ViewGroup) c);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static Method findInherited(Class<?> start, String name, Class<?>... params) {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
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
