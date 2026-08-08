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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    /** Client side: the one connection to the host. Null on the host. */
    SendReceive sendReceive;
    /** Host side: one connection per joined client, in join order. Empty on a client. */
    final List<SendReceive> connections = Collections.synchronizedList(new ArrayList<SendReceive>());
    /** Host side: player indexes that have finished arranging and pressed BEGIN. */
    private final Set<Integer> readySet = new HashSet<>();
    /** Cap on players in a single match, host included. */
    public static final int MAX_PLAYERS = 8;
    /** True on the device that pressed HOST. The host is always player 0 and runs the lobby. */
    public static boolean isHost;
    /** This device's player index. The host deals these out when the match starts. */
    public static int turn;
    /** Player indexes that are out of the rotation: finished, or dropped out. */
    public static List<String> ignoreTurn;
    /** Player indexes that have left for good. Survives replays; ignoreTurn does not. */
    public static Set<String> departed = new HashSet<>();
    private volatile boolean isActive = true;
    /** Set once the host has dealt out player indexes; nobody may join after that. */
    private volatile boolean matchStarted = false;

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
        // Close the sockets to break the SendReceive read loops
        if (sendReceive != null) {
            try {
                if (sendReceive.socket != null) {
                    sendReceive.socket.close();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        synchronized (connections) {
            for (SendReceive c : connections) {
                try {
                    if (c.socket != null) c.socket.close();
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
        // Close the server socket if we were hosting
        if (serverClass != null && serverClass.serverSocket != null) {
            try {
                serverClass.serverSocket.close();
            } catch (Exception e) { e.printStackTrace(); }
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver2);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver4);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tempReceiver5);
    }

    public class ServerClass extends Thread{
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

                // Keep taking joiners until the host starts the match or the activity goes away.
                while (isActive && !matchStarted) {
                    Socket socket = serverSocket.accept();
                    if (matchStarted || connections.size() + 1 >= MAX_PLAYERS) {
                        // Lobby is full, or the match already started: turn them away.
                        try { socket.close(); } catch (IOException ignored) { }
                        continue;
                    }
                    SendReceive conn = new SendReceive(socket);
                    connections.add(conn);
                    conn.start();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(Start.this, "Player joined", Toast.LENGTH_SHORT).show();
                                updateLobby();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Put a message on the wire. The host is the hub, so it fans out to every client;
     * a client only ever talks to the host.
     */
    private void sendToAll(String msg) {
        if (msg == null) return;
        byte[] bytes = msg.getBytes();
        if (isHost) {
            synchronized (connections) {
                for (SendReceive c : connections) {
                    c.write(bytes);
                }
            }
        } else if (sendReceive != null) {
            sendReceive.write(bytes);
        }
    }

    /**
     * Host only: pass a client's message on to the other clients, in the order the host
     * received it. That relay order is what keeps every device's turn rotation in step.
     */
    private void relay(String msg, SendReceive from) {
        byte[] bytes = msg.getBytes();
        synchronized (connections) {
            for (SendReceive c : connections) {
                if (c != from) c.write(bytes);
            }
        }
    }

    /** Host only: note that a player has finished arranging, and start once everyone has. */
    private void markReady(int playerIndex) {
        if (!isHost) return;
        readySet.add(playerIndex);
        checkAllReady();
    }

    private void checkAllReady() {
        if (!isHost || readySet.isEmpty()) return;
        int required = Card.count - (ignoreTurn == null ? 0 : ignoreTurn.size());
        if (required < 1) required = 1;
        if (readySet.size() >= required) {
            readySet.clear();
            sendToAll("go");
            Intent go = new Intent("data3");
            go.putExtra("begin1", "go");
            LocalBroadcastManager.getInstance(Start.this).sendBroadcast(go);
        }
    }

    /**
     * A player dropped out. Everyone takes them out of the turn rotation, so play carries
     * on with the remaining players instead of stalling on a turn that will never come.
     */
    private void handlePlayerLeft(int playerIndex) {
        if (ignoreTurn == null) ignoreTurn = new ArrayList<>();
        departed.add(playerIndex + "");
        if (!ignoreTurn.contains(playerIndex + "")) ignoreTurn.add(playerIndex + "");
        readySet.remove(playerIndex);

        Intent intent = new Intent("player_left");
        intent.putExtra("playerIndex", playerIndex);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        checkAllReady();
    }

    @SuppressLint("SetTextI18n")
    private void updateLobby() {
        if (!isHost || text == null) return;
        int players = connections.size() + 1;
        text.setText("HOSTING - " + players + (players == 1 ? " player" : " players") + " connected");
        Button next = (Button) findViewById(R.id.nextBtn2);
        boolean canStart = !connections.isEmpty() && !matchStarted;
        next.setVisibility(canStart ? View.VISIBLE : View.INVISIBLE);
        next.setEnabled(canStart);
    }

    private class SendReceive extends Thread{
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        /** Host side: which player this connection is, or -1 until the match starts. */
        volatile int playerIndex = -1;

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
                                // The hub has to forward whatever a client says to the rest.
                                if (isHost) relay(completeMsg, this);
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
            connections.remove(this);
            final int lost = playerIndex;
            runOnUiThread(() -> {
                if (!isActive || isFinishing() || isDestroyed()) return;
                if (isHost) {
                    if (lost < 0) {
                        // Left the lobby before indexes were handed out: nothing to announce.
                        Toast.makeText(Start.this, "A player left the lobby", Toast.LENGTH_SHORT).show();
                        updateLobby();
                    } else {
                        Toast.makeText(Start.this, "Player " + (lost + 1) + " disconnected", Toast.LENGTH_SHORT).show();
                        sendToAll("left " + lost);
                        handlePlayerLeft(lost);
                    }
                } else {
                    // A client's only link is to the host, so losing it ends the match.
                    Toast.makeText(Start.this, "Host disconnected", Toast.LENGTH_SHORT).show();
                    LocalBroadcastManager.getInstance(Start.this)
                            .sendBroadcast(new Intent("peer_lost"));
                }
            });
        }

        public synchronized void write(byte[] bytes)
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
                isHost = false;
                sendReceive = new SendReceive(socket);
                sendReceive.start();
                runOnUiThread(new Runnable() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        text.setText("CONNECTED - waiting for the host to start");
                        findViewById(R.id.hostBtn).setEnabled(false);
                        findViewById(R.id.nextBtn).setEnabled(false);
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

                        } else if (tempMsg.startsWith("assign ")) {
                            // The host names us. Arrives before "start" on the same socket,
                            // so our index is set by the time the round begins.
                            turn = Integer.parseInt(tempMsg.substring(7).trim());
                            if (text != null) text.setText("CONNECTED - you are player " + (turn + 1));

                        } else if (tempMsg.startsWith("start ")) {
                            resetGameState(Integer.parseInt(tempMsg.substring(6).trim()));
                            Intent intent = new Intent(Start.this, Card.class);
                            startActivity(intent);

                        } else if (tempMsg.startsWith("ready ")) {
                            // Host-only bookkeeping; clients see these via the relay and ignore them.
                            if (isHost) markReady(Integer.parseInt(tempMsg.substring(6).trim()));

                        } else if (tempMsg.equals("go")) {
                            Intent intent = new Intent("data3");
                            intent.putExtra("begin1", "go");
                            LocalBroadcastManager.getInstance(Start.this).sendBroadcast(intent);

                        } else if (tempMsg.startsWith("left ")) {
                            handlePlayerLeft(Integer.parseInt(tempMsg.substring(5).trim()));

                        } else if (tempMsg.equals("replay")) {
                            // Someone pressed PLAY AGAIN — follow them into the new round
                            readySet.clear();
                            LocalBroadcastManager.getInstance(Start.this)
                                    .sendBroadcast(new Intent("remote_replay"));

                        } else if (tempMsg.startsWith("bingo ")) {
                            // Parse: "bingo <turn> <name>" — name may contain spaces
                            String[] strings = tempMsg.split(" ", 3);
                            if (strings.length >= 3) {
                                ignoreTurn.add(strings[1]);

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

    /** This player finished arranging and pressed BEGIN. */
    private BroadcastReceiver tempReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isHost) {
                markReady(turn);
            } else {
                new ThreadX("ready " + turn).start();
            }
        }
    };

    private BroadcastReceiver tempReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readySet.clear();
            new ThreadX("replay").start();
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
        isHost = false;
        departed.clear();
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
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver4,new IntentFilter("replay"));
        LocalBroadcastManager.getInstance(this).registerReceiver(tempReceiver5,new IntentFilter("bingo"));
        ignoreTurn = new ArrayList<>();
        Button scan = (Button) findViewById(R.id.nextBtn);
        Button host = (Button) findViewById(R.id.hostBtn);
        Button next = (Button) findViewById(R.id.nextBtn2);
        Button send = (Button) findViewById(R.id.send);
        text = (TextView) findViewById(R.id.info1);
        next.setVisibility(View.INVISIBLE);
        next.setEnabled(false);
        send.setVisibility(View.GONE);
        send.setEnabled(false);

        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        host.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isHost = true;
                serverClass = new ServerClass();
                serverClass.start();
                host.setEnabled(false);
                scan.setEnabled(false);
                updateLobby();
            }
        });

        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discoverServices();
            }
        });

        // Only the host starts the match: it is the one device that knows the full roster.
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isHost || connections.isEmpty() || matchStarted) return;

                matchStarted = true;
                final int players = connections.size() + 1;
                resetGameState(players);
                next.setEnabled(false);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Hand out dense indexes now that the roster is fixed. The host is 0,
                        // so a client that left the lobby earlier cannot leave a hole.
                        synchronized (connections) {
                            for (int i = 0; i < connections.size(); i++) {
                                SendReceive c = connections.get(i);
                                c.playerIndex = i + 1;
                                c.write(("assign " + c.playerIndex).getBytes());
                            }
                        }
                        sendToAll("start " + players);
                    }
                }).start();

                startActivity(new Intent(Start.this, Card.class));
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
            sendToAll(msg);
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

    /**
     * Start a round with the given number of players. Indexes come from the host, never
     * from who finished arranging first — that ordering used to be a race that could
     * give two players the same turn number.
     */
    private void resetGameState(int players) {
        Card.count = players;
        if (isHost) Start.turn = 0;   // clients keep the index their "assign" gave them
        Game.currentTurn = 0;
        Game.bingoNum = 0;
        readySet.clear();
        clearIgnoreTurn();
    }

    /** Wipe the round's finishers but keep anyone who has left the match for good. */
    public static void clearIgnoreTurn() {
        if (ignoreTurn == null) {
            ignoreTurn = new ArrayList<>();
        } else {
            ignoreTurn.clear();
        }
        ignoreTurn.addAll(departed);
    }


}