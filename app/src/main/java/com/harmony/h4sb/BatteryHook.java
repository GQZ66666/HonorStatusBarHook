package com.harmony.h4sb;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 用鸿蒙 4 的电池几何（115×64）重画状态栏电池：
 * 外框直接用提取出的矢量，填充按电量，充电画闪电，百分比用固定数字基准居中。
 */
public final class BatteryHook {

    private static final String BATTERY = "com.android.systemui.battery.HwStatusBarBatteryView";
    private static final int VIEW_W = 115;
    private static final int VIEW_H = 64;

    private final Context ctx;
    private final ModuleRes res;
    private static final java.util.concurrent.atomic.AtomicBoolean sFirst =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public BatteryHook(Context ctx) {
        this.ctx = ctx;
        this.res = ModuleRes.get(ctx);
    }

    public void install(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(BATTERY, cl);
        if (c == null) {
            XposedBridge.log(ModuleEntry.TAG + ": HwStatusBarBatteryView not found");
            return;
        }
        Method m = findInherited(c, "dispatchDraw", Canvas.class);
        if (m == null) {
            XposedBridge.log(ModuleEntry.TAG + ": dispatchDraw not found");
            return;
        }
        XposedBridge.hookMethod(m, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!c.isInstance(param.thisObject)) {
                    return;
                }
                try {
                    draw(param, (View) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });
        XposedBridge.log(ModuleEntry.TAG + ": battery hooked");
    }

    private void draw(XC_MethodHook.MethodHookParam param, View view) {
        Canvas canvas = param.args.length > 0 && param.args[0] instanceof Canvas
                ? (Canvas) param.args[0] : null;
        if (canvas == null || res == null) {
            return;
        }
        Object obj = param.thisObject;
        int level = getInt(obj, "mLevel");
        if (level < 0 || level > 100) {
            return;
        }
        if (!getBool(obj, "mIsShowPercentIn")) {
            return;
        }
        boolean charging = getBool(obj, "mIsCharging");
        boolean show = getBool(obj, "mIsShowPercent");
        int tint = currentTint(obj);
        if (tint == 0) {
            tint = 0xFFFFFFFF;
        }
        if (sFirst.compareAndSet(false, true)) {
            Diag.report(ctx, "battery", "level=" + level + " tint=#" + Integer.toHexString(tint));
        }
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        param.setResult(null);

        float s = Math.min(w / (float) VIEW_W, h / (float) VIEW_H);
        float ox = (w - VIEW_W * s) / 2f;
        float oy = (h - VIEW_H * s) / 2f;

        RectF body = new RectF(ox + 6 * s, oy + 6 * s, ox + 99 * s, oy + 58 * s);
        int fill = charging ? 0xFF34C759 : (level <= 20 ? 0xFFFF9F0A : (tint | 0xFF000000));

        float inset = 4 * s;
        float fillW = (body.width() - 2 * inset) * level / 100f;
        if (fillW > 0.5f) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(fill);
            float fr = Math.min((body.height() - 2 * inset) / 2f, Math.max(1f, fillW / 2f));
            canvas.drawRoundRect(new RectF(body.left + inset, body.top + inset,
                    body.left + inset + fillW, body.bottom - inset), fr, fr, p);
        }

        Drawable outline = res.get(R.drawable.hm_battery_outline);
        if (outline != null) {
            outline.setTint(tint);
            outline.setBounds(Math.round(ox), Math.round(oy),
                    Math.round(ox + VIEW_W * s), Math.round(oy + VIEW_H * s));
            outline.draw(canvas);
        }

        if (charging) {
            Drawable bolt = res.get(R.drawable.hm_battery_charge);
            if (bolt != null) {
                bolt.setTint(0xFFFFFFFF);
                bolt.setBounds(Math.round(ox), Math.round(oy),
                        Math.round(ox + VIEW_W * s), Math.round(oy + VIEW_H * s));
                bolt.draw(canvas);
            }
        }

        if (show) {
            drawPercent(canvas, level, body.centerX(), body.centerY(),
                    Math.max(6f, body.height() * 0.60f), textColor(fill), s);
        }
    }

    private static int textColor(int fill) {
        int r = (fill >> 16) & 0xFF;
        int g = (fill >> 8) & 0xFF;
        int b = fill & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 160 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static void drawPercent(Canvas c, int level, float cx, float cy,
                                    float size, int color, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        String text = String.valueOf(level);
        String ref = "0123456789";
        Rect r = new Rect();
        p.getTextBounds(ref, 0, ref.length(), r);
        c.drawText(text, cx, cy - (r.top + r.bottom) / 2f, p);
    }

    /** 优先取百分比文字当前颜色（会随深浅色状态栏变化），退回 mTintColor。 */
    private static int currentTint(Object obj) {
        Object pv = field(obj, "mInsidePercentView");
        if (pv instanceof android.widget.TextView) {
            int c = ((android.widget.TextView) pv).getCurrentTextColor();
            if (c != 0) {
                return c | 0xFF000000;
            }
        }
        int c = getInt(obj, "mTintColor");
        return c == 0 ? 0 : (c | 0xFF000000);
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

    private static int getInt(Object obj, String name) {
        Object v = field(obj, name);
        return v instanceof Integer ? (Integer) v : 0;
    }

    private static boolean getBool(Object obj, String name) {
        Object v = field(obj, name);
        return v instanceof Boolean && (Boolean) v;
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
