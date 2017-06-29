package com.job.dollar.internetrestrict;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by SMARTTECHX on 11/17/2016.
 */

public class VPNInitService extends VpnService implements Runnable {
    private static final String TAG = "Internet.vpnservice";

    private Thread thread = null;
    public static final String EXTRA_COMMAND = "Command";

    public enum Command {start, reload, stop}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("enabled", false);

        Command cmd = (intent == null ? Command.start : (Command) intent.getSerializableExtra(EXTRA_COMMAND));
        Log.i(TAG, "Start intent=" + intent + " command=" + cmd + " enabled=" + enabled + " running=" + (thread != null));

        if (cmd == Command.reload || cmd == Command.stop) {
            if (thread != null) {
                Log.i(TAG, "Stopping thread=" + thread);
                thread.interrupt();
            }
            if (cmd == Command.stop)
                stopSelf();
        }

        if (cmd == Command.start || cmd == Command.reload) {
            if (enabled && (thread == null || thread.isInterrupted())) {
                Log.i(TAG, "Starting");
                thread = new Thread(this, "VpnServiceThread");
                thread.start();
                Log.i(TAG, "Started thread=" + thread);
            }
        }

        return START_STICKY;
    }

    private BroadcastReceiver connectivityChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(TAG, intent);
            if (intent.hasExtra(ConnectivityManager.EXTRA_NETWORK_TYPE) &&
                    intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_DUMMY) == ConnectivityManager.TYPE_WIFI) {
                Intent service = new Intent(VPNInitService.this,VPNInitService.class);
                service.putExtra(VPNInitService.EXTRA_COMMAND, Command.reload);
                startService(service);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Create");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityChangedReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroy");
        if (thread != null) {
            Log.i(TAG, "Interrupt thread=" + thread);
            thread.interrupt();
        }
        unregisterReceiver(connectivityChangedReceiver);
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "Revoke");
        if (thread != null) {
            Log.i(TAG, "Interrupt thread=" + thread);
            thread.interrupt();
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("enabled", false).apply();
        super.onRevoke();
    }

    @Override
    public synchronized void run() {
        Log.i(TAG, "Run thread=" + Thread.currentThread());
        ParcelFileDescriptor pfd = null;
        try {
            // Check if Wi-Fi connection
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            boolean wifi = (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI);
            Log.i(TAG, "wifi=" + wifi);

            // Build VPN service
            final Builder builder = new Builder();
            builder.setSession("VPNInitService");
            builder.addAddress("10.1.10.1", 32);
            builder.addRoute("0.0.0.0", 0);
            builder.setBlocking(false);

            // Add list of allowed applications
            for (AppList applist : AppList.getAppLists(this))
                if (!(wifi ? applist.wifi_blocked : applist.mobile_blocked)) {
                    Log.i(TAG, "Allowing " + applist.info.packageName);
                    builder.addDisallowedApplication(applist.info.packageName);
                }

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setConfigureIntent(pi);

            // Start VPN service
            pfd = builder.establish();

            // Drop all packets
            Log.i(TAG, "Loop start thread=" + Thread.currentThread());
            FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
            while (!Thread.currentThread().isInterrupted() && pfd.getFileDescriptor().valid())
                if (in.skip(32768) < 0)
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
            Log.i(TAG, "Loop exit thread=" + Thread.currentThread());

        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));

        } finally {
            if (pfd != null)
                try {
                    pfd.close();
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
        }
    }
}

