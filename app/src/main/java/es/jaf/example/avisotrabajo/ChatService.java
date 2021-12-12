package es.jaf.example.avisotrabajo;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

class ChatService {
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-cafa-11de-8a39-0800200c9a66");

    // Member fields
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private AcceptThread acceptThread;
    private int state;

    // Constants that indicate the current connection state
    private static final int STATE_NONE = 0;
    private static final int STATE_LISTEN = 1; // listening connection
    private static final int STATE_CONNECTING = 2; // initiate outgoing
    private static final int STATE_CONNECTED = 3; // connected to remote device
    private ConnectedThread connectedThread;

    ChatService(Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        this.handler = handler;
    }

    // Set the current state of the chat connection
    private synchronized void setState(int state) {
        try {
            this.state = state;
            handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        } catch (Exception e) {
            GlobalApplication.saveException("Error en setState", e);
        }
    }

    synchronized boolean isConnected() {
        return (state == STATE_CONNECTED);
    }

    // start service
    synchronized void start() {
        try {
            // Cancel any running thread
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }

            setState(STATE_LISTEN);

            // Start the thread to listen on a BluetoothServerSocket
            if (acceptThread == null) {
                acceptThread = new AcceptThread();
                acceptThread.start();
            }
        } catch (Exception e) {
            GlobalApplication.saveException("Error en ChatService.start()", e);
        }
    }

    // manage Bluetooth connection
    private synchronized void connected(BluetoothSocket socket) {
        try {
            // Cancel running thread
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }

            if (acceptThread != null) {
                acceptThread.cancel();
                acceptThread = null;
            }

            // Start the thread to manage the connection and perform transmissions
            connectedThread = new ConnectedThread(socket);
            connectedThread.start();

            // Send the name of the connected device back to the UI Activity
            Message msg = handler.obtainMessage(MainActivity.MESSAGE_CONNECTED);
            handler.sendMessage(msg);

            setState(STATE_CONNECTED);
        } catch (Exception e) {
            GlobalApplication.saveException("Error en ChatService.connected()", e);
        }
    }

    // stop all threads
    synchronized void stop() {
        try {
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }

            if (acceptThread != null) {
                acceptThread.cancel();
                acceptThread = null;
            }

            setState(STATE_NONE);
        } catch (Exception e) {
            GlobalApplication.saveException("Error en ChatService.stop()", e);
        }
    }

    private void connectionBroken() {
        try {
            ChatService.this.stop();

            setState(STATE_CONNECTING);
            Message msg = handler.obtainMessage(MainActivity.MESSAGE_BROKEN);
            handler.sendMessage(msg);
        } catch (Exception e) {
            GlobalApplication.saveException("Error en ChatService.connectionBroken()", e);
        }
    }

    private void connectionLost() {
        try {
            ChatService.this.stop();

            setState(STATE_CONNECTING);
            Message msg = handler.obtainMessage(MainActivity.MESSAGE_DISCONNECTED);
            handler.sendMessage(msg);
        } catch (Exception e) {
            GlobalApplication.saveException("Error en ChatService.connectionLost()", e);
        }
    }

    // runs while listening for incoming connections
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket = null;

        AcceptThread() {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord( NAME_SECURE, MY_UUID_SECURE);
            } catch (Exception e) {/**/}
        }

        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (state != STATE_CONNECTED) {
                try {
                    socket = serverSocket.accept();
                } catch (Exception e) {
                    GlobalApplication.saveException("AcceptThread.run() error", e);
                    break;
                }

                try {
                    // If a connection was accepted
                    if (socket == null) {
                        cancel();
                    } else {
                        synchronized (ChatService.this) {
                            switch (state) {
                                case STATE_LISTEN:
                                case STATE_CONNECTING: // start the connected thread.
                                    connected(socket);
                                    break;
                                case STATE_NONE:
                                case STATE_CONNECTED: // Either not ready or already connected. Terminate new socket.
                                    try {
                                        socket.close();
                                    } catch (Exception e) {/**/}
                                    break;
                            }
                        }
                    }
                } catch (Exception e) {
                    GlobalApplication.saveException("AcceptThread.run2() error", e);
                }
            }
        }

        void cancel() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception e) {/**/}
            }
        }
    }

    // runs during a connection with a remote device
    private class ConnectedThread extends Thread {
        private BluetoothSocket bluetoothSocket;
        private InputStream inputStream = null;

        ConnectedThread(BluetoothSocket bluetoothSocket) {
            this.bluetoothSocket = bluetoothSocket;

            try {
                inputStream = bluetoothSocket.getInputStream();
            } catch (Exception e) {/**/}
        }

        public void run() {
            try {
                inputStream.read();
            } catch (Exception e) {
                //GlobalApplication.writeToSDFile("AcceptThread.read error", e);
                if (e.getMessage().toLowerCase().contains("software caused connection abort")) {
                    connectionBroken();
                    return;
                }
            } finally {
                cancel();
            }
            connectionLost();
        }

        void cancel() {
            if (inputStream != null) {
                try {
                    inputStream.close();
                    inputStream = null;
                } catch (IOException e) {/**/}
            }
            if (bluetoothSocket != null) {
                try {
                    bluetoothSocket.close();
                    bluetoothSocket = null;
                } catch (IOException e) {/**/}
            }
        }
    }
}
