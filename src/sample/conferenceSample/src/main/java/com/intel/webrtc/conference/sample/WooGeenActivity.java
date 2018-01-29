/*
 * Copyright © 2017 Intel Corporation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.intel.webrtc.conference.sample;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.intel.webrtc.base.ActionCallback;
import com.intel.webrtc.base.ClientContext;
import com.intel.webrtc.base.ConnectionStats;
import com.intel.webrtc.base.LocalCameraStream;
import com.intel.webrtc.base.LocalCameraStreamParameters;
import com.intel.webrtc.base.LocalCameraStreamParameters.CameraType;
import com.intel.webrtc.base.LocalScreenStream;
import com.intel.webrtc.base.LocalScreenStreamParameters;
import com.intel.webrtc.base.MediaCodec.VideoCodec;
import com.intel.webrtc.base.RemoteCameraStream;
import com.intel.webrtc.base.RemoteScreenStream;
import com.intel.webrtc.base.RemoteStream;
import com.intel.webrtc.base.Stream;
import com.intel.webrtc.base.WoogeenException;
import com.intel.webrtc.base.WoogeenIllegalArgumentException;
import com.intel.webrtc.conference.ConferenceClient;
import com.intel.webrtc.conference.ConferenceClient.ConferenceClientObserver;
import com.intel.webrtc.conference.ConferenceClientConfiguration;
import com.intel.webrtc.conference.ConnectionOptions;
import com.intel.webrtc.conference.ExternalOutputAck;
import com.intel.webrtc.conference.ExternalOutputOptions;
import com.intel.webrtc.conference.PublishOptions;
import com.intel.webrtc.conference.RecordAck;
import com.intel.webrtc.conference.RecordOptions;
import com.intel.webrtc.conference.Region;
import com.intel.webrtc.conference.RemoteMixedStream;
import com.intel.webrtc.conference.SubscribeOptions;
import com.intel.webrtc.conference.User;
import com.intel.webrtc.sample.utils.WoogeenSurfaceRenderer;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.RendererCommon;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

@SuppressWarnings("deprecation")
@TargetApi(21)
public class WooGeenActivity extends TabActivity implements
                                                 ConferenceClientObserver, View.OnClickListener,
                                                 RemoteMixedStream.RemoteMixedStreamObserver {
    private static final String TAG = "WooGeen-Activity";

    private ConferenceClient mRoom;
    private WoogeenSurfaceRenderer localStreamRenderer;
    private WoogeenSurfaceRenderer remoteStreamRenderer;

    private LinearLayout localViewContainer;
    private LinearLayout remoteViewContainer;
    private EglBase rootEglBase;

    private String basicServerString = "https://example.com:3004/";
    private String externalServerString = "rtmp://user:password@example.com/some/url";
    private static final String stunAddr = "stun:example.com";
    private static final String turnAddrUDP = "turn:example.com:port?transport=udp";
    private static final String turnAddrTCP = "turn:example.com:port?transport=tcp";
    private LocalCameraStream localStream;
    private LocalScreenStream screenStream;
    private RemoteStream currentRemoteStream;

    private int resolutionSelected = 0;

    private Button callButton;
    private Button connectButton;
    private Button localAudioButton;
    private Button localVideoButton;
    private Button remoteAudioButton;
    private Button remoteVideoButton;
    private Button startRecorderButton;
    private Button stopRecorderButton;
    private Button switchCameraButton;
    private Button externalButton;
    private Button screenShareButton;
    private EditText roomIdEditText;
    private EditText remoteMessageEditText, localMessageEditText;
    private EditText basicServerEditText, externalServerEditText;
    private TextView statsText;
    private RatingBar ratingBar;
    private ZoomControls zoomControls;
    private int cameraID = 0;
    private Message msg;

    public static final int MSG_ROOM_DISCONNECTED = 98;
    public static final int MSG_PUBLISH = 99;
    public static final int MSG_LOGIN = 100;
    public static final int MSG_SUBSCRIBE = 101;
    public static final int MSG_UNSUBSCRIBE = 102;
    public static final int MSG_UNPUBLISH = 103;
    public static final int MSG_PAUSEVIDEO = 104;
    public static final int MSG_PLAYVIDEO = 105;
    public static final int MSG_PAUSEAUDIO = 106;
    public static final int MSG_PLAYAUDIO = 107;
    public static final int MSG_SWITCHCAMERA = 108;
    public static final int MSG_START_EXTERNAL = 109;
    public static final int MSG_STOP_EXTERNAL = 110;
    public static final int MSG_START_RECORDER = 111;
    public static final int MSG_STOP_RECORDER = 112;
    public static final int MSG_SEND_MSG = 113;
    public static final int STATUS_REMOTE = 114;
    public static final int STATUS_LOCAL = 115;
    public static final int MSG_START_SHARESCREEN = 116;
    public static final int MSG_STOP_SHARESCREEN = 117;

    private HandlerThread roomThread;
    private RoomHandler roomHandler;

    private String roomId;
    private String recorderId;
    private String externalOutputId;

    private List<RemoteStream> subscribedStreams = new ArrayList<RemoteStream>();

    private SSLContext sslContext = null;
    private HostnameVerifier hostnameVerifier = null;

    private Timer statsTimer;
    final long interval = 3000;
    private long lastSubscribeByteReceived = 0;

    final StringBuffer statsStr = new StringBuffer("");
    final HashMap<String, String> stsList = new HashMap<>();

    private int originAudioMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                Log.d(TAG, "uncaughtexception");
                e.printStackTrace();
                System.exit(-1);
            }
        });
        AudioManager audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        audioManager.setSpeakerphoneOn(true);
        originAudioMode = audioManager.getMode();
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                                     audioManager.getStreamMaxVolume(
                                             AudioManager.STREAM_VOICE_CALL) / 4,
                                     AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        registerReceiver(new WiredHeadsetReceiver(), new IntentFilter(Intent.ACTION_HEADSET_PLUG));

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.tabhost);
        TabHost mTabHost = getTabHost();
        // Main tab
        TabSpec mainSpec = mTabHost.newTabSpec("tab_video");
        mainSpec.setIndicator("Main");
        mainSpec.setContent(R.id.tab_video);
        mTabHost.addTab(mainSpec);
        // chat tab
        TabSpec chatSpec = mTabHost.newTabSpec("tab_chat");
        chatSpec.setIndicator("Chat");
        chatSpec.setContent(R.id.tab_chat);
        mTabHost.addTab(chatSpec);
        // server tab
        TabSpec serverSpec = mTabHost.newTabSpec("tab_server");
        serverSpec.setIndicator("Configuration");
        serverSpec.setContent(R.id.tab_server);
        mTabHost.addTab(serverSpec);
        // stream tab
        localStream = null;
        TabSpec streamSpec = mTabHost.newTabSpec("tab_stream");
        streamSpec.setIndicator("StreamSetting");
        streamSpec.setContent(R.id.tab_stream);
        mTabHost.addTab(streamSpec);

        int childCount = mTabHost.getTabWidget().getChildCount();
        for (int i = 0; i < childCount; i++) {
            mTabHost.getTabWidget().getChildAt(i)
                    .getLayoutParams().height = (int) (30 * getResources()
                    .getDisplayMetrics().density);
        }

        roomIdEditText = (EditText) findViewById(R.id.roomId);
        remoteMessageEditText = (EditText) findViewById(R.id.remoteMsgEdTx);
        localMessageEditText = (EditText) findViewById(R.id.localMsgEdTx);
        basicServerEditText = (EditText) findViewById(R.id.basicServerEdTx);
        basicServerEditText.setText(basicServerString);
        externalServerEditText = (EditText) findViewById(R.id.externalServerEdTx);
        externalServerEditText.setText(externalServerString);

        callButton = (Button) findViewById(R.id.btStartStopCall);
        connectButton = (Button) findViewById(R.id.btConnect);
        localAudioButton = (Button) findViewById(R.id.btSetLocalAudio);
        localVideoButton = (Button) findViewById(R.id.btSetLocalVideo);
        remoteAudioButton = (Button) findViewById(R.id.btSetRemoteAudio);
        remoteVideoButton = (Button) findViewById(R.id.btSetRemoteVideo);
        startRecorderButton = (Button) findViewById(R.id.btStartRecorder);
        stopRecorderButton = (Button) findViewById(R.id.btStopRecorder);
        switchCameraButton = (Button) findViewById(R.id.btSwitchCamera);
        externalButton = (Button) findViewById(R.id.externalSeverBtn);
        screenShareButton = (Button) findViewById(R.id.btShareScreen);
        localViewContainer = (LinearLayout) findViewById(R.id.llLocalView);
        remoteViewContainer = (LinearLayout) findViewById(R.id.llRemoteView);
        statsText = (TextView) findViewById(R.id.stats);

        ratingBar = (RatingBar) findViewById(R.id.ratingBar);
        RemoteViewOnTouchListener mOnTouchListener = new RemoteViewOnTouchListener(this);
        remoteViewContainer.setOnTouchListener(mOnTouchListener);

        zoomControls = (ZoomControls) findViewById(R.id.zoomControls);
        zoomControls.setIsZoomInEnabled(false);
        zoomControls.setIsZoomOutEnabled(false);

        zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (localStream != null) {
                    Camera.Parameters parameters = localStream.getCameraParameters();
                    int currentZoomLevel = parameters.getZoom();
                    if (currentZoomLevel < parameters.getMaxZoom()) {
                        currentZoomLevel += parameters.getMaxZoom() / 4;
                        if (currentZoomLevel > parameters.getMaxZoom()) {
                            parameters.setZoom(parameters.getMaxZoom());
                        } else {
                            parameters.setZoom(currentZoomLevel);
                        }
                        localStream.setCameraParameters(parameters);
                    }
                }

            }
        });

        zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (localStream != null) {
                    Camera.Parameters parameters = localStream.getCameraParameters();
                    int currentZoomLevel = parameters.getZoom();
                    if (currentZoomLevel > 0) {
                        currentZoomLevel -= parameters.getMaxZoom() / 4;
                        if (currentZoomLevel < 0) {
                            parameters.setZoom(0);
                        } else {
                            parameters.setZoom(currentZoomLevel);
                        }
                        localStream.setCameraParameters(parameters);
                    }
                }

            }
        });

        initRoom();
        initVideoStreamsViews();
    }

    @Override
    protected void onPause() {
        if (localStream != null) {
            localStream.disableVideo();
            localStream.disableAudio();
            localStream.detach();
            Toast.makeText(this, "Woogeen is running in the background.",
                           Toast.LENGTH_SHORT).show();
        }
        if (currentRemoteStream != null) {
            currentRemoteStream.disableAudio();
            currentRemoteStream.disableVideo();
            currentRemoteStream.detach();
        }
        ((AudioManager) getSystemService(AUDIO_SERVICE)).setMode(originAudioMode);
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (localStream != null) {
            localStream.enableVideo();
            localStream.enableAudio();
            try {
                localStream.attach(localStreamRenderer);
            } catch (WoogeenIllegalArgumentException e) {
                e.printStackTrace();
            }
            Toast.makeText(this, "Welcome back", Toast.LENGTH_SHORT).show();
        }
        if (currentRemoteStream != null) {
            currentRemoteStream.enableVideo();
            currentRemoteStream.enableAudio();
            try {
                currentRemoteStream.attach(remoteStreamRenderer);
            } catch (WoogeenIllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        ((AudioManager) getSystemService(AUDIO_SERVICE)).setMode(
                AudioManager.MODE_IN_COMMUNICATION);
        super.onResume();
    }

    public void onClick(View arg0) {
        msg = new Message();
        switch (arg0.getId()) {
            case R.id.btConnect:
                if (connectButton.getText().toString().equals("Connect")) {
                    msg.what = MSG_LOGIN;
                } else {
                    msg.what = MSG_ROOM_DISCONNECTED;
                }
                roomHandler.sendMessage(msg);
                break;
            case R.id.btStartStopCall:
                if (callButton.getText().toString().equals("Start Video")) {
                    LocalCameraStreamParameters.CameraType[] cameraType =
                            LocalCameraStreamParameters.getCameraList();
                    int cameraNum = cameraType.length;
                    if (cameraNum == 0) {
                        Toast.makeText(WooGeenActivity.this,
                                       "You do not have a camera.",
                                       Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String cameraLists[] = new String[cameraNum];
                    for (int i = 0; i < cameraNum; i++) {
                        if (cameraType[i] == LocalCameraStreamParameters.CameraType.BACK) {
                            cameraLists[i] = "Back";
                        } else if (cameraType[i] == LocalCameraStreamParameters.CameraType.FRONT) {
                            cameraLists[i] = "Front";
                        } else if (cameraType[i] ==
                                LocalCameraStreamParameters.CameraType.UNKNOWN) {
                            cameraLists[i] = "Unknown";
                        }
                    }
                    new AlertDialog.Builder(WooGeenActivity.this)
                            .setTitle("Select Camera")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setSingleChoiceItems(cameraLists, -1,
                                                  new DialogInterface.OnClickListener() {

                                                      public void onClick(DialogInterface dialog,
                                                                          int id) {
                                                          dialog.dismiss();
                                                          cameraID = id;
                                                          msg.what = MSG_PUBLISH;
                                                          callButton.setEnabled(false);
                                                          roomHandler.sendMessage(msg);
                                                      }
                                                  }
                                                 )
                            .setNegativeButton("Cancel",
                                               new DialogInterface.OnClickListener() {
                                                   public void onClick(DialogInterface dialog,
                                                                       int id) {}
                                               }
                                              )
                            .show();
                } else {
                    msg.what = MSG_UNPUBLISH;
                    callButton.setEnabled(false);
                    roomHandler.sendMessage(msg);
                }
                break;
            case R.id.btSetLocalAudio:
                try {
                    if (localStream != null) {
                        if (localAudioButton.getText().toString().equals(getString(R.string.pauseLocalAudio))) {
                            localStream.disableAudio();
                            localAudioButton.setText(getString(R.string.startLocalAudio));
                        } else {
                            localStream.enableAudio();
                            localAudioButton.setText(getString(R.string.pauseLocalAudio));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case R.id.btSetLocalVideo:
                try {
                    if (localStream != null) {
                        if (localVideoButton.getText().toString().equals(getString(R.string.pauseLocalVideo))) {
                            localStream.disableVideo();
                            localVideoButton.setText(getString(R.string.startLocalVideo));
                        } else {
                            localStream.enableVideo();
                            localVideoButton.setText(getString(R.string.pauseLocalVideo));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case R.id.btSetRemoteAudio:
                if (remoteAudioButton.getText().toString().equals(getString(R.string.pauseAudio))) {
                    msg.what = MSG_PAUSEAUDIO;
                } else {
                    msg.what = MSG_PLAYAUDIO;
                }
                remoteAudioButton.setEnabled(false);
                roomHandler.sendMessage(msg);
                break;
            case R.id.btSetRemoteVideo:
                if (remoteVideoButton.getText().toString().equals(getString(R.string.pauseVideo))) {
                    msg.what = MSG_PAUSEVIDEO;
                } else {
                    msg.what = MSG_PLAYVIDEO;
                }
                remoteVideoButton.setEnabled(false);
                roomHandler.sendMessage(msg);
                break;
            case R.id.sendBt:
                msg = new Message();
                msg.what = MSG_SEND_MSG;
                roomHandler.sendMessage(msg);
                break;
            case R.id.btStartRecorder:
                msg = new Message();
                msg.what = MSG_START_RECORDER;
                roomHandler.sendMessage(msg);
                break;
            case R.id.btStopRecorder:
                msg = new Message();
                msg.what = MSG_STOP_RECORDER;
                roomHandler.sendMessage(msg);
                break;
            case R.id.btSwitchCamera:
                msg = new Message();
                msg.what = MSG_SWITCHCAMERA;
                roomHandler.sendMessage(msg);
                switchCameraButton.setEnabled(false);
                break;
            case R.id.externalSeverBtn:
                if (externalButton.getText().toString().equals("Start")) {
                    msg = new Message();
                    msg.what = MSG_START_EXTERNAL;
                    roomHandler.sendMessage(msg);
                    externalButton.setEnabled(false);
                } else {
                    msg = new Message();
                    msg.what = MSG_STOP_EXTERNAL;
                    roomHandler.sendMessage(msg);
                    externalButton.setEnabled(false);
                }
                break;
            case R.id.btShowStats:
                if (statsText.isShown()) {
                    statsText.setVisibility(View.GONE);
                } else {
                    statsText.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.btShareScreen:
                if (screenShareButton.getText().equals("Share Screen")) {
                    MediaProjectionManager manager = (MediaProjectionManager) getApplication()
                            .getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(manager.createScreenCaptureIntent(), 1);
                } else {
                    msg.what = MSG_STOP_SHARESCREEN;
                    roomHandler.sendMessage(msg);
                }
                screenShareButton.setEnabled(false);
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        LocalScreenStreamParameters lssp = new LocalScreenStreamParameters(resultCode, data);
        lssp.setResolution(1280, 720);
        screenStream = new LocalScreenStream(lssp);

        Message msg = new Message();
        msg.what = MSG_START_SHARESCREEN;
        roomHandler.sendMessage(msg);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
        return super.onKeyDown(keyCode, event);
    }

    void showResolutionSelect() {
        for (final RemoteStream stream : mRoom.getRemoteStreams()) {
            if (stream instanceof RemoteMixedStream
                    && ((RemoteMixedStream) stream).getViewport().equals("common")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        List<Hashtable<String, Integer>> list =
                                ((RemoteMixedStream) stream).getSupportedResolutions();
                        if (list.size() != 0) {
                            String[] itemList = new String[list.size()];
                            for (int i = 0; i < list.size(); i++) {
                                itemList[i] = list.get(i).get("width").toString()
                                              + " x "
                                              + list.get(i).get("height").toString();
                            }
                            AlertDialog.Builder resoSelect =
                                    new AlertDialog.Builder(WooGeenActivity.this);
                            resoSelect.setTitle("Please select the resolution of mixed stream");
                            DialogInterface.OnClickListener itemListener =
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            resolutionSelected = which;
                                }
                            };
                            resoSelect.setSingleChoiceItems(itemList, 0, itemListener);

                            DialogInterface.OnClickListener okListener =
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            subscribeMixed((RemoteMixedStream) stream);
                                        }
                                    };
                            resoSelect.setPositiveButton("OK", okListener);
                            resoSelect.create().show();
                        } else {
                            subscribeMixed((RemoteMixedStream) stream);
                        }
                    }
                });
                break;
            }
        }
    }

    void subscribeMixed(RemoteMixedStream stream) {
        Message msg = new Message();
        msg.what = MSG_SUBSCRIBE;
        msg.obj = stream;
        roomHandler.sendMessage(msg);
    }

    @Override
    public void onServerDisconnected() {
        Log.d(TAG, "onRoomDisconnected");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callButton.setText("Start Video");
                callButton.setEnabled(false);
                connectButton.setText("Connect");
                localAudioButton.setText(getString(R.string.pauseLocalAudio));
                localAudioButton.setEnabled(false);
                localVideoButton.setText(getString(R.string.pauseLocalVideo));
                localVideoButton.setEnabled(false);
                remoteAudioButton.setText(getString(R.string.pauseAudio));
                remoteAudioButton.setEnabled(false);
                remoteVideoButton.setText(getString(R.string.pauseVideo));
                remoteVideoButton.setEnabled(false);
                switchCameraButton.setEnabled(false);
                externalButton.setEnabled(false);
                screenShareButton.setText("Share Screen");
                zoomControls.setIsZoomInEnabled(false);
                zoomControls.setIsZoomOutEnabled(false);
                statsText.setVisibility(View.GONE);
                if (statsTimer != null) {
                    statsTimer.cancel();
                }
                ratingBar.setRating(0);
                Toast.makeText(WooGeenActivity.this, "Room DisConnected",
                               Toast.LENGTH_SHORT).show();
            }
        });
        currentRemoteStream = null;
        subscribedStreams.clear();
        localStreamRenderer.cleanFrame();
        remoteStreamRenderer.cleanFrame();
        if (localStream != null) {
            localStream.close();
            localStream = null;
        }
        if (screenStream != null) {
            screenStream.close();
            screenStream = null;
        }
    }

    @Override
    public void onStreamAdded(RemoteStream remoteStream) {
        Log.d(TAG, "onStreamAdded: streamId = " + remoteStream.getId()
                + ", from " + remoteStream.getRemoteUserId());

        //We don't subscribe the streams from ourselves
        if (localStream != null && (remoteStream.getId().equals(localStream.getId()))) {
            return;
        }

        if (screenStream != null && (remoteStream.getId().equals(screenStream.getId()))) {
            return;
        }

        //We only subscribe the "common" mix stream and screen stream by default
        if (remoteStream instanceof RemoteMixedStream
                && ((RemoteMixedStream) remoteStream).getViewport().equals("common")) {
            showResolutionSelect();
        } else if (remoteStream instanceof RemoteScreenStream) {
            Message msg = new Message();
            msg.what = MSG_SUBSCRIBE;
            msg.obj = remoteStream;
            roomHandler.sendMessage(msg);
        }
    }

    @Override
    public void onStreamRemoved(RemoteStream remoteStream) {
        Log.d(TAG, "onStreamRemoved: streamId = " + remoteStream.getId());
        if (currentRemoteStream != null
                && currentRemoteStream.getId().equals(remoteStream.getId())) {
            Message msg = new Message();
            msg.what = MSG_UNSUBSCRIBE;
            msg.obj = remoteStream;
            roomHandler.sendMessage(msg);

            currentRemoteStream = null;
            remoteStreamRenderer.cleanFrame();
        }
        for (int i = 0; i < subscribedStreams.size(); i++) {
            if (subscribedStreams.get(i).getId().equals(remoteStream.getId())) {
                subscribedStreams.remove(i);
                break;
            }
        }
        if (subscribedStreams.size() == 0 || currentRemoteStream != null) {
            return;
        }
        // If there is another remote stream subscribed, render it.
        RemoteStream streamToBeRendered = subscribedStreams.get(0);
        for (int i = 0; i < subscribedStreams.size(); i++) {
            if (subscribedStreams.get(i) instanceof RemoteScreenStream) {
                streamToBeRendered = subscribedStreams.get(i);
                break;
            }
        }
        try {
            currentRemoteStream = streamToBeRendered;
            mRoom.playVideo(currentRemoteStream, null);
            currentRemoteStream.attach(remoteStreamRenderer);
        } catch (WoogeenIllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStreamError(final Stream errorStream, final WoogeenException exception) {
        Log.w(TAG,
              "If this event is triggered, it means an error has happened on the published " +
                      "or subscribed stream. Recovering the app status and republishing or " +
                      "resubscribing may be needed.");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WooGeenActivity.this,
                               "onStreamError " + errorStream.getId() + " "
                                       + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onUserJoined(final User user) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WooGeenActivity.this,
                               "A client named " + user.getName() + " has joined this room.",
                               Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onUserLeft(final User user) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(WooGeenActivity.this,
                               "A client named " + user.getName() + " has left this room.",
                               Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMessageReceived(final String sender, final String message,
                                  final boolean broadcast) {
        runOnUiThread(new Runnable() {
            public void run() {
                String userName = sender;
                for (User user : mRoom.getUsers()) {
                    if (user.getId().equals(sender)) {
                        userName = user.getName();
                        break;
                    }
                }
                remoteMessageEditText.setText(remoteMessageEditText.getText().toString()
                                                      + "\n" + (broadcast ? "[Broadcast message]"
                                                                          : "[Private message]")
                                                      + userName + ":" + message);
            }
        });
    }

    private void initRoom() {
        rootEglBase = EglBase.create();
        ClientContext.setApplicationContext(this);
        //To ignore cellular network.
        //ClientContext.addIgnoreNetworkType(ClientContext.NetworkType.CELLULAR);
        ClientContext.setVideoHardwareAccelerationOptions(rootEglBase.getEglBaseContext(),
                                                          rootEglBase.getEglBaseContext());
        ConferenceClientConfiguration config = new ConferenceClientConfiguration();
        List<IceServer> iceServers = new ArrayList<>();
        //iceServers.add(new IceServer(stunAddr));
        //iceServers.add(new IceServer(turnAddrTCP, "woogeen", "master"));
        //iceServers.add(new IceServer(turnAddrUDP, "woogeen", "master"));
        try {
            config.setIceServers(iceServers);
        } catch (WoogeenException e1) {
            e1.printStackTrace();
        }
        mRoom = new ConferenceClient(config);
        mRoom.addObserver(this);

        roomThread = new HandlerThread("Room Thread");
        roomThread.start();
        roomHandler = new RoomHandler(roomThread.getLooper());
    }

    private void initVideoStreamsViews() {
        localStreamRenderer = new WoogeenSurfaceRenderer(this);
        remoteStreamRenderer = new WoogeenSurfaceRenderer(this);
        localStreamRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        localViewContainer.addView(localStreamRenderer);
        remoteViewContainer.addView(remoteStreamRenderer);

        localStreamRenderer.init(rootEglBase.getEglBaseContext(), null);
        remoteStreamRenderer.init(rootEglBase.getEglBaseContext(), null);
    }

    @Override
    public void onVideoLayoutChanged() {
        Log.d(TAG, "onVideoLayoutChanged");
    }

    //This is to set up an SSL context enabled with a self-signed certificate.
    private void setUpSelfsignedSSLContext() {
        InputStream caInput;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            //democert.crt is a demo certificate, you need to substitute it with a proper one.
            caInput = WooGeenActivity.this.getResources().openRawResource(R.raw.democert);
            Certificate ca = cf.generateCertificate(caInput);
            caInput.close();

            // Create a KeyStore containing the trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

        } catch (CertificateException | IOException | NoSuchAlgorithmException |
                KeyStoreException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    //******************WARNING****************
    //DO NOT IMPLEMENT THIS IN PRODUCTION CODE
    //*****************************************
    private void setUpINSECURESSLContext() {
        TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }
        };

        hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    String getTokenSSLSelfsigned(String basicServer, String roomId) {
        setUpSelfsignedSSLContext();
        return doGetToken(basicServer, roomId, true);
    }

    //******************WARNING****************
    //DO NOT IMPLEMENT THIS IN PRODUCTION CODE
    //*****************************************
    String getTokenSSLINSECURE(String basicServer, String roomId) {
        setUpINSECURESSLContext();
        return doGetToken(basicServer, roomId, true);
    }

    String getToken(String basicServer, String roomId) {
        return doGetToken(basicServer, roomId, false);
    }

    private String doGetToken(String basicServer, String roomId, boolean isSecure) {
        StringBuilder token = new StringBuilder("");
        URL url;
        HttpURLConnection httpURLConnection = null;
        try {
            url = new URL(basicServer + "createToken/");
            if (isSecure) {
                httpURLConnection = (HttpsURLConnection) url.openConnection();
                ((HttpsURLConnection) httpURLConnection).setSSLSocketFactory(
                        sslContext.getSocketFactory());
                if (hostnameVerifier != null) {
                    Log.w(TAG, "YOU ARE NOT VERIFYING THE HOSTNAME");
                    Log.w(TAG, "DO NOT IMPLEMENT THIS IN PRODUCTION CODE");
                    //******************WARNING****************
                    //DO NOT IMPLEMENT THIS IN PRODUCTION CODE
                    //*****************************************
                    ((HttpsURLConnection) httpURLConnection).setHostnameVerifier(hostnameVerifier);
                }
            } else {
                httpURLConnection = (HttpURLConnection) url.openConnection();
            }
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setUseCaches(false);
            httpURLConnection.setRequestProperty("Content-Type", "application/json");
            httpURLConnection.setRequestProperty("Accept", "application/json");
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setRequestMethod("POST");

            DataOutputStream out = new DataOutputStream(httpURLConnection.getOutputStream());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", "presenter");
            jsonObject.put("username", "user");
            jsonObject.put("room", roomId.equals("") ? "" : roomId);
            out.write(jsonObject.toString().getBytes("UTF-8"));
            out.flush();
            out.close();

            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(httpURLConnection.getInputStream()));
                String lines;
                while ((lines = reader.readLine()) != null) {
                    lines = new String(lines.getBytes(), "UTF-8");
                    token.append(lines);
                }
                reader.close();
            }

        } catch (JSONException | IOException e) {
            e.printStackTrace();
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }

        return token.toString();
    }

    class RoomHandler extends Handler {
        public RoomHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MSG_LOGIN:
                    roomId = roomIdEditText.getText().toString().trim();
                    Pattern pattern = Pattern.compile("[0-9a-zA-Z]*");
                    if(!pattern.matcher(roomId).matches()){
                        Toast.makeText(WooGeenActivity.this,
                                "Room Id should only contain alphabets and numbers!",
                                Toast.LENGTH_SHORT).show();
                        break;
                    }

                    runOnUiThread(new Runnable() {
                        public void run() {
                            roomIdEditText.setEnabled(false);
                            connectButton.setEnabled(false);
                        }
                    });
                    basicServerString = basicServerEditText.getText().toString();
                    //If you would like to connect to server without SSL, or the server enables SSL
                    //with a certificate issued by a well-known CA, please use getToken and no
                    // need to set ConnectionOptions.
                    //If you would like to connect to server with SSL enabled with a self-signed
                    // certificate,
                    //please use getTokenSSLSelfsigned. And pass SSLContext and/or
                    // HostnameVerifier to join() by ConnectionOptions.
                    //For debug, if you would like to trust all certificates and hostnames,
                    //please use getTokenSSLINSECURE. And pass SSLContext and/or HostnameVerifier
                    // to join() by ConnectionOptions.
                    String tokenString = getTokenSSLINSECURE(basicServerString, roomId);
                    Log.d(TAG, "token is " + tokenString);
                    ConnectionOptions connectionOptions = new ConnectionOptions();
                    connectionOptions.setSslContext(sslContext);
                    connectionOptions.setHostnameVerifier(hostnameVerifier);
                    mRoom.join(tokenString, connectionOptions, new ActionCallback<User>() {

                        @Override
                        public void onSuccess(User myself) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callButton.setEnabled(true);
                                    callButton.setText("Start Video");
                                    connectButton.setEnabled(true);
                                    connectButton.setText("disconnect");
                                    externalButton.setEnabled(true);
                                    screenShareButton.setEnabled(true);
                                    statsText.setVisibility(View.VISIBLE);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "Room Connected",
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                            Log.d(TAG, "My client Id: " + myself.getId());

                            statsTimer = new Timer();
                            statsTimer.schedule(new TimerTask() {

                                @Override
                                public void run() {
                                    Message msg = roomHandler.obtainMessage();
                                    msg.what = STATUS_REMOTE;
                                    roomHandler.sendMessage(msg);
                                }
                            }, 1000, 3000);
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this,
                                                   e.getMessage(), Toast.LENGTH_SHORT).show();
                                    roomIdEditText.setEnabled(true);
                                    connectButton.setEnabled(true);
                                }
                            });
                        }
                    });
                    break;
                case MSG_PUBLISH:
                    try {
                        LocalCameraStreamParameters msp =
                                new LocalCameraStreamParameters(true, true, true);
                        msp.setResolution(640, 480);
                        msp.setCameraId(cameraID);
                        //To set the video frame filter.
                        //WoogeenBrightenFilter is a simple filter for brightening the image.
                        //LocalCameraStream.setFilter(WoogeenBrightenFilter.create(rootEglBase
                        // .getEglBaseContext()));
                        localStream = new LocalCameraStream(msp);
                        localStream.attach(localStreamRenderer);
                        localStreamRenderer.setMirror(LocalCameraStreamParameters
                                                    .getCameraList()[cameraID] == CameraType.FRONT);
                        PublishOptions option = new PublishOptions();
                        option.setMaximumVideoBandwidth(Integer.MAX_VALUE);
                        //Be careful when you set up the audio bandwidth, as different audio
                        // codecs require different minimum bandwidth.
                        option.setMaximumAudioBandwidth(Integer.MAX_VALUE);
                        option.setVideoCodec(VideoCodec.H264);
                        mRoom.publish(localStream, option, new ActionCallback<Void>() {

                            @Override
                            public void onSuccess(final Void result) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        localAudioButton.setEnabled(true);
                                        localVideoButton.setEnabled(true);
                                        callButton.setText("Stop Video");
                                        callButton.setEnabled(true);
                                        switchCameraButton.setEnabled(true);
                                        zoomControls.setIsZoomInEnabled(true);
                                        zoomControls.setIsZoomOutEnabled(true);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(final WoogeenException e) {
                                if (localStream != null) {
                                    localStream.close();
                                    localStreamRenderer.cleanFrame();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            localAudioButton.setEnabled(false);
                                            localVideoButton.setEnabled(false);
                                            callButton.setEnabled(true);
                                        }
                                    });
                                    localStream = null;
                                }
                                e.printStackTrace();
                            }

                        });
                    } catch (WoogeenException e) {
                        if (localStream != null) {
                            localStream.close();
                            localStreamRenderer.cleanFrame();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    localAudioButton.setEnabled(false);
                                    localVideoButton.setEnabled(false);
                                    callButton.setEnabled(true);
                                }
                            });
                            localStream = null;
                        }
                        e.printStackTrace();
                    }
                    break;
                case MSG_UNPUBLISH:
                    if (localStream != null) {
                        mRoom.unpublish(localStream, new ActionCallback<Void>() {

                            @Override
                            public void onSuccess(Void result) {
                                localStream.close();
                                localStream = null;
                                localStreamRenderer.cleanFrame();
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        callButton.setText("Start Video");
                                        callButton.setEnabled(true);
                                        localAudioButton.setEnabled(false);
                                        localVideoButton.setEnabled(false);
                                        localAudioButton.setText(
                                                getString(R.string.pauseLocalAudio));
                                        localVideoButton.setText(
                                                getString(R.string.pauseLocalVideo));
                                        switchCameraButton.setEnabled(false);
                                        zoomControls.setIsZoomInEnabled(false);
                                        zoomControls.setIsZoomOutEnabled(false);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(WoogeenException e) {
                                Log.d(TAG, e.getMessage());
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        callButton.setText("Start Video");
                                        callButton.setEnabled(true);
                                        localAudioButton.setEnabled(false);
                                        localVideoButton.setEnabled(false);
                                        localAudioButton.setText(
                                                getString(R.string.pauseLocalAudio));
                                        localVideoButton.setText(
                                                getString(R.string.pauseLocalVideo));
                                        switchCameraButton.setEnabled(false);
                                        switchCameraButton.setEnabled(false);
                                    }
                                });
                            }
                        });
                    }
                    break;
                case MSG_SUBSCRIBE:
                    SubscribeOptions option = new SubscribeOptions();
                    option.setVideoCodec(VideoCodec.H264);
                    RemoteStream remoteStream = (RemoteStream) msg.obj;
                    if (remoteStream instanceof RemoteMixedStream) {
                        if (((RemoteMixedStream) remoteStream).getSupportedResolutions()
                                                              .size() >= resolutionSelected + 1) {
                            option.setResolution(
                                    ((RemoteMixedStream) remoteStream).getSupportedResolutions()
                                                                      .get(resolutionSelected)
                                                                      .get("width"),
                                    ((RemoteMixedStream) remoteStream).getSupportedResolutions()
                                                                      .get(resolutionSelected)
                                                                      .get("height"));
                        }
                    }

                    mRoom.subscribe(remoteStream, option, new ActionCallback<RemoteStream>() {
                        @Override
                        public void onSuccess(final RemoteStream remoteStream) {
                            Log.d(TAG, "onStreamSubscribed");
                            try {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        remoteAudioButton.setEnabled(true);
                                        remoteVideoButton.setEnabled(true);
                                        startRecorderButton.setEnabled(true);
                                        stopRecorderButton.setEnabled(true);
                                        remoteStreamRenderer.setScalingType(
                                                RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                                        ViewTreeObserver vto = remoteStreamRenderer.getViewTreeObserver();
                                        ViewTreeObserver.OnGlobalLayoutListener listener =
                                                new ViewTreeObserver.OnGlobalLayoutListener() {
                                                    @Override
                                                    public void onGlobalLayout() {
                                                        remoteStreamRenderer.getViewTreeObserver()
                                                                .removeGlobalOnLayoutListener(this);
                                                        remoteStreamRenderer.setLayoutParams(
                                                                new LinearLayout.LayoutParams(
                                                                        remoteStreamRenderer
                                                                                .getMeasuredWidth(),
                                                                        remoteStreamRenderer
                                                                                .getMeasuredHeight()));
                                                    }
                                                };
                                        vto.addOnGlobalLayoutListener(listener);
                                        Log.d(TAG, "Subscribed stream: " + remoteStream.getId());
                                    }
                                });
                                subscribedStreams.add(remoteStream);
                                if (currentRemoteStream != null) {
                                    if (currentRemoteStream instanceof RemoteScreenStream) {
                                        mRoom.pauseVideo(remoteStream, null);
                                        return;
                                    }
                                    mRoom.pauseVideo(currentRemoteStream, null);
                                    currentRemoteStream.detach(remoteStreamRenderer);
                                }
                                currentRemoteStream = remoteStream;
                                currentRemoteStream.attach(remoteStreamRenderer);
                            } catch (WoogeenException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            e.printStackTrace();
                        }

                    });
                    break;
                case MSG_UNSUBSCRIBE:
                    final RemoteStream stream = (RemoteStream) msg.obj;
                    mRoom.unsubscribe(stream, new ActionCallback<Void>() {

                        @Override
                        public void onSuccess(Void result) {
                            subscribedStreams.remove(stream);
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            e.printStackTrace();
                        }

                    });
                    break;
                case MSG_ROOM_DISCONNECTED:
                    mRoom.leave(new ActionCallback<Void>() {

                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    roomIdEditText.setEnabled(true);
                                    callButton.setText("Start Video");
                                    callButton.setEnabled(false);
                                    connectButton.setText("Connect");
                                    startRecorderButton.setEnabled(false);
                                    stopRecorderButton.setEnabled(false);
                                    remoteAudioButton.setEnabled(false);
                                    remoteVideoButton.setEnabled(false);
                                    switchCameraButton.setEnabled(false);
                                    statsText.setVisibility(View.GONE);
                                    if (localStream != null) {
                                        localStream.close();
                                        localStream = null;
                                    }
                                    if (statsTimer != null) {
                                        statsTimer.cancel();
                                    }
                                    ratingBar.setRating(0);
                                }
                            });

                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            e.printStackTrace();
                        }

                    });
                    break;
                case MSG_PAUSEVIDEO:
                    //we only pause and play the mix and screen sharing stream in default.
                    mRoom.pauseVideo(currentRemoteStream, new ActionCallback<Void>() {

                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    remoteVideoButton.setText(getString(R.string.startVideo));
                                    remoteVideoButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "pause video success", Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this,
                                                   "pause video failed " + e.getMessage(),
                                                   Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                    });
                    break;
                case MSG_PLAYVIDEO:
                    //we only pause and play the mix stream in default.
                    mRoom.playVideo(currentRemoteStream, new ActionCallback<Void>() {

                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    remoteVideoButton.setText(getString(R.string.pauseVideo));
                                    remoteVideoButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "play video success", Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this,
                                                   "play video failed " + e.getMessage(),
                                                   Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                    });
                    break;
                case MSG_PAUSEAUDIO:
                    //we only pause and play the mix stream in default.
                    mRoom.pauseAudio(currentRemoteStream, new ActionCallback<Void>() {

                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    remoteAudioButton.setText(getString(R.string.startAudio));
                                    remoteAudioButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "pause audio success", Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this,
                                                   "pause audio failed " + e.getMessage(),
                                                   Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                    });
                    break;
                case MSG_PLAYAUDIO:
                    //we only pause and play the mix stream in default.
                    mRoom.playAudio(currentRemoteStream, new ActionCallback<Void>() {

                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    remoteAudioButton.setText(getString(R.string.pauseAudio));
                                    remoteAudioButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "play audio success", Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this,
                                                   "play audio failed " + e.getMessage(),
                                                   Toast.LENGTH_SHORT)
                                         .show();
                                }
                            });
                        }

                    });
                    break;
                case MSG_SWITCHCAMERA:
                    if (localStream == null) {
                        return;
                    }
                    localStream.switchCamera(new ActionCallback<Boolean>() {

                        @Override
                        public void onSuccess(final Boolean isFrontCamera) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    switchCameraButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "Switch to " + (isFrontCamera ? "front"
                                                                                 : "back") + " "
                                                           + "camera.",
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                            localStreamRenderer.setMirror(isFrontCamera);
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    switchCameraButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "Failed to switch camera. " + e
                                                           .getLocalizedMessage(),
                                                   Toast.LENGTH_SHORT).show();
                                }

                            });
                        }

                    });
                    break;
                case MSG_START_RECORDER:
                    if (currentRemoteStream == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                startRecorderButton.setEnabled(true);
                                Toast.makeText(WooGeenActivity.this,
                                               "No remote stream to be recorded",
                                               Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        RecordOptions startOpt = new RecordOptions();
                        startOpt.setVideoStreamId(currentRemoteStream.getId());
                        startOpt.setAudioStreamId(currentRemoteStream.getId());
                        mRoom.startRecorder(startOpt, new ActionCallback<RecordAck>() {

                            @Override
                            public void onSuccess(final RecordAck ack) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        startRecorderButton.setEnabled(false);
                                        stopRecorderButton.setEnabled(true);
                                        WooGeenActivity.this.recorderId = ack.getRecorderId();
                                        Toast.makeText(WooGeenActivity.this,
                                                       getString(R.string.startRecorder),
                                                       Toast.LENGTH_SHORT).show();
                                    }
                                });
                                Log.d(TAG, "Started recorder, location: "
                                        + ack.getPath() + "id: " + ack.getRecorderId());
                            }

                            @Override
                            public void onFailure(WoogeenException e) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(WooGeenActivity.this,
                                                       "Start recorder failed",
                                                       Toast.LENGTH_SHORT).show();
                                    }
                                });
                                Log.w(TAG, "Started record failed, exception: "
                                        + e.getLocalizedMessage());
                            }

                        });
                    }
                    break;
                case MSG_STOP_RECORDER:
                    RecordOptions stopOpt = new RecordOptions();
                    stopOpt.setRecorderId(WooGeenActivity.this.recorderId);
                    mRoom.stopRecorder(stopOpt, new ActionCallback<RecordAck>() {

                        @Override
                        public void onSuccess(RecordAck ack) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    startRecorderButton.setEnabled(true);
                                    stopRecorderButton.setEnabled(false);
                                    Toast.makeText(WooGeenActivity.this,
                                                   getString(R.string.stopRecorder),
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });

                            Log.d(TAG, "Stopped recorder, id: " + ack.getRecorderId());
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this, "Stop recorder failed",
                                                   Toast.LENGTH_SHORT).show();

                                }
                            });
                            Log.w(TAG, "Stopped record failed, exception: "
                                    + e.getLocalizedMessage());
                        }

                    });
                    break;
                case MSG_SEND_MSG:
                    String messageString = localMessageEditText.getText().toString();
                    mRoom.send(messageString, new ActionCallback<Void>() {
                        @Override
                        public void onFailure(WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this, "Sent message failed.",
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    localMessageEditText.setText("");
                                    Toast.makeText(WooGeenActivity.this, "Sent message.",
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                    break;
                case MSG_START_EXTERNAL:
                    ExternalOutputOptions options = new ExternalOutputOptions();
                    Log.d(TAG,
                          "External Output Server: " + externalServerEditText.getText().toString());
                    ActionCallback<ExternalOutputAck> callback =
                            new ActionCallback<ExternalOutputAck>() {
                                @Override
                                public void onSuccess(ExternalOutputAck result) {
                                    externalOutputId = result.getUrl();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(WooGeenActivity.this,
                                                           "Start external output" +
                                                                   " succeed!",
                                                           Toast.LENGTH_SHORT).show();
                                            externalButton.setEnabled(true);
                                            externalButton.setText("Stop");
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(final WoogeenException e) {
                                    e.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            externalButton.setEnabled(true);
                                            Toast.makeText(WooGeenActivity.this,
                                                           "Start external output"
                                                                   + " failed: " + e.getMessage(),
                                                           Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            };
                    mRoom.addExternalOutput(externalServerEditText.getText().toString(), callback);
                    break;
                case MSG_STOP_EXTERNAL:
                    if (externalOutputId == null) {
                        return;
                    }
                    mRoom.removeExternalOutput(externalOutputId, new ActionCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    externalButton.setEnabled(true);
                                    externalButton.setText("Start");
                                    Toast.makeText(WooGeenActivity.this,
                                                   "Stop external output succeed!",
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            Log.d(TAG, e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    externalButton.setEnabled(true);
                                    Toast.makeText(WooGeenActivity.this,
                                                   "Stop external output failed!" + e.getMessage(),
                                                   Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                    break;
                case STATUS_REMOTE:
                    ActionCallback<ConnectionStats> statsCallback = new ActionCallback<ConnectionStats>() {
                        @Override
                        public void onSuccess(final ConnectionStats result) {
                            List<ConnectionStats.MediaTrackStats> trackStatsList =
                                    result.mediaTracksStatsList;
                            for (ConnectionStats.MediaTrackStats trackStats : trackStatsList) {
                                if (trackStats instanceof ConnectionStats.VideoReceiverMediaTrackStats) {
                                    ConnectionStats.VideoReceiverMediaTrackStats videoStats =
                                            (ConnectionStats.VideoReceiverMediaTrackStats) trackStats;
                                    stsList.put("ReceiveCodeName", videoStats.codecName);
                                    stsList.put("PacketsReceived", videoStats.packetsReceived + "");
                                    stsList.put("ReceivePacketLost", videoStats.packetsLost + "");
                                    stsList.put("FrameRateReceived", videoStats.frameRateReceived + "");
                                    stsList.put("frameResolutionReceived",
                                                videoStats.frameWidthReceived + "*"
                                                        + videoStats.frameHeightReceived);
                                    final double byteRate = ((double)
                                            (videoStats.bytesReceived - lastSubscribeByteReceived) / interval);
                                    lastSubscribeByteReceived = videoStats.bytesReceived;
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            float rate;
                                            rate = (float) ((byteRate / 20 > 5) ? 5 : byteRate / 20);
                                            ratingBar.setRating(rate);
                                        }
                                    });
                                    break;
                                }
                            }
                            Message msg = roomHandler.obtainMessage();
                            msg.what = STATUS_LOCAL;
                            roomHandler.sendMessage(msg);
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            stsList.put("ReceiveCodeName", "--");
                            stsList.put("PacketsReceived", "--");
                            stsList.put("ReceivePacketLost", "--");
                            stsList.put("FrameRateReceived", "--");
                            stsList.put("ReceiveBandwidth", "--");
                            stsList.put("frameResolutionReceived", "--");
                            Message msg = roomHandler.obtainMessage();
                            msg.what = STATUS_LOCAL;
                            roomHandler.sendMessage(msg);
                        }
                    };
                    mRoom.getConnectionStats(currentRemoteStream, statsCallback);
                    break;
                case STATUS_LOCAL:
                    mRoom.getConnectionStats(localStream, new ActionCallback<ConnectionStats>() {
                        @Override
                        public void onSuccess(ConnectionStats result) {
                            List<ConnectionStats.MediaTrackStats> trackStatsList =
                                    result.mediaTracksStatsList;
                            for (ConnectionStats.MediaTrackStats trackStats : trackStatsList) {
                                if (trackStats instanceof ConnectionStats.VideoSenderMediaTrackStats) {
                                    ConnectionStats.VideoSenderMediaTrackStats videoStats =
                                            (ConnectionStats.VideoSenderMediaTrackStats) trackStats;
                                    stsList.put("SendCodeName", videoStats.codecName);
                                    stsList.put("PacketsSend", videoStats.packetsSent + "");
                                    stsList.put("SendPacketLost", videoStats.packetsLost + "");
                                    stsList.put("FrameRateSend", videoStats.frameRateSent + "");
                                    stsList.put("frameResolutionSend",
                                                videoStats.frameWidthSent
                                                        + "*" + videoStats.frameHeightSent);
                                    break;
                                }
                            }
                            updateStats();
                        }

                        @Override
                        public void onFailure(WoogeenException e) {
                            stsList.put("SendCodeName", "--");
                            stsList.put("PacketsSend", "--");
                            stsList.put("SendPacketLost", "--");
                            stsList.put("FrameRateSend", "--");
                            stsList.put("SendBandwidth", "--");
                            stsList.put("frameResolutionSend", "--");
                            updateStats();
                        }
                    });
                    break;
                case MSG_START_SHARESCREEN:
                    if (screenStream == null) {
                        return;
                    }
                    mRoom.publish(screenStream, new ActionCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this, "Succeed to share screen",
                                                   Toast.LENGTH_SHORT).show();
                                    screenShareButton.setText(R.string.stopShareScreen);
                                    screenShareButton.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onFailure(final WoogeenException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(WooGeenActivity.this,
                                                   "Failed to share screen" + e.getMessage(),
                                                   Toast.LENGTH_SHORT).show();
                                    screenShareButton.setEnabled(true);
                                }
                            });
                        }
                    });
                    break;
                case MSG_STOP_SHARESCREEN:
                    if (screenStream == null) {
                        return;
                    }
                    mRoom.unpublish(screenStream, new ActionCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    screenShareButton.setText(R.string.startShareScreen);
                                    screenShareButton.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onFailure(WoogeenException e) {

                        }
                    });
                    screenStream.close();
                    screenStream = null;
                    break;
            }
            super.handleMessage(msg);
        }
    }

    public void updateStats() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statsStr.append("==Receive==").append("\n");
                statsStr.append("CodecName:").append(stsList.get("ReceiveCodeName")).append("\n");
                statsStr.append("resolution:")
                        .append(stsList.get("frameResolutionReceived"))
                        .append("\n");
                statsStr.append("Packets:").append(stsList.get("PacketsReceived")).append("\n");
                statsStr.append("PacketLost:")
                        .append(stsList.get("ReceivePacketLost"))
                        .append("\n");
                statsStr.append("FrameRate:")
                        .append(stsList.get("FrameRateReceived"))
                        .append("\n\n");
                statsStr.append("==Send==").append("\n");
                statsStr.append("CodeName:").append(stsList.get("SendCodeName")).append("\n");
                statsStr.append("resolution:")
                        .append(stsList.get("frameResolutionSend"))
                        .append("\n");
                statsStr.append("Packets:").append(stsList.get("PacketsSend")).append("\n");
                statsStr.append("PacketLost:").append(stsList.get("SendPacketLost")).append("\n");
                statsStr.append("FrameRate:").append(stsList.get("FrameRateSend"));

                statsText.setText(statsStr);
                statsStr.setLength(0);
            }
        });
    }

    private class WiredHeadsetReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra("state", 0);
            AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
            audioManager.setSpeakerphoneOn(state == 0);
        }
    }

    public class RemoteViewOnTouchListener implements View.OnTouchListener {
        long firClick;
        long secClick;
        int firstX, firstY;
        int secondX, secondY;
        private static final int DOUBLE_TAP_SLOP = 2;
        private static final int DOUBLE_TAP_TIMEOUT = 300;
        private int mDoubleTapSlopSquare = DOUBLE_TAP_SLOP * DOUBLE_TAP_SLOP;

        RemoteViewOnTouchListener(Context context) {
            final ViewConfiguration configuration = ViewConfiguration.get(context);
            int doubleTapSlop = configuration.getScaledDoubleTapSlop();
            mDoubleTapSlopSquare = doubleTapSlop * doubleTapSlop;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                firClick = secClick;
                secClick = event.getEventTime();
                firstX = secondX;
                secondX = (int) event.getX();
                firstY = secondY;
                secondY = (int) event.getY();
                if (secClick - firClick > DOUBLE_TAP_TIMEOUT) {
                    return false;
                }
                int deltaX = secondX - firstX;
                int deltaY = secondY - firstY;
                if (deltaX * deltaX + deltaY * deltaY < mDoubleTapSlopSquare) {
                    onDoubleTap(v, event);
                }
            }
            return false;
        }

        private String findStreamID(List<Region> currentRegions, double x, double y) {
            int i;
            boolean found = false;
            for (i = currentRegions.size() - 1; i >= 0; i--) {
                double top = currentRegions.get(i).getTop();
                double left = currentRegions.get(i).getLeft();
                double relativesize = currentRegions.get(i).getRelativeSize();
                if ((x < left + relativesize) && (y < top + relativesize) && (x > left) && (y > top)) {
                    found = true;
                    break;
                }
            }
            return found ? currentRegions.get(i).getStreamId() : null;
        }

        private boolean onDoubleTap(View v, MotionEvent event) {
            if (currentRemoteStream instanceof RemoteCameraStream) {
                for (final RemoteStream stream : subscribedStreams) {
                    if (stream instanceof RemoteMixedStream) {
                        try {
                            RemoteStream previousStream = currentRemoteStream;
                            currentRemoteStream = stream;
                            mRoom.playVideo(currentRemoteStream, null);
                            currentRemoteStream.attach(remoteStreamRenderer);

                            Message msg = new Message();
                            msg.what = MSG_UNSUBSCRIBE;
                            msg.obj = previousStream;
                            roomHandler.sendMessage(msg);
                            previousStream.detach();
                        } catch (WoogeenException e) {
                            e.getStackTrace();
                        }
                        break;
                    }
                }
            } else if (currentRemoteStream instanceof RemoteMixedStream) {
                int streamNumber = mRoom.getRemoteStreams().size() - 1;
                List<Region> currentRegions = ((RemoteMixedStream) currentRemoteStream)
                        .getCurrentRegions();
                if (currentRegions == null) {
                    return false;
                }
                final String streamID;
                streamID = findStreamID(currentRegions, (double) event.getX() / v.getWidth(),
                                        (double) event.getY() / v.getHeight());
                Log.d(TAG, "Find streamID: " + streamID);
                boolean found = false;
                if (streamID != null) {
                    if (localStream != null && localStream.getId().equals(streamID)) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(WooGeenActivity.this,
                                               "You can not subscribe yourself!",
                                               Toast.LENGTH_SHORT)
                                     .show();
                            }
                        });
                        return false;
                    }
                    for (int i = 0; i < mRoom.getRemoteStreams().size(); i++) {
                        RemoteStream remoteStream = mRoom.getRemoteStreams().get(i);
                        if (remoteStream instanceof RemoteCameraStream
                                && remoteStream.getId().equals(streamID)) {
                            Message msg = new Message();
                            msg.what = MSG_SUBSCRIBE;
                            msg.obj = remoteStream;
                            roomHandler.sendMessage(msg);
                        }
                    }
                }
            }
            return true;
        }
    }
}
