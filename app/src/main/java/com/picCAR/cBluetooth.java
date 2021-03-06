/**
 *  Class for Bluetooth Remote Control
 *  @version 1.0
 *  23.09.2013
 *  Gregor Schlechtriem 
 *  www.pikoder.com
 */

package com.picCAR;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.String;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */

public class cBluetooth {
    // Debugging
    public static String TAG = cBluetooth.class.getSimpleName();  // used in other modules to indicate bluetooth error conditions
    private static final boolean D = true;
    final Handler toastHandler = new Handler();

    // SPP UUID service
	private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    // statuses for Handler - inherited from Cxem car original library 
    public final static int BL_NOT_AVAILABLE = 1;        	// Bluetooth is not available
    public final static int BL_INCORRECT_ADDRESS = 2;		// incorrect MAC-address 
    public final static int BL_REQUEST_ENABLE = 3;			// request enable Bluetooth 
    public final static int BL_SOCKET_FAILED = 4;			// socket error 
    public final static int RECEIVE_MESSAGE = 5;			// receive message 
    public final static int USER_STOP_INITIATED = 6;        // user hit back button - shutting down
    public final static int BL_DEVICE_NOT_FOUND = 7;        // device not found in list of paired devices

    /**
     * Constructor. Prepares a new Bluetooth RFcomm connection to receiver
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public cBluetooth(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        if (mAdapter == null) {
            Log.i(TAG, "Bluetooth is not available. Exit");
            mHandler.sendEmptyMessage(BL_NOT_AVAILABLE);
        } else {
            if (mAdapter.isEnabled()) {
                Log.i(TAG, "Bluetooth adapter is enabled");
            } else {
                Log.i(TAG, "Request Bluetooth Enable");
                mHandler.sendEmptyMessage(BL_REQUEST_ENABLE);
            }
        }
    }


    /*
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }


    /**
     * if BT adapter would be available but disabled then the process for activating is initiated
     * @param
     */
/*
    public void checkBTState() {
    	if(mAdapter == null) { 
     		mHandler.sendEmptyMessage(BL_NOT_AVAILABLE);
    	} else {
    		if (mAdapter.isEnabled()) {
    			Log.i(TAG, "Bluetooth adapter is enabled");
    		} else {
    			mHandler.sendEmptyMessage(BL_REQUEST_ENABLE);
    		}
    	}
	}
*/
    public void BT_onPause() {
    	Log.d(TAG, "...On Pause...");
        /*
        if (outStream != null) {
    		try {
    	        outStream.flush();
    	    } catch (IOException e) {
	        	Log.e(TAG, "In onPause() and failed to flush output stream: " + e.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
    	    }
    	}

    	if (btSocket != null) {
	    	try {
	    		btSocket.close();
	    	} catch (IOException e2) {
	        	Log.e(TAG, "In onPause() and failed to close socket." + e2.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
	    	}
    	}
    	*/
        mHandler.sendEmptyMessage(USER_STOP_INITIATED);
    	stop();
     }

    
    /**
     * Start the service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
    */
    /*
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
        	mConnectThread.cancel(); 
        	mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }

        setState(STATE_NONE);
    }
    */

    /**
     * Start the ConnectThread to initiate a connection to a remote device. Called by the
     * Activity onResume()
     * @param BT_DeviceName  The BluetoothDevice to connect to
     */
    public synchronized void BT_Connect(String BT_DeviceName, boolean listen_in) {
        String address = "";
        if (D) Log.d(TAG, "BT_Connect: connect to " + BT_DeviceName);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null){
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // find device and store address....
                if (BT_DeviceName.equals(device.getName())) {
                    address= device.getAddress();
                }
            }
        }
        if (address.equals("")) {
            mHandler.sendEmptyMessage(BL_DEVICE_NOT_FOUND);
        } else {
            BluetoothDevice device = mAdapter.getRemoteDevice(address);
            mConnectThread = new ConnectThread(device);
            mConnectThread.start();
            setState(STATE_CONNECTING);
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.i(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
        	mConnectThread.cancel(); 
        	mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");


        if (mConnectThread != null) {
        	mConnectThread.cancel(); 
        	mConnectThread = null;
        }

        if (mConnectedThread != null) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

     
    /*
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void sendData(String message) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
    	Log.i(TAG, "ConnectedThreat created, send data: " + message);
        r.write(message.getBytes());
    }

    /*
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void sendDataByte(byte message[]) {
                // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized

        Log.i(TAG, "ConnectedThreat created, send data: " + message);
        r.write(message);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_NONE);
        // Send a failure message back to the Activity
		mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);

        // Send a failure message back to the Activity
		mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
    }
    
      
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;       	
        private final BluetoothDevice mmDevice;
 
        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            Log.i(TAG, "ConnectThread started...");

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
     
    		try {
                tmp = device.createRfcommSocketToServiceRecord(SerialPortServiceClass_UUID);
                Log.i(TAG, "Created BluetoothSocket");    			
    		} 	catch (IOException e1) {
	        	Log.e(TAG, "In BT_Connect() socket create failed: " + e1.getMessage());
	        	mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
			}
    		mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "run ConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.i(TAG, "Attempting connecting to BluetoothSocket");    			
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "connect() failed", e);
            	connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                    Log.e(TAG, "closed socket after exception");
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                //BluetoothSerialService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (cBluetooth.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        
        public ConnectedThread(BluetoothSocket socket) {
            Log.i(TAG, "created ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            Log.i(TAG, "Assigned In- and OutputStream...");
        }

        @Override
        public void run() {
            Log.i(TAG, "run mConnectedThread");
            byte[] buffer = new byte[1024];
            setName("ConnectedThread");
            int bytes = 0;
            // Keep listening to the InputStream while connected
            while (true) {
                if (mmSocket.isConnected()) {
                    Log.i(TAG, "ConnectedThread: Trying to read...");
                    try {
                        // Read from the InputStream for echo to avoid overflow
                        bytes = mmInStream.read(buffer);
                        mHandler.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget();
                        // String str1 = new String(buffer, "UTF-8");
                        Log.i(TAG, "got response: " + buffer);
                    } catch (IOException e) {
                        Log.e(TAG, "disconnected", e);
                        connectionLost();
                        break;
                    }
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            if (mmOutStream != null) {
            	try {
            		mmOutStream.write(buffer);
            		mmOutStream.flush();
            	} catch (IOException e) {
            		Log.e(TAG, "Exception during write", e);
            	}
            } else Log.e(TAG, "Error Send data: outStream is Null");
        }

         public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
