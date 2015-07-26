package com.picCAR;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ActivityButtons extends Activity {
	
	private cBluetooth bl = null;
/*	private ToggleButton LightButton; */
	
	private Button btn_forward, btn_backward, btn_left, btn_right;

	private final int cCommandHeader = 0xFF; // equals 0xFF
	private final byte cChannelLeft = 0;
	private final byte cChannelRight = 1;
	private final int cChannelNeutral = 0x7F;
	private final int cChannelMax = 0xFE; // equals 0xFE
	private final byte cChannelMin = 0;
	private String address;			// MAC-address from settings
    private byte[] commandLeft = {(byte)cCommandHeader, cChannelLeft, (byte) cChannelNeutral};	// command buffer for left motor
    private byte[] commandRight = {(byte) cCommandHeader,cChannelRight, (byte) cChannelNeutral}; // command buffer for right motor
//    private String commandHorn;		// command symbol for optional command (for example - horn) from settings

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_buttons);
		
		address = (String) getResources().getText(R.string.default_MAC);
//		pwmBtnMotorLeft = Integer.parseInt((String) getResources().getText(R.string.default_pwmBtnMotorLeft));
//		pwmBtnMotorRight = Integer.parseInt((String) getResources().getText(R.string.default_pwmBtnMotorRight));
//        commandLeft = (String) getResources().getText(R.string.default_commandLeft);
//        commandRight = (String) getResources().getText(R.string.default_commandRight);
//        commandHorn = (String) getResources().getText(R.string.default_commandHorn);

		loadPref();
		
	    bl = new cBluetooth(this, mHandler);
	    bl.checkBTState();
		
		btn_forward = (Button) findViewById(R.id.forward);
		btn_backward = (Button) findViewById(R.id.backward);
		btn_left = (Button) findViewById(R.id.left);
		btn_right = (Button) findViewById(R.id.right);
		       
		btn_forward.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
					commandLeft[2] = cChannelMin; // commands for miniSSC
					commandRight[2] = (byte) cChannelMax; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
					commandLeft[2] = cChannelNeutral; // commands for miniSSC
					commandRight[2] = cChannelNeutral; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        }
				return false;
		    }
		});
		
		btn_left.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
					commandLeft[2] = cChannelMin; // commands for miniSSC
					commandRight[2] = cChannelMin; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
					commandLeft[2] = cChannelNeutral; // commands for miniSSC
					commandRight[2] = cChannelNeutral; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        }
				return false;
		    }
		});
		
		btn_right.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
					commandLeft[2] = (byte) cChannelMax; // commands for miniSSC
					commandRight[2] = (byte) cChannelMax; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
					commandLeft[2] = cChannelNeutral; // commands for miniSSC
					commandRight[2] = cChannelNeutral; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        }
				return false;
		    }
		});
		
		btn_backward.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
					commandLeft[2] = (byte) cChannelMax; // commands for miniSSC
					commandRight[2] = cChannelMin; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
					commandLeft[2] = cChannelNeutral; // commands for miniSSC
					commandRight[2] = cChannelNeutral; // commands for miniSSC
		        	if (bl.getState() == cBluetooth.STATE_CONNECTED) {
						bl.sendDataByte(commandLeft);
						bl.sendDataByte(commandRight);
		        	}
		        }
				return false;
		    }
		});

		mHandler.postDelayed(sRunnable, 600000);
		
	}
		
    private static class MyHandler extends Handler {
        private final WeakReference<ActivityButtons> mActivity;
     
        public MyHandler(ActivityButtons activity) {
          mActivity = new WeakReference<ActivityButtons>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
			boolean suppressMessage = false;
			ActivityButtons activity = mActivity.get();
        	if (activity != null) {
        		switch (msg.what) {
	            case cBluetooth.BL_NOT_AVAILABLE:
	               	Log.d(cBluetooth.TAG, "Bluetooth is not available. Exit");
	            	Toast.makeText(activity.getBaseContext(), "Bluetooth is not available", Toast.LENGTH_SHORT).show();
	                activity.finish();
	                break;
	            case cBluetooth.BL_INCORRECT_ADDRESS:
	            	Log.d(cBluetooth.TAG, "Incorrect MAC address");
	            	Toast.makeText(activity.getBaseContext(), "Incorrect Bluetooth address", Toast.LENGTH_SHORT).show();
	                break;
	            case cBluetooth.BL_REQUEST_ENABLE:   
	            	Log.d(cBluetooth.TAG, "Request Bluetooth Enable");
	            	BluetoothAdapter.getDefaultAdapter();
	            	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	            	activity.startActivityForResult(enableBtIntent, 1);
	                break;
	            case cBluetooth.BL_SOCKET_FAILED:
					if (!suppressMessage) Toast.makeText(activity.getBaseContext(), "Socket failed", Toast.LENGTH_SHORT).show();
	            	activity.finish();
	                break;
				case cBluetooth.USER_STOP_INITIATED:
					suppressMessage = true;
					break;
				}
          	}
        }
	}
     
	private final MyHandler mHandler = new MyHandler(this);
     
	private final static Runnable sRunnable = new Runnable() {
		public void run() { }
	};
	
    private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);  
    	address = mySharedPreferences.getString("pref_MAC_address", address);			// the first time we load the default values
//    	pwmBtnMotorLeft = Integer.parseInt(mySharedPreferences.getString("pref_pwmBtnMotorLeft", String.valueOf(pwmBtnMotorLeft)));
//    	pwmBtnMotorRight = Integer.parseInt(mySharedPreferences.getString("pref_pwmBtnMotorRight", String.valueOf(pwmBtnMotorRight)));
 //   	commandLeft = mySharedPreferences.getString("pref_commandLeft", commandLeft);
		//   	commandRight = mySharedPreferences.getString("pref_commandRight", commandRight);
//    	commandHorn = mySharedPreferences.getString("pref_commandHorn", commandHorn);
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
    	bl.BT_Connect(address, false);
    }

    @Override
    protected void onPause() {
    	super.onPause();
    	bl.BT_onPause();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
    }
}
