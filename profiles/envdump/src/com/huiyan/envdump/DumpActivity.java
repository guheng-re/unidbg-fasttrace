package com.huiyan.envdump;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public class DumpActivity extends Activity {
    private TextView log;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        btn = new Button(this);
        btn.setText("开始采集");
        log = new TextView(this);
        log.setTextSize(16f);
        log.setText("会尽量按 Pixel 6 画像字段采集；读不到的会跳过。\n完成后 pull envdump.zip");
        ScrollView sv = new ScrollView(this);
        sv.addView(log);
        root.addView(btn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = 24;
        root.addView(sv, lp);
        setContentView(root);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAndRun();
            }
        });
        requestAndRun();
    }

    private void requestAndRun() {
        String[] perms = new String[] {
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.GET_ACCOUNTS
        };
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        for (int i = 0; i < perms.length; i++) {
            if (checkSelfPermission(perms[i]) != PackageManager.PERMISSION_GRANTED) {
                need.add(perms[i]);
            }
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
            return;
        }
        startDump();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        startDump();
    }

    private void startDump() {
        btn.setEnabled(false);
        log.setText("采集中…");
        final File out = new File(getExternalFilesDir(null), "out");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Collector c = new Collector(DumpActivity.this, out);
                    final File zip = c.run(new Collector.Progress() {
                        @Override
                        public void on(final String msg) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    log.setText("采集中：" + msg);
                                }
                            });
                        }
                    });
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btn.setEnabled(true);
                            log.setText("完成\n" + zip.getAbsolutePath()
                                    + "\n" + zip.length() + " bytes\n\n"
                                    + "adb pull \"" + zip.getAbsolutePath() + "\"");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btn.setEnabled(true);
                            log.setText("失败 " + e);
                        }
                    });
                }
            }
        }, "envdump").start();
    }
}
