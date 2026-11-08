package com.chichu.toolbox.acceptance;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;

public class ProbeActivity extends Activity {
    private static final String TAG = "ChichuAcceptance";
    private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(this::probe);
    private final Shizuku.OnRequestPermissionResultListener result = (code, grant) -> report("REQUEST_RESULT=" + grant);
    private TextView text;
    private boolean ran;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setPadding(32, 64, 32, 32);
        text.setText("官方 SDK 兼容性验收；结果输出至 ChichuAcceptance 日志。");
        setContentView(text);
        Shizuku.addRequestPermissionResultListener(result);
        Shizuku.addBinderReceivedListenerSticky(received);
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ran = false;
        if (Shizuku.pingBinder()) probe();
    }

    private void report(String message) { Log.i(TAG, message); runOnUiThread(() -> text.append("\n" + message)); }

    private void probe() {
        if (ran) return;
        ran = true;
        new Thread(() -> {
            try {
                int permission = Shizuku.checkSelfPermission();
                report("BINDER=true PERMISSION=" + permission);
                checkManagerBlocked();
                if ("request".equals(getIntent().getStringExtra("mode"))) {
                    Shizuku.requestPermission(77);
                    report("REQUEST_SENT");
                } else if (permission == PackageManager.PERMISSION_GRANTED) {
                    moe.shizuku.server.IRemoteProcess process = moe.shizuku.server.IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(new String[]{"id"}, null, null);
                    String output = new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(process.getInputStream()))).readLine();
                    report("SHELL=" + output + " EXIT=" + process.waitFor());
                    bindUserService();
                } else {
                    try {
                        moe.shizuku.server.IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(new String[]{"id"}, null, null);
                        report("FAIL_UNAUTHORIZED_PROCESS_ALLOWED");
                    } catch (SecurityException expected) { report("UNAUTHORIZED_PROCESS_BLOCKED=true"); }
                }
            } catch (Throwable error) { Log.e(TAG, "PROBE_FAILED", error); }
        }, "sdk-acceptance").start();
    }

    private void checkManagerBlocked() throws Exception {
        for (int code : new int[]{10001, 10002, 10003, 10004}) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("moe.shizuku.server.IShizukuService");
                Shizuku.getBinder().transact(code, data, reply, 0);
                reply.readException();
                report("FAIL_MANAGER_TRANSACTION_ALLOWED=" + code);
            } catch (SecurityException expected) { report("MANAGER_BLOCKED=" + code);
            } finally { data.recycle(); reply.recycle(); }
        }
    }

    private void bindUserService() {
        boolean daemon = "daemon".equals(getIntent().getStringExtra("mode"));
        java.util.concurrent.atomic.AtomicBoolean connected = new java.util.concurrent.atomic.AtomicBoolean();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!connected.get()) report("FAIL_USER_SERVICE_TIMEOUT");
        }, 10_000);
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(this, ProbeUserService.class))
                .daemon(daemon).processNameSuffix("probe").debuggable(false).version(1);
        Shizuku.bindUserService(args, new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                connected.set(true);
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    binder.transact(1, data, reply, 0);
                    reply.readException();
                    report("USER_SERVICE_UID=" + reply.readInt());
                    if (!daemon) Shizuku.unbindUserService(args, this, true);
                } catch (Throwable error) { Log.e(TAG, "USER_SERVICE_FAILED", error);
                } finally { data.recycle(); reply.recycle(); }
            }
            @Override public void onServiceDisconnected(ComponentName name) { report("USER_SERVICE_DISCONNECTED"); }
        });
    }

    @Override public void onDestroy() {
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeRequestPermissionResultListener(result);
        super.onDestroy();
    }
}
