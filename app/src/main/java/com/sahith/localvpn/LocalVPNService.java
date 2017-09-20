/*
    Logic:
      Enforce vpn on all apps except the one which should access the internet.
      In vpn, drop all packets.
      The app which is excluded can access internet outside the vpn.
*/

package com.sahith.localvpn;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class LocalVPNService extends VpnService
{
    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // IPv4 address
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    public static final String BROADCAST_VPN_STATE = "com.sahith.localvpn.VPN_STATE";

    private static boolean isRunning = false; // state of vpn

    private ParcelFileDescriptor vpnInterface = null; //vpn interface

    private PendingIntent pendingIntent; // intent to be used by vpnservice builder

    private ExecutorService executorService; // to run a continuous thread of vpnservice


    @Override
    public void onCreate()
    {
        super.onCreate();
        isRunning = true;
        setupVPN();
        executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor())); // thread to intercept packets
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
        Log.i(TAG, "Started");
    }

    private void setupVPN()
    {
        if (vpnInterface == null)
        {
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addRoute(VPN_ROUTE, 0);
            try
            {
                // vpn is enforced on all apps except fb messenger("com.facebook.orca")
                // replace it with safe app("com.iitb.cse.arkenstone.safe")
                // this works only with api level >= 21 i.e, lollipop. looking into implementation for earlier apis
                builder.addDisallowedApplication("com.facebook.orca");
            }
            catch (PackageManager.NameNotFoundException e)
            {
                Log.e(TAG, "Wrong Package Name");
            }
            vpnInterface = builder.setSession(getString(R.string.app_name)).setConfigureIntent(pendingIntent).establish();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    public static boolean isRunning()
    {
        return isRunning;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        isRunning = false;
        executorService.shutdownNow(); // closing the service
        Log.i(TAG, "Stopped");
    }

    private static class VPNRunnable implements Runnable
    {
        private static final String TAG = VPNRunnable.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor; // file descriptor for packet interception
        public VPNRunnable(FileDescriptor vpnFileDescriptor)
        {
            this.vpnFileDescriptor = vpnFileDescriptor;
        }

        @Override
        public void run()
        {
            Log.i(TAG, "Started");

            // the following are the file channels to be used to intercept the packets
            // since we only need to drop all packets, we're not using them
            // FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel(); // input to vpn i.e, from device to network
            // FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel(); // output of vpn i.e, from network to device

            try
            {
                while (!Thread.interrupted())
                {
                    // sleep looping is not very battery friendly
                    Thread.sleep(10);
                }
            }
            catch (InterruptedException e)
            {
                Log.i(TAG, "Stopping");
            }
        }
    }
}
