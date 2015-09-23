package com.picCAR;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ActivityTouch extends Activity {
	
    private cBluetooth bl = null;

	private final int cCommandHeader = 0xFF; // equals 0xFF
	private final byte cChannelLeft = 0;
	private final byte cChannelRight = 1;
	private final int cChannelNeutral = 127;
	private final int cChannelMax = 0xFE; // equals 0xFE
	private final int cChannelMin = 0;
	private String address;			// MAC-address from settings
	private byte[] commandLeft = {(byte) cCommandHeader,cChannelLeft,cChannelNeutral};	// command buffer for left motor
	private byte[] commandRight = {(byte) cCommandHeader,cChannelRight,cChannelNeutral}; // command buffer for right motor

	private final int pwnNeutral = 127;


	private final static int BIG_CIRCLE_SIZE = 120;
	private final static int FINGER_CIRCLE_SIZE = 20;
	
    private int motorLeft = 0;
    private int motorRight = 0;
	private int prev_xAxis = 255; // make sure that initial setting is executed
	private int prev_yAxis = 0;

    private boolean show_Debug;			// show debug information (from settings)
    // private boolean BT_is_connect;		// bluetooh is connected  
    private int xRperc;					// pivot point from settings 
    private final int pwmMax = 126;	   	// maximum value of PWM from settings

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyView v1 = new MyView(this);
        //setContentView(new MyView(this));
        setContentView(v1);
        
        address = (String) getResources().getText(R.string.default_MAC);
        xRperc = Integer.parseInt(getResources().getString(R.string.default_xRperc));

        loadPref();
        
        bl = new cBluetooth(this, mHandler);
        bl.checkBTState();
        
/*        final ToggleButton myLightButton = new ToggleButton(this);
        addContentView(myLightButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        myLightButton.setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			if(myLightButton.isChecked()){
    				if(bl.getState() == cBluetooth.STATE_CONNECTED) bl.sendData(String.valueOf(commandHorn+"1\r"));
    			}else{
    				if(bl.getState() == cBluetooth.STATE_CONNECTED) bl.sendData(String.valueOf(commandHorn+"0\r"));
    			}
    		}
    	});
    */
        mHandler.postDelayed(sRunnable, 600000);
	}

	private static class MyHandler extends Handler {
        private final WeakReference<ActivityTouch> mActivity;
     
        public MyHandler(ActivityTouch activity) {
          mActivity = new WeakReference<ActivityTouch>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
			boolean suppressMessage = false;
        	ActivityTouch activity = mActivity.get();
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
	
	
	class MyView extends View {

		Paint fingerPaint, borderPaint, textPaint;

        int dispWidth;
        int dispHeight;
        
        float x;
        float y;
        
        float xcirc;
        float ycirc;
        
        // variables for drag
        boolean drag = false;
        float dragX = 0;
        float dragY = 0;

        public MyView(Context context) {
        	super(context);
        	fingerPaint = new Paint();
        	fingerPaint.setAntiAlias(true);
        	fingerPaint.setColor(Color.RED);
                
        	borderPaint = new Paint();
        	borderPaint.setColor(Color.BLUE);
        	borderPaint.setAntiAlias(true);
        	borderPaint.setStyle(Style.STROKE);
        	borderPaint.setStrokeWidth(3);
        	
	        textPaint = new Paint(); 
	        textPaint.setColor(Color.WHITE); 
	        textPaint.setStyle(Style.FILL); 
	        textPaint.setColor(Color.BLACK); 
	        textPaint.setTextSize(14); 
        }


        protected void onDraw(Canvas canvas) {
        	dispWidth = (int) Math.round((this.getRight()-this.getLeft())/3.5);
        	dispHeight = (int) Math.round((this.getBottom()-this.getTop())/1.7);
        	if(!drag){
        		x = dispWidth;
        		y = dispHeight;
        		fingerPaint.setColor(Color.RED);
        	}

            canvas.drawCircle(x, y, FINGER_CIRCLE_SIZE, fingerPaint);              
            canvas.drawCircle(dispWidth, dispHeight, BIG_CIRCLE_SIZE, borderPaint);
            
            if(show_Debug){
	            canvas.drawText(String.valueOf("X:"+xcirc), 10, 75, textPaint);
	            canvas.drawText(String.valueOf("Y:"+(-ycirc)), 10, 95, textPaint);
	            canvas.drawText(String.valueOf("Motor:"+String.valueOf(motorLeft)+" "+String.valueOf(motorRight)), 10, 115, textPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
        	
        	// coordinate of Touch-event
        	float evX = event.getX();
        	float evY = event.getY();
                          
        	xcirc = event.getX() - dispWidth;
        	ycirc = event.getY() - dispHeight;
        	Log.d("4WD", String.valueOf("X:"+this.getRight()+" Y:"+dispHeight));
            	   
        	float radius = (float) Math.sqrt(Math.pow(Math.abs(xcirc),2)+Math.pow(Math.abs(ycirc),2));

        	switch (event.getAction()) {

        	case MotionEvent.ACTION_DOWN:        
        		if(radius >= 0 && radius <= BIG_CIRCLE_SIZE){
        			x = evX;
        			y = evY;
        			fingerPaint.setColor(Color.GREEN);
        			CalcMotor(xcirc,ycirc);
        			invalidate();
        			drag = true;
        		}
        		break;

        	case MotionEvent.ACTION_MOVE:
        		// if drag mode is enabled 
        		if (drag && radius >= 0 && radius <= BIG_CIRCLE_SIZE) {
        			x = evX;
        			y = evY;
        			fingerPaint.setColor(Color.GREEN);
        			//temptxtMotor = CalcMotor(xcirc,ycirc);
        			CalcMotor(xcirc,ycirc);
        			invalidate();
        		}
        		break;

        	// touch completed 
        	case MotionEvent.ACTION_UP:
        		// turn off the drag mode 
        		xcirc = 0;
        		ycirc = 0; 
        		drag = false;
        		//temptxtMotor = CalcMotor(xcirc,ycirc);
        		CalcMotor(xcirc,ycirc);
        		invalidate();
        		break;
        	}
        	return true;
        }
	}
	
	private void CalcMotor(float calc_x, float calc_y){

		int xAxis = Math.round(calc_x*pwmMax/BIG_CIRCLE_SIZE);
		int yAxis = Math.round(calc_y*pwmMax/BIG_CIRCLE_SIZE);

		Log.d("4WD", String.valueOf("xAxis:"+xAxis+"  yAxis"+yAxis));

		int xR = Math.round(BIG_CIRCLE_SIZE*xRperc/100);		// calculate the value of pivot point

		if(xAxis > 0) {
			motorRight = yAxis;
			if(Math.abs(Math.round(calc_x)) > xR){
				motorLeft = Math.round((calc_x-xR)*pwmMax/(BIG_CIRCLE_SIZE-xR));
				motorLeft = Math.round(-motorLeft * yAxis/pwmMax);
			} else motorLeft = yAxis - yAxis*xAxis/pwmMax;
		}
		else if(xAxis < 0) {
			motorLeft = yAxis;
			if(Math.abs(Math.round(calc_x)) > xR){
				motorRight = Math.round((Math.abs(calc_x)-xR)*pwmMax/(BIG_CIRCLE_SIZE-xR));
				motorRight = Math.round(-motorRight * yAxis/pwmMax);
			} else motorRight = yAxis - yAxis*Math.abs(xAxis)/pwmMax;
		} else if(xAxis == 0) {
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
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
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
    	// BT_is_connect = bl.BT_Connect(address, false);
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
    }
    
    private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);  
    	address = mySharedPreferences.getString("pref_MAC_address", address);			// the first time we load the default values
    	xRperc = Integer.parseInt(mySharedPreferences.getString("pref_xRperc", String.valueOf(xRperc)));
//    	pwmMax = Integer.parseInt(mySharedPreferences.getString("pref_pwmMax", String.valueOf(pwmMax)));
    	show_Debug = mySharedPreferences.getBoolean("pref_Debug", false);
//    	commandLeft = mySharedPreferences.getString("pref_commandLeft", commandLeft);
//    	commandRight = mySharedPreferences.getString("pref_commandRight", commandRight);
//    	commandHorn = mySharedPreferences.getString("pref_commandHorn", commandHorn);
//		pwmBtnMotorLeft = Integer.parseInt((String) getResources().getText(R.string.default_pwmBtnMotorLeft));
//		pwmBtnMotorRight = Integer.parseInt((String) getResources().getText(R.string.default_pwmBtnMotorRight));

    }
}
