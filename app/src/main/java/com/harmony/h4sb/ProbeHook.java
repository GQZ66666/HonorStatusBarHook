package com.harmony.h4sb;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 广撒网探针：把信号/Wi-Fi 相关候选类的方法全部挂上，只记录“谁被调用 + 参数”，
 * 不做替换，避免把系统界面搞崩。诊断页按调用顺序显示。
 */
public final class ProbeHook {

    private static final String[] CLASSES = {
            "com.android.systemui.statusbar.SignalClusterView",
            "com.android.systemui.statusbar.HwSignalClusterView",
            "com.android.systemui.statusbar.SignalUnitNormalView",
            "com.android.systemui.statusbar.SignalUnitUCTView",
            "com.android.systemui.statusbar.SignalUnitTelecomView",
            "com.android.systemui.statusbar.HwCustSignalClusterViewImpl",
            "com.android.systemui.statusbar.HwCustMSimSignalClusterViewImpl",
            "com.android.systemui.statusbar.HwCustSignalUnitNormalViewImpl",
            "com.android.systemui.statusbar.HwCustSignalUnitUCTViewImpl",
            "com.android.systemui.statusbar.HwSystemIcons",
            "com.android.systemui.statusbar.HnNetWorkIconsMore",
            "com.android.systemui.networkspeed.NetworkSpeedView",
            "com.android.systemui.statusbar.pipeline.wifi.ui.view.WifiView",
            "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernMobileView",
            "com.android.systemui.statusbar.pipeline.mobile.ui.view.MobileSignalIconView",
            "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernSignalIconView"
    };

    private static final Pattern NAME = Pattern.compile(
            "(?i)^(set|update|apply|refresh|bind|onWifi|onMobile|onSignal).*");
    private static final Pattern SKIP = Pattern.compile(
            "(?i).*(draw|layout|measure|attached|detached|save|restore|dispatch).*");

    private static final Set<String> SEEN = Collections.synchronizedSet(new HashSet<>());
    private static final AtomicInteger COUNT = new AtomicInteger();

    private final Context ctx;

    public ProbeHook(Context ctx) {
        this.ctx = ctx;
    }

    public void install(ClassLoader cl) {
        for (String name : CLASSES) {
            Class<?> c = XposedHelpers.findClassIfExists(name, cl);
            if (c == null) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterTypes().length > 6 || !NAME.matcher(m.getName()).matches()
                        || SKIP.matcher(m.getName()).matches()) {
                    continue;
                }
                final String key = c.getSimpleName() + "." + m.getName();
                if (!SEEN.add(key)) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(m, probe(key));
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
            try {
                Constructor<?> ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                XposedBridge.hookMethod(ctor, probe(c.getSimpleName() + ".<init>"));
            } catch (Throwable ignored) {
            }
        }
        hookImage();
        XposedBridge.log(ModuleEntry.TAG + ": probe installed, keys=" + SEEN.size());
    }

    private XC_MethodHook probe(final String key) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (COUNT.incrementAndGet() > 400) {
                        return;
                    }
                    Diag.report(ctx, key, args(param));
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        };
    }

    private String args(XC_MethodHook.MethodHookParam param) {
        StringBuilder sb = new StringBuilder();
        Object[] a = param.args;
        for (int i = 0; i < a.length && i < 3; i++) {
            Object o = a[i];
            if (i > 0) {
                sb.append(',');
            }
            if (o == null) {
                sb.append("null");
            } else if (o instanceof Integer || o instanceof Boolean) {
                sb.append(o);
            } else if (o instanceof String) {
                String s = (String) o;
                sb.append(s.length() > 24 ? s.substring(0, 24) : s);
            } else if (o instanceof Icon) {
                sb.append(iconEntry((Icon) o));
            } else {
                sb.append(o.getClass().getSimpleName());
            }
        }
        return sb.toString();
    }

    private String iconEntry(Icon icon) {
        try {
            int id = icon.getResId();
            String pkg = icon.getResPackage();
            if (id != 0 && pkg != null) {
                return ctx.getPackageManager().getResourcesForApplication(pkg)
                        .getResourceEntryName(id);
            }
        } catch (Throwable ignored) {
        }
        return "icon";
    }

    private void hookImage() {
        XposedBridge.hookAllMethods(ImageView.class, "setImageResource", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length < 1 || !(param.thisObject instanceof ImageView)) {
                        return;
                    }
                    ImageView v = (ImageView) param.thisObject;
                    String entry = entry(v, (Integer) param.args[0]);
                    String id = id(v);
                    if (match(entry, id, v.getClass().getSimpleName())) {
                        Diag.report(ctx, "img.res", v.getClass().getSimpleName() + "#" + id
                                + " -> " + entry);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        XposedBridge.hookAllMethods(ImageView.class, "setImageIcon", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length < 1 || !(param.thisObject instanceof ImageView)
                            || !(param.args[0] instanceof Icon)) {
                        return;
                    }
                    ImageView v = (ImageView) param.thisObject;
                    Icon icon = (Icon) param.args[0];
                    String entry = iconEntry(icon);
                    String id = id(v);
                    if (match(entry, id, v.getClass().getSimpleName())) {
                        Diag.report(ctx, "img.icon", v.getClass().getSimpleName() + "#" + id
                                + " -> " + entry);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        XposedBridge.hookAllMethods(ImageView.class, "setImageDrawable", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.thisObject instanceof ImageView)) {
                        return;
                    }
                    ImageView v = (ImageView) param.thisObject;
                    String id = id(v);
                    if (match(null, id, v.getClass().getSimpleName())) {
                        Diag.report(ctx, "img.drawable", v.getClass().getSimpleName() + "#" + id);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static boolean match(String entry, String id, String cls) {
        String s = ((entry == null ? "" : entry) + " " + (id == null ? "" : id) + " " + cls)
                .toLowerCase();
        return s.contains("wifi") || s.contains("signal") || s.contains("mobile");
    }

    private static String entry(View v, int resId) {
        try {
            return v.getResources().getResourceEntryName(resId);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String id(View v) {
        try {
            return v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "";
        }
    }
}
