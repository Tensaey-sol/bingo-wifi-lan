package com.game.bingo;

import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.transition.Fade;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class Start extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */

    private static final boolean AUTO_HIDE = true;
    ListView listView;
    TextView text;
    NsdManager mNsdManager;
    NsdManager.RegistrationListener mRegistrationListener;
    NsdManager.DiscoveryListener mDiscoveryListener;
    String mServiceName;
    static final String SERVICE_TYPE = "_bingo._tcp.";

    List<NsdServiceInfo> discoveredServices = new ArrayList<>();
    String[] deviceNameArray;
    NsdServiceInfo[] serviceArray;
    static final int MESSAGE_READ = 1;
    ServerClass serverClass;
    ClientClass clientClass;
    SendReceive sendReceive;
    public static int peerCount;
    public static int turn;
    public static List<String> ignoreTurn;
    private volatile boolean isActive = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAfterTransition();
    }
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    public void registerService(int port) {
        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("Bingo_" + System.currentTimeMillis());
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                mServiceName = NsdServiceInfo.getServiceName();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(Start.this, "Game Hosted: " + mServiceName, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(Start.this, "Host Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {}

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {}
        };

        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    public void discoverServices() {
        discoveredServices.clear();
        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(Start.this, "Scanning...", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {}

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (service.getServiceType().contains(SERVICE_TYPE.substring(0, SERVICE_TYPE.length() - 1))) {
                    if (mServiceName != null && service.getServiceName().equals(mServiceName)) {
                        // Our own service
                    } else if (service.getServiceName().contains("Bingo")) {
                        runOnUiThread(() -> {
                            boolean alreadyFound = false;
                            for (NsdServiceInfo s : discoveredServices) {
                                if (s.getServiceName().equals(service.getServiceName())) {
                                    alreadyFound = true;
                                    break;
                                }
                            }
                            if (!alreadyFound) {
                                discoveredServices.add(service);
                                updateListView();
                            }
                        });
                    }
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                runOnUiThread(() -> {
                    for (int i = 0; i < discoveredServices.size(); i++) {
                        if (discoveredServices.get(i).getServiceName().equals(service.getServiceName())) {
                            discoveredServices.remove(i);
                            break;
                        }
                    }
                    updateListView();
                });
            }
        };

        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    private void updateListView() {
        deviceNameArray = new String[discoveredServices.size()];
        serviceArray = new NsdServiceInfo[discoveredServices.size()];
        for (int i = 0; i < discoveredServices.size(); i++) {
            deviceNameArray[i] = discoveredServices.get(i).getServiceName();
            serviceArray[i] = discoveredServices.get(i);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
        listView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        isActive = false;
        super.onDestroy();
        if (mNsdManager != null) {
            if (mRegistrationListener != null) {
                try {
                    mNsdManager.unregisterService(mRegistrationListener);
                } catch (Exception e) { e.printStackTrace(); }
            }
            if (mDiscoveryListener != null) {
                try {
                    mNsdManager.stopServiceDiscovery(mDiscoveryListener);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
        // Close the socket to break the SendReceive read loop
        if (sendReceive != null) {
            try {
                if (sendReceive.socket != null) {
                    sendReceive.socket.close();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        // Close the server socket if we were hosting
        if (serverClass != null && serverClass.serverSocket != null) {
            try {
                serverClass.serverSocket.close();
            } catch (Exception e) { e.printStackTrace(); }
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver2);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver3);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver4);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver5);
    }

    public class ServerClass extends Thread{
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(0);
                final int port = serverSocket.getLocalPort();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        registerService(port);
                    }
                });
                socket = serverSocket.accept();
                sendReceive = new SendReceive(socket);
                sendReceive.start();
                runOnUiThread(new Runnable() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        text.setText("CONNECTED (HOST)");
                        Button next = (Button) findViewById(R.id.nextBtn2);
                        Button send = (Button) findViewById(R.id.send);
                        next.setVisibility(View.VISIBLE);
                        next.setEnabled(true);
                        send.setVisibility(View.VISIBLE);
                        send.setEnabled(true);
                        Toast.makeText(Start.this, "Client Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SendReceive extends Thread{
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket skt)
        {
            socket = skt;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            StringBuilder sb = new StringBuilder();

            while (socket != null)
            {
                try {
                    bytes = inputStream.read(buffer);

                    if(bytes > 0)
                    {
                        sb.append(new String(buffer, 0, bytes));

                        // Process complete messages delimited by newline
                        int newlineIdx;
                        while ((newlineIdx = sb.indexOf("\n")) != -1) {
                            String completeMsg = sb.substring(0, newlineIdx);
                            sb.delete(0, newlineIdx + 1);

                            if (!completeMsg.isEmpty()) {
                                byte[] msgBytes = completeMsg.getBytes();
                                handler.obtainMessage(MESSAGE_READ, msgBytes.length, -1, msgBytes).sendToTarget();
                            }
                        }
                    } else if (bytes == -1) {
                        // Connection closed by remote side
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }

            // Clean up on disconnect
            try {
                if (socket != null) {
                    socket.close();
                    socket = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                if (isActive && !isFinishing() && !isDestroyed()) {
                    Toast.makeText(Start.this, "Disconnected", Toast.LENGTH_SHORT).show();
                }
            });
        }

        public void write(byte[] bytes)
        {
            try {
                // Append newline delimiter for message framing
                outputStream.write(bytes);
                outputStream.write('\n');
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ClientClass extends Thread{
        Socket socket;
        String hostAdd;
        int port;

        public ClientClass(InetAddress hostAddress, int port)
        {
            this.hostAdd = hostAddress.getHostAddress();
            this.port = port;
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, port), 5000);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
                runOnUiThread(new Runnable() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        text.setText("CONNECTED (CLIENT)");
                        Button next = (Button) findViewById(R.id.nextBtn2);
                        Button send = (Button) findViewById(R.id.send);
                        next.setVisibility(View.VISIBLE);
                        next.setEnabled(true);
                        send.setVisibility(View.VISIBLE);
                        send.setEnabled(true);
                        Toast.makeText(Start.this, "Connected to Host", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Start.this, "Connection Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            // Don't process messages after activity is destroyed
            if (!isActive) return true;

            switch (msg.what)
            {
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    int temp = 0;
                    String tempMsg = new String(readBuff, 0, msg.arg1);

                    try{
                        temp = Integer.parseInt(tempMsg);
                    } catch (Exception e){ e.printStackTrace(); }

                    try {
                        if (temp < 26 && temp > 0) {
                            Intent intent = new Intent("data1");
                            intent.putExtra("idMess", tempMsg);
                            LocalBroadcastManager.getInstance(Start.this).sendBroadcast(intent);

                        } else if (tempMsg.equals("start")) {
                            resetGameState();
                            peerCount = 1;
                            Intent intent = new Intent(Start.this, Card.class);
                            startActivity(intent);

                        } else if (tempMsg.equals("begin")) {
                            Intent intent = new Intent("data3");
                            intent.putExtra("begin1", "begin");
                            LocalBroadcastManager.getInstance(Start.this).sendBroadcast(intent);

                        } else if (tempMsg.equals("ready")) {
                            Intent intent = new Intent("data3");
                            intent.putExtra("begin1", "ready");
                            LocalBroadcastManager.getInstance(Start.this).sendBroadcast(intent);

                        } else if (tempMsg.equals("not ready")) {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(Start.this, "Some of the players are still arranging", Toast.LENGTH_SHORT).show();
                            }

                        } else if (tempMsg.equals("peer")) {
                            Card.count++;

                        } else if (tempMsg.contains("bingo")) {
                            // Parse: "bingo <turn> <name>" — name may contain spaces
                            String[] strings = tempMsg.split(" ", 3);
                            if (strings.length >= 3) {
                                ignoreTurn.add(strings[1]);
                                if(!ignoreTurn.contains(turn+""))
                                    Game.bingoNum++;

                                // Tell Game.java to end the game for the losing player
                                Intent intent = new Intent("remote_bingo");
                                intent.putExtra("winnerName", strings[2]);
                                LocalBroadcastManager.getInstance(Start.this).sendBroadcast(intent);
                            }
                        } else {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast toast = Toast.makeText(Start.this, tempMsg, Toast.LENGTH_SHORT);
                                toast.setGravity(Gravity.CENTER, 0, 0);
                                toast.show();
                            }

                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    break;
            }
            return true;
        }
    });

    private BroadcastReceiver tempReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ThreadX thread = new ThreadX(intent.getStringExtra("idMsg"));
            thread.start();
        }
    };

    private BroadcastReceiver tempReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ThreadX thread = new ThreadX(intent.getStringExtra("begin"));
            thread.start();
        }
    };

    private BroadcastReceiver tempReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ThreadX thread = new ThreadX(intent.getStringExtra("ready"));
            thread.start();
        }
    };

    private BroadcastReceiver tempReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ThreadX thread = new ThreadX(intent.getStringExtra("peer"));
            thread.start();
        }
    };

    private BroadcastReceiver tempReceiver5 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ThreadX thread = new ThreadX(intent.getStringExtra("bingo"));
            thread.start();
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setEnterTransition(new Fade());
        getWindow().setExitTransition(new Fade());

        setContentView(R.layout.start);

        Card.count = 0;
        Card.isReplay = false;
        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hide();
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver,new IntentFilter("data"));
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver2,new IntentFilter("data2"));
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver3,new IntentFilter("data4"));
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver4,new IntentFilter("peer"));
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver5,new IntentFilter("bingo"));
        ignoreTurn = new ArrayList<>();
        Button scan = (Button) findViewById(R.id.nextBtn);
        Button host = (Button) findViewById(R.id.hostBtn);
        Button next = (Button) findViewById(R.id.nextBtn2);
        Button send = (Button) findViewById(R.id.send);
        text = (TextView) findViewById(R.id.info1);
        next.setVisibility(View.INVISIBLE);
        next.setEnabled(false);
        send.setVisibility(View.INVISIBLE);
        send.setEnabled(false);

        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        host.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverClass = new ServerClass();
                serverClass.start();
                host.setEnabled(false);
            }
        });

        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discoverServices();
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetGameState();
                peerCount = 1;
                ThreadX thread = new ThreadX("start");
                thread.start();
                Intent intent = new Intent(Start.this, Card.class);
                startActivity(intent);
            }
        });

        listView = (ListView) findViewById(R.id.listview);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                NsdServiceInfo service = serviceArray[i];
                Toast.makeText(Start.this, "Connecting...", Toast.LENGTH_SHORT).show();
                mNsdManager.resolveService(service, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        runOnUiThread(() -> Toast.makeText(Start.this, "Failed to resolve service", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        clientClass = new ClientClass(serviceInfo.getHost(), serviceInfo.getPort());
                        clientClass.start();
                    }
                });
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(sendReceive != null) {
                    ThreadX thread = new ThreadX("TEST 123");
                    thread.start();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
    }

    class ThreadX extends Thread{

        String msg;

        ThreadX(String msg){
            this.msg = msg;
        }

        @Override
        public void run() {
            if(sendReceive != null) {
                sendReceive.write(msg.getBytes());
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        hide();
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, 0);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private void resetGameState() {
        Card.count = 0;
        Start.turn = 0;
        if (Start.ignoreTurn != null) {
            Start.ignoreTurn.clear();
        } else {
            Start.ignoreTurn = new ArrayList<>();
        }
    }


}