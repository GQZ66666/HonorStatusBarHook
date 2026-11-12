package com.harmony.h4sb;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ModuleEntry implements IXposedHookLoadPackage {

    public static final String TAG = "Harmony4SB";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        final String pkg = lp.packageName;
        if (!"com.android.systemui".equals(pkg) && !"com.hihonor.systemui".equals(pkg)) {
            return;
        }
        XposedBridge.log(TAG + ": loading into " + pkg);
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context ctx = (Context) param.args[0];
                        if (ctx == null || !pkg.equals(ctx.getPackageName())) {
                            return;
                        }
                        try {
                            new IconHook(ctx).install(lp.classLoader);
                            new ResHook(ctx).install(lp.classLoader);
                            new BatteryHook(ctx).install(lp.classLoader);
                            Diag.report(ctx, "hooks", "icon+cluster+battery+probe installed");
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                });
    }
}
