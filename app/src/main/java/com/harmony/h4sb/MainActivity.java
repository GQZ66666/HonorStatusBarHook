package com.harmony.h4sb;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private TextView tvDiag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvDiag = findViewById(R.id.tvDiag);
        findViewById(R.id.btnRestart).setOnClickListener(v -> restart());
        findViewById(R.id.btnDiag).setOnClickListener(v -> refreshDiag());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiag();
    }

    private void refreshDiag() {
        StringBuilder sb = new StringBuilder();
        try {
            Bundle b = getContentResolver().call(Diag.URI, "diag", null, null);
            if (b != null) {
                append(sb, b, "hooks", "注入");
                append(sb, b, "iconset", "StatusBarIconView");
                append(sb, b, "unit", "信号格");
                append(sb, b, "wifi", "Wi-Fi");
                append(sb, b, "battery", "电池");
            }
        } catch (Throwable ignored) {
        }
        tvDiag.setText(sb.length() == 0 ? getString(R.string.diag_empty) : sb.toString().trim());
    }

    private static void append(StringBuilder sb, Bundle b, String key, String label) {
        String v = b.getString(key);
        if (v != null) {
            sb.append(label).append(": ").append(v).append('\n');
        }
    }

    private void restart() {
        Toast.makeText(this, "正在重启系统界面…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String msg;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "pkill -f '^com.android.systemui$' || killall com.android.systemui"});
                msg = p.waitFor() == 0 ? "已重启系统界面" : "重启失败，请手动重启系统界面";
            } catch (Throwable t) {
                msg = "重启失败：" + t;
            }
            final String m = msg;
            runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        }, "h4-restart").start();
    }
}
