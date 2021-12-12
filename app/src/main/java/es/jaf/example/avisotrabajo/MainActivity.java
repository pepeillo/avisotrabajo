package es.jaf.example.avisotrabajo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import es.jaf.example.avisotrabajo.BuildConfig;
import es.jaf.example.avisotrabajo.R;

public class MainActivity extends Activity {
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_CONNECTED = 4;
    public static final int MESSAGE_FAILED = 5;
    public static final int MESSAGE_DISCONNECTED = 6;
    public static final int MESSAGE_BROKEN = 7;
    private OverlayShowingService mService = null;
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            OverlayShowingService.LocalBinder binder = (OverlayShowingService.LocalBinder) service;
            mService = binder.getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        CheckBlueToothState();

        getPrefs();

        findViewById(R.id.cmdStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                savePrefs();
                if (mService == null) {
                    connect();
                }
                moveTaskToBack(true);
            }
        });

        findViewById(R.id.cmdStop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mService == null) {
                    exit();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setTitle(R.string.closing)
                            .setMessage(R.string.closing_sure)
                            .setNegativeButton(android.R.string.no, null)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    exit();
                                }
                            })
                            .show();
                }
            }
        });

        findViewById(R.id.opcNotif).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                View watch = findViewById(R.id.opcNotifWatch);
                if (((CheckBox)view).isChecked()) {
                    watch.setEnabled(true);
                }else {
                    watch.setEnabled(false);
                    ((CheckBox)watch).setChecked(false);
                }
            }
        });
	}

    @Override
    protected void onResume() {
        super.onResume();
        GlobalApplication.setCurrentActivity(this);
        refreshImage();
    }

    void refreshImage(){
        try {
            int c = GlobalApplication.getIconId();
            ImageView imgColor = findViewById(R.id.imgColor);
            if (c == 0) {
                findViewById(R.id.cmdStart).setEnabled(true);
                imgColor.setImageDrawable(null);
            } else {
                findViewById(R.id.cmdStart).setEnabled(false);
                imgColor.setImageResource(c);
            }
        } catch (Exception e) {
            GlobalApplication.saveException("Error en refreshImage", e);
        }
    }

    @Override
    protected void onPause() {
        clearReferences();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        clearReferences();
        super.onDestroy();
    }

    @Override
	public void onBackPressed() {
	    try {
	        savePrefs();
            if (mService == null) {
                exit();
                return;
            }
            moveTaskToBack(false);
        } catch (Exception e) {
            GlobalApplication.saveException("Error en onBackPressed", e);
        }
	}

    private void clearReferences(){
        Activity currActivity = GlobalApplication.getCurrentActivity();
        if (this.equals(currActivity))
            GlobalApplication.setCurrentActivity(null);
    }

    private void CheckBlueToothState(){
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            String msg = null;
            if (bluetoothAdapter == null) {
                msg = getString(R.string.bt_not_supported);
            } else {
                if (bluetoothAdapter.isEnabled()) {
                    if (bluetoothAdapter.isDiscovering()) {
                        msg = getString(R.string.bt_discovering);
                    }
                } else {
                    msg = getString(R.string.bt_disabled);
                }
            }
            if (msg != null) {
                new AlertDialog.Builder(MainActivity.this)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.app_name)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                exit();
                            }
                        })
                        .show();
            }
        } catch (Exception e) {
            GlobalApplication.saveException("Error en CheckBlueToothState", e);
        }
    }

    private void exit() {
        if (mService != null) {
            unbindService(mConnection);
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            try {
                notificationManager.cancel(GlobalApplication.getRandom());
            } catch (Exception e) {/**/}
        }

        finish();
        System.exit(0);
	}

 	private void connect() {
	    try {
            Intent intent = new Intent(this, OverlayShowingService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            GlobalApplication.saveException("Error en connect", e);
        }
    }

    private void getPrefs() {
	    try {
            SharedPreferences prefs = getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE);
            int opc = prefs.getInt("ACTION", OverlayShowingService.ACTION_SOUND
                    + OverlayShowingService.ACTION_MESSAGE
                    + OverlayShowingService.ACTION_NOTIFICATION
                    + OverlayShowingService.ACTION_NOTIFICATION_WATCH);
            ((CheckBox) findViewById(R.id.opcSound)).setChecked((opc & OverlayShowingService.ACTION_SOUND) == OverlayShowingService.ACTION_SOUND);
            ((CheckBox) findViewById(R.id.opcMessage)).setChecked((opc & OverlayShowingService.ACTION_MESSAGE) == OverlayShowingService.ACTION_MESSAGE);
            boolean notif = (opc & OverlayShowingService.ACTION_NOTIFICATION) == OverlayShowingService.ACTION_NOTIFICATION;
            ((CheckBox) findViewById(R.id.opcNotif)).setChecked(notif);
            findViewById(R.id.opcNotifWatch).setEnabled(notif);
            if (notif) {
                ((CheckBox) findViewById(R.id.opcNotifWatch)).setChecked((opc & OverlayShowingService.ACTION_NOTIFICATION_WATCH) == OverlayShowingService.ACTION_NOTIFICATION_WATCH);
            } else {
                ((CheckBox) findViewById(R.id.opcNotifWatch)).setChecked(false);
            }

        } catch (Exception e) {
            GlobalApplication.saveException("Error en getPrefs", e);
        }
    }

    private void savePrefs() {
	    try {

            int action = ( ((CheckBox)findViewById(R.id.opcSound)).isChecked() ?
                    OverlayShowingService.ACTION_SOUND : 0) +
                    ( ((CheckBox)findViewById(R.id.opcMessage)).isChecked() ?
                            OverlayShowingService.ACTION_MESSAGE : 0) +
                    ( ((CheckBox)findViewById(R.id.opcNotif)).isChecked() ?
                            OverlayShowingService.ACTION_NOTIFICATION : 0) +
                    ( ((CheckBox)findViewById(R.id.opcNotifWatch)).isChecked() ?
                            OverlayShowingService.ACTION_NOTIFICATION_WATCH : 0);
            SharedPreferences.Editor editor = getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE).edit();
            editor.putInt("ACTION", action);

            editor.apply();
            editor.commit();
        } catch (Exception e) {
            GlobalApplication.saveException("Error en savePrefs", e);
        }
    }
}
