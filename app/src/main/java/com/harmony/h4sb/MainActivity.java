package com.harmony.h4sb;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btnRestart).setOnClickListener(v -> restart());
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
