package com.picCAR;

import java.lang.ref.WeakReference;
import java.util.Locale;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class ActivityWheel extends Activity implements SensorEventListener  {
	
	private SensorManager mSensorManager;
    private Sensor mAccel;
    private cBluetooth bl = null;
    private ToggleButton LightButton;
    private Button btn_Rear;
    private VerticalSeekBar VSeekBar;
	
	private int xAxis = 0;
    private int yAxis = 0;
    private int motorLeft = 0;
    private int motorRight = 0;
    private float xgl = 0;
    private boolean isRear = false;															// reverse is off
    private boolean show_Debug;																// show debug information (from settings)
    private int xMax;		    															// limit on the X axis from settings
    private int yMax;		    															// limit on the Y axis from settings
    private int yThreshold;  																// minimum value of PWM from settings
    private int pwmMax;	   																	// maximum value of PWM from settings
    private int xR;																			// pivot point from settings
	private final int cCommandHeader = 0xFF;
	private final byte cChannelLeft = 0;
	private final byte cChannelRight = 1;
	private final int cChannelNeutral = 127;
	private final int cChannelMax = 0xFE;
	private final int cChannelMin = 0;
	private String address;																	// MAC-address from settings
	private byte[] commandLeft = {(byte) cCommandHeader,cChannelLeft,cChannelNeutral};		// command buffer for left motor
	private byte[] commandRight = {(byte) cCommandHeader,cChannelRight,cChannelNeutral}; 	// command buffer for right motor
	private final int pwnNeutral = 127;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wheel);
                
        address = (String) getResources().getText(R.string.default_MAC);
        xMax = Integer.parseInt((String) getResources().getText(R.string.default_xMax));
        xR = Integer.parseInt((String) getResources().getText(R.string.default_xR));
        yMax = Integer.parseInt((String) getResources().getText(R.string.default_yMax));
        yThreshold = Integer.parseInt((String) getResources().getText(R.string.default_yThreshold));
        pwmMax = Integer.parseInt((String) getResources().getText(R.string.default_pwmMax));

		loadPref();
        
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);          
        
        bl = new cBluetooth(this, mHandler);
        bl.checkBTState();
        
        VSeekBar = (VerticalSeekBar) findViewById(R.id.calcVerticalSeekBar);  
        VSeekBar.setMaximum(pwmMax);
        
		btn_Rear = (Button) findViewById(R.id.btnRear);

        btn_Rear.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
		        	isRear = true;
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
		        	isRear = false;
		        }
				return false;
		    }
		});
        
        VSeekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				//Toast.makeText(getBaseContext(), String.valueOf(seekBar.getProgress()), Toast.LENGTH_SHORT).show();
				MotionChanged(xgl,progress);
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				VSeekBar.setProgressAndThumb(0);
			}
    	}); 
        
        mHandler.postDelayed(sRunnable, 600000);
        //finish();
    }
    
    private static class MyHandler extends Handler {
        private final WeakReference<ActivityWheel> mActivity;
     
        public MyHandler(ActivityWheel activity) {
          mActivity = new WeakReference<ActivityWheel>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
			boolean suppressMessage = false;
        	ActivityWheel activity = mActivity.get();
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
	
	public void onSensorChanged(SensorEvent e) {
	    float xRaw;		// RAW-value from Accelerometer sensor 
	    
		WindowManager windowMgr = (WindowManager)this.getSystemService(WINDOW_SERVICE);
        int rotationIndex = windowMgr.getDefaultDisplay().getRotation();
        if (rotationIndex == 1 || rotationIndex == 3){
        	xRaw = -e.values[1];
        }
        else{
        	xRaw = e.values[0]; 	
        }
		MotionChanged(xRaw,VSeekBar.getProgress());
		xgl = xRaw;
	}
        
	private void MotionChanged(float x, int y) {

    	Log.d(cBluetooth.TAG, Math.round(x)+"  "+y);
    	    	
    	xAxis = - Math.round(x*pwmMax/xR);
        //yAxis = -y;
    	if(isRear) yAxis = y;
    	else yAxis = -y;
        
        if(xAxis > pwmMax) xAxis = pwmMax;
        else if(xAxis < -pwmMax) xAxis = -pwmMax;		// negative - tilt right 
        
        if(yAxis > pwmMax) yAxis = pwmMax;
        else if(yAxis < -pwmMax) yAxis = -pwmMax;		// negative - tilt forward 
        else if(yAxis >= 0 && yAxis < yThreshold) yAxis = 0;
        else if(yAxis < 0 && yAxis > -yThreshold) yAxis = 0;
        
        if(xAxis > 0) {		// if tilt to left, slow down the left engine 
        	motorRight = yAxis;
        	if(Math.abs(Math.round(x)) > xR){
        		motorLeft = Math.round((x-xR)*pwmMax/(xMax-xR));
        		motorLeft = Math.round(-motorLeft * yAxis/pwmMax);
        		//if(motorLeft < -pwmMax) motorLeft = -pwmMax;
        	}
        	else motorLeft = yAxis - yAxis*xAxis/pwmMax;
        }
        else if(xAxis < 0) {		// tilt to right 
        	motorLeft = yAxis;
        	if(Math.abs(Math.round(x)) > xR){
        		motorRight = Math.round((Math.abs(x)-xR)*pwmMax/(xMax-xR));
        		motorRight = Math.round(-motorRight * yAxis/pwmMax);
        		//if(motorRight > -pwmMax) motorRight = -pwmMax;
        	}
        	else motorRight = yAxis - yAxis*Math.abs(xAxis)/pwmMax;
        }
        else if(xAxis == 0) {
        	motorLeft = yAxis;
        	motorRight = yAxis;
        }

		if(motorLeft > 0) {
			if (motorLeft > pwmMax) motorLeft = pwmMax;
			motorLeft = motorLeft + cChannelNeutral;
		} else {
			if (motorLeft < -pwmMax) motorLeft = -pwmMax;
			motorLeft = motorLeft + cChannelNeutral;
		}

		if(motorRight > 0) {
			if (motorRight > pwmMax) motorRight = pwmMax;
			motorRight = - motorRight + cChannelNeutral;
		} else {
			if (motorRight < -pwmMax) motorRight = -pwmMax;
			motorRight = - motorRight + cChannelNeutral;
		}

		commandLeft[2] = (byte) motorLeft; // commands for miniSSC
		commandRight[2] = (byte) motorRight; // commands for miniSSC

		if(bl.getState() == cBluetooth.STATE_CONNECTED) {
			bl.sendDataByte(commandLeft);
			bl.sendDataByte(commandRight);
		}

        TextView textX = (TextView) findViewById(R.id.textViewX);
        TextView textY = (TextView) findViewById(R.id.textViewY);
        TextView mLeft = (TextView) findViewById(R.id.mLeft);
        TextView mRight = (TextView) findViewById(R.id.mRight);
        TextView textCmdSend = (TextView) findViewById(R.id.textViewCmdSend);
        
        if(show_Debug){
        	textX.setText(String.valueOf("X:" + String.format("%.1f",x) + "; xPWM:"+xAxis));
	        textY.setText(String.valueOf("Y:" + String.valueOf(y) + "; yPWM:"+yAxis));
        }
        else{
        	textX.setText("");
        	textY.setText("");
        	mLeft.setText("");
        	mRight.setText("");
        	textCmdSend.setText("");
        }
        
    }
   
    private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		address = mySharedPreferences.getString("pref_MAC_address", address);			// the first time we load the default values
    	xMax = Integer.parseInt(mySharedPreferences.getString("pref_xMax", String.valueOf(xMax)));
    	xR = Integer.parseInt(mySharedPreferences.getString("pref_xR", String.valueOf(xR)));
    	yMax = Integer.parseInt(mySharedPreferences.getString("pref_yMax", String.valueOf(yMax)));
    	yThreshold = Integer.parseInt(mySharedPreferences.getString("pref_yThreshold", String.valueOf(yThreshold)));
    //	pwmMax = Integer.parseInt(mySharedPreferences.getString("pref_pwmMax", String.valueOf(pwmMax)));
    	show_Debug = mySharedPreferences.getBoolean("pref_Debug", false);
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
		commandLeft[2] = cChannelNeutral; // commands for miniSSC
		commandRight[2] = cChannelNeutral; // commands for miniSSC
		if (bl.getState() == cBluetooth.STATE_CONNECTED) {
			bl.sendDataByte(commandLeft);
			bl.sendDataByte(commandRight);
		}
		bl.BT_Connect(address, false);
    	mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
    	super.onPause();
		commandLeft[2] = cChannelNeutral; // commands for miniSSC
		commandRight[2] = cChannelNeutral; // commands for miniSSC
		if (bl.getState() == cBluetooth.STATE_CONNECTED) {
			bl.sendDataByte(commandLeft);
			bl.sendDataByte(commandRight);
		}
		bl.BT_onPause();
		mSensorManager.unregisterListener(this);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
    }
    
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub
    }
}
