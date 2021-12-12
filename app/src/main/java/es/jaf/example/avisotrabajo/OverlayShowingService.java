package es.jaf.example.avisotrabajo;

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class OverlayShowingService extends Service{
    public static final int ACTION_SOUND = 1;
    public static final int ACTION_MESSAGE = 2;
    public static final int ACTION_NOTIFICATION = 4;
    public static final int ACTION_NOTIFICATION_WATCH = 8;
    public static final String CHANNEL_ID = "es.jaf.example.avisotrabajo.channel";

    private ChatService mChatService;
    private Timer timerObj;
    private TimerTask timerTaskObj;
    private final Boolean checker = false;
    private AlertDialog dialog = null;
    private BroadcastReceiver btReceiver;

    private final IBinder binder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    class LocalBinder extends Binder {
        OverlayShowingService getService() {
            // Return this instance of LocalService so clients can call public methods
            return OverlayShowingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);

                    switch(state){
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            mChatService.stop();
                            disableTimer();
                            break;
                        case BluetoothAdapter.STATE_ON:
                            enableTimer();
                            break;
                    }
                } catch (Exception e) {
                    GlobalApplication.saveException("Error en BroadcastReceiver", e);
                }
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(intent != null) {
            try {
                startForegroundService();
            } catch (Exception e) {
                GlobalApplication.saveException("Error en onStartCommand", e);
            }
        }
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopForegroundService();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        removeNotification();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        removeNotification();
        super.onTaskRemoved(rootIntent);
    }

    private void startForegroundService() {
        GlobalApplication.setIconId(R.mipmap.ic_launcher_round_red);
        try {
            if (mChatService == null) {
                mChatService = new ChatService( new MyHandler(this));
                mChatService.start();
            }
            try {
                unregisterReceiver(btReceiver);
            } catch (Exception ex) {/**/}

            registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        } catch (Exception e) {
            GlobalApplication.saveException("Error en startForegroundService", e);
        }
    }

    private void stopForegroundService() {
        try {
            try {
                unregisterReceiver(btReceiver);
            } catch (Exception ex) {/**/}

            if (mChatService != null) {
                mChatService.stop();
                mChatService = null;
            }

            stopSelf();
        } catch (Exception e) {
            GlobalApplication.saveException("Error en stopForegroundService", e);
        }
    }

    private void enableTimer() {
        disableTimer();

        synchronized (checker) {
            try {
                timerObj = new Timer();
                timerTaskObj = new TimerTask() {
                    public void run() {
                        if (mChatService.isConnected()) {
                            disableTimer();
                        } else {
                            mChatService.start();
                        }
                    }
                };
                timerObj.schedule(timerTaskObj, 5000);
            } catch (Exception e) {
                GlobalApplication.saveException("Error en enableTimer", e);
            }
        }
    }

    private void disableTimer() {
        synchronized (checker) {
            try {
                if (timerObj != null) {
                    timerObj.cancel();
                    timerObj.purge();
                }
                if (timerTaskObj != null) {
                    timerTaskObj.cancel();
                }
            } catch (Exception e) {
                GlobalApplication.saveException("Error en disableTimer", e);
            } finally {
                timerObj = null;
                timerTaskObj = null;
            }
        }
    }

    boolean isTimeToNotify() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        int t = h * 100 + m;

        return (t > 730 && t < 2000);
    }

    private void showMsg(boolean connected) {
        String text = getString(connected ? R.string.connected : R.string.disconnected);
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch(Exception e) {/**/}
        }

        SharedPreferences prefs = getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE);
        int receiverAction = prefs.getInt("ACTION", 3);

        vibrate();

        if ((receiverAction & ACTION_MESSAGE) == ACTION_MESSAGE || !isTimeToNotify()) {
            GlobalApplication.showMessage(dialog, text , isTimeToNotify());
        }

        if ((receiverAction & ACTION_SOUND) == ACTION_SOUND) {
            soundMe();
        }
        if ((receiverAction & ACTION_NOTIFICATION) == ACTION_NOTIFICATION) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notifyMe(notificationManager, connected, text, (receiverAction & ACTION_NOTIFICATION_WATCH) == ACTION_NOTIFICATION_WATCH);
            }
        }
    }

    private void vibrate() {
        if (!isTimeToNotify()) {
            return;
        }

        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= 26) {//{Build.VERSION_CODES.O
                    v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {//deprecated in API 26
                    v.vibrate(1000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void soundMe() {
        if (!isTimeToNotify()) {
            return;
        }

        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            RingtoneManager.getRingtone(getApplicationContext(), notification).play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyMe(NotificationManager notificationManager, boolean connected, String text, boolean watchMode) {
        int random = GlobalApplication.getRandom();
        if (notificationManager == null || !isTimeToNotify()) {
            return;
        }
        try {
            notificationManager.cancel(random);
        } catch (Exception e) {/**/}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createChannels();
            }
            Notification.Builder mBuilder = new Notification.Builder(this, "es.jaf.example.avisotrabajo")
                    .setSmallIcon(connected ? R.mipmap.ic_launcher_round_green : R.mipmap.ic_launcher_round_red)
                    .setContentText(text)
                    .setVibrate(new long[]{0L})
                    .setAutoCancel(true);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBuilder.setColor(getResources().getColor(connected ? android.R.color.holo_green_dark : android.R.color.darker_gray));
            }
            notificationManager.notify(random, mBuilder.build());

            if (watchMode) {
                removeNotification();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            try {
                notificationManager.cancel(GlobalApplication.getRandom());
            } catch (Exception e) {/**/}
        }
    }

    public void createChannels() {
        NotificationChannel androidChannel = new NotificationChannel(CHANNEL_ID,
                "es.jaf.example.avisotrabajo", NotificationManager.IMPORTANCE_DEFAULT);
        androidChannel.enableLights(true);
        androidChannel.enableVibration(true);
        androidChannel.setLightColor(Color.GREEN);
        androidChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(androidChannel);
        }
    }

    public void handleMessage( Message msg) {
        try {
            switch (msg.what) {
                case MainActivity.MESSAGE_CONNECTED:
                    disableTimer();
                    GlobalApplication.setIconId(R.mipmap.ic_launcher_round_green);
                    showMsg(true);
                    break;
                case MainActivity.MESSAGE_DISCONNECTED:
                case MainActivity.MESSAGE_BROKEN:
                    if (timerObj == null ) {
                        if (msg.what == MainActivity.MESSAGE_DISCONNECTED) {
                            GlobalApplication.setIconId(R.mipmap.ic_launcher_round_red);
                            showMsg(false);
                        }
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (BluetoothAdapter.getDefaultAdapter().getState() == BluetoothAdapter.STATE_ON) {
                                    enableTimer();
                                }
                            }
                        }, 3000);
                    }
                    break;
                case MainActivity.MESSAGE_FAILED:
                    GlobalApplication.setIconId(R.mipmap.ic_launcher_round_red);
                    if (timerObj == null ) {
                        enableTimer();
                    }
                    break;
            }
        } catch (Exception e) {
            GlobalApplication.saveException("Error en handleMessage", e);
        }
    }
}