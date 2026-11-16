package com.chichu.toolbox.acceptance;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * KernelSU root-request probe: every (re)start issues one real {@code su -c id} request and
 * reports the exit code plus transcript. Allowed requests show {@code uid=0(root)}; denied
 * requests surface the kernel's real failure, not a synthetic verdict.
 */
public class RootProbeActivity extends Activity {
    private static final String TAG = "ChichuAcceptance";
    private TextView text;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setPadding(32, 64, 32, 32);
        text.setText("Root 请求探针：每次打开（或 am 重新启动）发起一次真实 su 请求。");
        setContentView(text);
        runProbe();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        runProbe();
    }

    private void runProbe() {
        new Thread(() -> {
            StringBuilder transcript = new StringBuilder();
            int exit;
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = stdout.readLine()) != null) transcript.append(line).append('\n');
                while ((line = stderr.readLine()) != null) transcript.append("[stderr] ").append(line).append('\n');
                exit = process.waitFor();
            } catch (Exception error) {
                exit = -1;
                transcript.append("EXCEPTION ").append(error);
            }
            String body = transcript.toString().trim();
            Log.i(TAG, "ROOT_EXIT=" + exit + " ROOT_OUT=" + body.replace('\n', '|'));
            final int code = exit;
            runOnUiThread(() -> text.append("\n\nROOT_EXIT=" + code + "\n" + body));
        }).start();
    }
}
