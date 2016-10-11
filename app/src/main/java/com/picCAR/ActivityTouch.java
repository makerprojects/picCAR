package com.picCAR;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
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
	private boolean mixing = true; // for backward compatibility
	private final int cChannelNeutral = 127;
	private String BT_DeviceName;			// Bluetooth device name from settings
	private byte[] commandLeft = {(byte) cCommandHeader,cChannelLeft,cChannelNeutral};	// command buffer for left motor
	private byte[] commandRight = {(byte) cCommandHeader,cChannelRight,cChannelNeutral}; // command buffer for right motor


	private final static int BIG_CIRCLE_SIZE = 120;
	private final static int FINGER_CIRCLE_SIZE = 20;
	
    private int motorLeft = 0;
    private int motorRight = 0;

    private boolean show_Debug;			// show debug information (from settings)
    private int xRperc;					// pivot point from settings
    private final int pwmMax = 126;	   	// maximum value of PWM from settings

	private static boolean suppressMessage = false;

	private static String TAG = ActivityButtons.class.getSimpleName();

	// fail safe related definitions
	Timer timer = null;
	TimerTask timerTask = null;
	int iTimeOut = 0;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MyView v1 = new MyView(this);
        setContentView(v1);

		BT_DeviceName = (String) getResources().getText(R.string.default_BtDevice);
        xRperc = Integer.parseInt(getResources().getString(R.string.default_xRperc));

        loadPref();
        
        bl = new cBluetooth(mHandler);

		Globals g = Globals.getInstance();	// load timeout form global variable
		iTimeOut = g.getData();
		Log.d(TAG, "Read timeout " + String.valueOf(iTimeOut));
	}

	private static class MyHandler extends Handler {
        private final WeakReference<ActivityTouch> mActivity;
     
        public MyHandler(ActivityTouch activity) {
          mActivity = new WeakReference<ActivityTouch>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
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
				case cBluetooth.BL_DEVICE_NOT_FOUND:
					if (!suppressMessage) Toast.makeText(activity.getBaseContext(), "Device not found", Toast.LENGTH_SHORT).show();
					activity.finish();
					break;
				}
          	}
        }
	}

	// get the heartbeat going
	public void startTimer() {
		Log.v(TAG, "starting Timer");
		timer = new Timer();
		timerTask = new TimerTask() {
			@Override
			public void run() {
				bl.sendDataByte(commandLeft);
				bl.sendDataByte(commandRight);
			}
		};
		timer.schedule(timerTask, 0, iTimeOut/2); 	// play it safe...
	}

	public void stopTimer() {
		//stop the timer, if it's not already null
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	private final MyHandler mHandler = new MyHandler(this);
	
	private final static Runnable sRunnable = new Runnable() {
		public void run() { }
	};
	
	
	class MyView extends View {

		Paint fingerPaint, borderPaint, textPaint, alphaPaint;

        int dispWidth;
        int dispHeight;

		Bitmap bitmap;
		int imageW,imageH;

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

			alphaPaint = new Paint();
			alphaPaint.setAlpha(75); // equals 0.3 on a range 0 .. 255

			Drawable image = this.getResources().getDrawable(R.drawable.pikoder_logo);
			imageW = image.getIntrinsicWidth() + (int) convertDpToPixel(5, context);
			imageH = image.getIntrinsicHeight() + (int) convertDpToPixel(5, context);
			Log.d(TAG, String.valueOf("bitmap width:"+image.getIntrinsicWidth()+"  height"+image.getIntrinsicHeight()));
			bitmap = ((BitmapDrawable) image).getBitmap();

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
			canvas.drawRect(dispWidth - BIG_CIRCLE_SIZE, dispHeight - BIG_CIRCLE_SIZE, dispWidth + BIG_CIRCLE_SIZE, dispHeight + BIG_CIRCLE_SIZE, borderPaint);

			Log.d(TAG, String.valueOf("display getLeft: "+this.getLeft()+"  getRight: "+this.getRight()));
			int bmx = this.getRight() - this.getLeft()- imageW;
			int bmy =  this.getBottom()- this.getTop()- imageH;
			Log.d(TAG, String.valueOf("bitmap position x:"+bmx+"  y"+bmy));
			canvas.drawBitmap(bitmap, bmx, bmy, alphaPaint);

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

	/**
	 * This method converts dp unit to equivalent pixels, depending on device density.
	 *
	 * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
	 * @param context Context to get resources and device specific display metrics
	 * @return A float value to represent px equivalent to dp depending on device density
	 */
	public static float convertDpToPixel(float dp, Context context){
		Resources resources = context.getResources();
		DisplayMetrics metrics = resources.getDisplayMetrics();
		float px = dp * ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
		return px;
	}

	/**
	 * This method converts device specific pixels to density independent pixels.
	 *
	 * @param px A value in px (pixels) unit. Which we need to convert into db
	 * @param context Context to get resources and device specific display metrics
	 * @return A float value to represent dp equivalent to px value
	 */
	public static float convertPixelsToDp(float px, Context context){
		Resources resources = context.getResources();
		DisplayMetrics metrics = resources.getDisplayMetrics();
		float dp = px / ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
		return dp;
	}

	private void CalcMotor(float calc_x, float calc_y){

		int xAxis = Math.round(calc_x*pwmMax/BIG_CIRCLE_SIZE);
		int yAxis = Math.round(calc_y*pwmMax/BIG_CIRCLE_SIZE);

		Log.d("4WD", String.valueOf("xAxis:"+xAxis+"  yAxis"+yAxis));

		int xR = Math.round(BIG_CIRCLE_SIZE*xRperc/100);		// calculate the value of pivot point

		if (mixing) {
			if (xAxis > 0) {
				motorRight = yAxis;
				if (Math.abs(Math.round(calc_x)) > xR) {
					motorLeft = Math.round((calc_x - xR) * pwmMax / (BIG_CIRCLE_SIZE - xR));
					motorLeft = Math.round(-motorLeft * yAxis / pwmMax);
				} else motorLeft = yAxis - yAxis * xAxis / pwmMax;
			} else if (xAxis < 0) {
				motorLeft = yAxis;
				if (Math.abs(Math.round(calc_x)) > xR) {
					motorRight = Math.round((Math.abs(calc_x) - xR) * pwmMax / (BIG_CIRCLE_SIZE - xR));
					motorRight = Math.round(-motorRight * yAxis / pwmMax);
				} else motorRight = yAxis - yAxis * Math.abs(xAxis) / pwmMax;
			} else if (xAxis == 0) {
				motorLeft = yAxis;
				motorRight = yAxis;
			}

			if (motorLeft > 0) {
				if (motorLeft > pwmMax) motorLeft = pwmMax;
				motorLeft = motorLeft + cChannelNeutral;
			} else {
				if (motorLeft < -pwmMax) motorLeft = -pwmMax;
				motorLeft = motorLeft + cChannelNeutral;
			}

			if (motorRight > 0) {
				if (motorRight > pwmMax) motorRight = pwmMax;
				motorRight = -motorRight + cChannelNeutral;
			} else {
				if (motorRight < -pwmMax) motorRight = -pwmMax;
				motorRight = -motorRight + cChannelNeutral;
			}
		} else {
			motorLeft = cChannelNeutral + xAxis;
			motorRight = cChannelNeutral - yAxis;
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
		bl.BT_Connect(BT_DeviceName, false);
		suppressMessage = false;
		// start timer onResume if set
		if (iTimeOut > 0) {
			startTimer();
		}
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
		suppressMessage = true;  // regular exit
		stopTimer();
    }
    
    private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		BT_DeviceName = mySharedPreferences.getString("pref_BT_Device", BT_DeviceName);			// the first time we load the default values
    	xRperc = Integer.parseInt(mySharedPreferences.getString("pref_xRperc", String.valueOf(xRperc)));
    	show_Debug = mySharedPreferences.getBoolean("pref_Debug", false);
		mixing = mySharedPreferences.getBoolean("pref_Mixing_active", true);
    }
}
