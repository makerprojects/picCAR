package com.picCAR;

import java.lang.ref.WeakReference;
import java.util.Locale;
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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class ActivityAccelerometer extends Activity implements SensorEventListener  {
	
	private SensorManager mSensorManager;
    private Sensor mAccel;
    private cBluetooth bl = null;

	private int xAxis = 0;
    private int yAxis = 0;
    private int motorLeft = 0;
    private int motorRight = 0;
    private boolean show_Debug;		// show debug information (from settings)
    private boolean mixing = true; // for backward compatibility
    private int xMax;		    	// limit on the X axis from settings  (0-10)
    private int yMax;		    	// limit on the Y axis from settings (0-10)
    private int yThreshold;  		// minimum value of PWM from settings 
    private int pwmMax;	   			// maximum value of PWM from settings 
    private int xR;					// pivot point from settings 

    private final int cCommandHeader = 0xFF; // equals 0xFF
    private final byte cChannelLeft = 0;
    private final byte cChannelRight = 1;
    private final int cChannelNeutral = 127;
    private String BT_DeviceName;			// Bluetooth device name from settings
    private byte[] commandLeft = {(byte) cCommandHeader,cChannelLeft,cChannelNeutral};	// command buffer for left motor
    private byte[] commandRight = {(byte) cCommandHeader,cChannelRight,cChannelNeutral}; // command buffer for right motor

    private static boolean suppressMessage = false;

    private static String TAG = ActivityAccelerometer.class.getSimpleName();

    private int iLastLeft = 255;
    private int iLastRight = 255;

    // additional specific defs for processing...
    private final static int SQUARE_SIDE_LENGTH = 180;
    private final static int FINGER_CIRCLE_SIZE = 20;

    // position of pointer
    float x;
    float y;
    // display features
    int dispWidth;
    int dispHeight;
    MyView v1;

    // made public to allow debug output
    float xRaw, yRaw;        // RAW-value from Accelerometer sensor

    // fail safe related definitions
    Timer timer = null;
    TimerTask timerTask = null;
    int iTimeOut = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        v1 = new MyView(this);
        setContentView(v1);

        BT_DeviceName = (String) getResources().getText(R.string.default_BtDevice);
        xMax = Integer.parseInt((String) getResources().getText(R.string.default_xMax));
        xR = Integer.parseInt((String) getResources().getText(R.string.default_xR));
        yMax = Integer.parseInt((String) getResources().getText(R.string.default_yMax));
        yThreshold = Integer.parseInt((String) getResources().getText(R.string.default_yThreshold));
        pwmMax = Integer.parseInt((String) getResources().getText(R.string.default_pwmMax));

        loadPref();
                
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        Globals g = Globals.getInstance();	// load timeout form global variable
        iTimeOut = g.getData();
        Log.d(TAG, "Read timeout " + String.valueOf(iTimeOut));

        bl = new cBluetooth(mHandler);

    }
    
    private static class MyHandler extends Handler {
        private final WeakReference<ActivityAccelerometer> mActivity;
     
        public MyHandler(ActivityAccelerometer activity) {
          mActivity = new WeakReference<ActivityAccelerometer>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
        	ActivityAccelerometer activity = mActivity.get();
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

    // Inserting Canvas related code...
    class MyView extends View {

        Paint fingerPaint, borderPaint, textPaint, alphaPaint, crossPaint, bigtextPaint;

        Bitmap bitmap;
        int imageW,imageH;

        public MyView(Context context) {
            super(context);
            fingerPaint = new Paint();
            fingerPaint.setAntiAlias(true);
            fingerPaint.setColor(Color.RED);

            borderPaint = new Paint();
            borderPaint.setColor(Color.BLUE);
            borderPaint.setAntiAlias(true);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3);

            crossPaint = new Paint();
            crossPaint.setColor(Color.BLUE);
            crossPaint.setAntiAlias(true);
            crossPaint.setStyle(Paint.Style.STROKE);
            crossPaint.setStrokeWidth(1);

            textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(14);

            bigtextPaint = new Paint();
            bigtextPaint.setStyle(Paint.Style.FILL);
            bigtextPaint.setColor(Color.BLACK);
            bigtextPaint.setTextSize(convertDpToPixel(25,getContext()));

            alphaPaint = new Paint();
            alphaPaint.setAlpha(75); // equals 0.3 on a range 0 .. 255

            Drawable image = this.getResources().getDrawable(R.drawable.pikoder_logo);
            imageW = image.getIntrinsicWidth() + (int) convertDpToPixel(5, context);
            imageH = image.getIntrinsicHeight() + (int) convertDpToPixel(5, context);
            Log.d(TAG, String.valueOf("bitmap width:"+image.getIntrinsicWidth()+"  height"+image.getIntrinsicHeight()));
            bitmap = ((BitmapDrawable) image).getBitmap();
        }


        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); // the default drawing

            dispWidth = (int) Math.round((this.getRight()-this.getLeft())/2);
            dispHeight = (int) Math.round((this.getBottom()-this.getTop())/2);

            if (x < dispWidth - SQUARE_SIDE_LENGTH) {
                x = dispWidth;
                y = dispHeight;
            }

            canvas.drawRect(dispWidth - SQUARE_SIDE_LENGTH, dispHeight - SQUARE_SIDE_LENGTH, dispWidth + SQUARE_SIDE_LENGTH, dispHeight + SQUARE_SIDE_LENGTH, borderPaint);
            canvas.drawLine(dispWidth - SQUARE_SIDE_LENGTH, dispHeight, dispWidth + SQUARE_SIDE_LENGTH,dispHeight, crossPaint);
            canvas.drawLine(dispWidth, dispHeight- SQUARE_SIDE_LENGTH, dispWidth,dispHeight + SQUARE_SIDE_LENGTH, crossPaint);
            canvas.drawCircle(x, y, FINGER_CIRCLE_SIZE, fingerPaint);

            Log.d(TAG, String.valueOf("display getLeft: "+this.getLeft()+"  getRight: "+this.getRight()));
            int bmx = this.getRight() - this.getLeft()- imageW;
            int bmy =  this.getBottom()- this.getTop()- imageH;
            Log.d(TAG, String.valueOf("bitmap position x:"+bmx+"  y"+bmy));
            canvas.drawBitmap(bitmap, bmx, bmy, alphaPaint);

            if(show_Debug){
                canvas.drawText(String.valueOf("X:" + String.format("%.1f",xRaw) + "; xPWM:"+xAxis), 10, 75, textPaint);
                canvas.drawText(String.valueOf("Y:" + String.format("%.1f",yRaw) + "; yPWM:"+yAxis), 10, 95, textPaint);
                canvas.drawText(String.valueOf("Motor:"+String.valueOf(motorLeft)+" "+String.valueOf(motorRight)), 10, 115, textPaint);
            }
            canvas.drawText(String.valueOf(" Tilt your device"), dispWidth - SQUARE_SIDE_LENGTH, dispHeight + SQUARE_SIDE_LENGTH +convertDpToPixel(35,getContext()), bigtextPaint);
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


    private final MyHandler mHandler = new MyHandler(this);

    private final static Runnable sRunnable = new Runnable() {
    	public void run() { }
    };
          
      
    public void onSensorChanged(SensorEvent e) {
        String directionL = "";
        String directionR = "";
        String cmdSendL, cmdSendR;
        float xRaw, yRaw;        // RAW-value from Accelerometer sensor

        WindowManager windowMgr = (WindowManager) this.getSystemService(WINDOW_SERVICE);
        int rotationIndex = windowMgr.getDefaultDisplay().getRotation();
        if (rotationIndex == 1 || rotationIndex == 3) {            // detect 90 or 270 degree rotation
            xRaw = -e.values[1];
            yRaw = e.values[0];
        } else {
            xRaw = e.values[0];
            yRaw = e.values[1];
        }

        // y-Axis = speed
        // x-Axis = direction

        xAxis = -Math.round(xRaw * pwmMax / xR);                // scale gyro input
        yAxis = Math.round(yRaw * pwmMax / yMax);

        if (xAxis > pwmMax) xAxis = pwmMax;
        else if (xAxis < -pwmMax) xAxis = -pwmMax;        // negative - tilt right

        if (yAxis > pwmMax) yAxis = pwmMax;
        else if (yAxis < -pwmMax) yAxis = -pwmMax;        // negative - tilt forward
        else if (yAxis >= 0 && yAxis < yThreshold) yAxis = 0;
        else if (yAxis < 0 && yAxis > -yThreshold) yAxis = 0;

        x = dispWidth + Math.round((xAxis * SQUARE_SIDE_LENGTH) / pwmMax);
        y = dispHeight - Math.round((yAxis * SQUARE_SIDE_LENGTH) / pwmMax);
        v1.invalidate();

        if (mixing) {
            if (xAxis > 0) {        // if tilt to left, slow down the left engine
                motorRight = yAxis;
                if (Math.abs(Math.round(xRaw)) > xR) {
                    motorLeft = Math.round((xRaw - xR) * pwmMax / (xMax - xR));
                    motorLeft = Math.round(-motorLeft * yAxis / pwmMax);
                    //if(motorLeft < -pwmMax) motorLeft = -pwmMax;
                } else motorLeft = yAxis - yAxis * xAxis / pwmMax;
            } else if (xAxis < 0) {        // tilt to right
                motorLeft = yAxis;
                if (Math.abs(Math.round(xRaw)) > xR) {
                    motorRight = Math.round((Math.abs(xRaw) - xR) * pwmMax / (xMax - xR));
                    motorRight = Math.round(-motorRight * yAxis / pwmMax);
                    //if(motorRight > -pwmMax) motorRight = -pwmMax;
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
            motorRight = cChannelNeutral + yAxis;
        }

        commandLeft[2] = (byte) motorLeft; // commands for miniSSC
        commandRight[2] = (byte) motorRight; // commands for miniSSC

        if(bl.getState() == cBluetooth.STATE_CONNECTED) {
            bl.sendDataByte(commandLeft);
            bl.sendDataByte(commandRight);
        }
    }
   
    private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        BT_DeviceName = mySharedPreferences.getString("pref_BT_Device", BT_DeviceName);			// the first time we load the default values
    	xMax = Integer.parseInt(mySharedPreferences.getString("pref_xMax", String.valueOf(xMax)));
    	xR = Integer.parseInt(mySharedPreferences.getString("pref_xR", String.valueOf(xR)));
    	yMax = Integer.parseInt(mySharedPreferences.getString("pref_yMax", String.valueOf(yMax)));
    	yThreshold = Integer.parseInt(mySharedPreferences.getString("pref_yThreshold", String.valueOf(yThreshold)));
    	pwmMax = Integer.parseInt(mySharedPreferences.getString("pref_pwmMax", String.valueOf(pwmMax)));
    	show_Debug = mySharedPreferences.getBoolean("pref_Debug", false);
        mixing = mySharedPreferences.getBoolean("pref_Mixing_active", true);
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
    	mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
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
        suppressMessage = true;
    	mSensorManager.unregisterListener(this);
        stopTimer();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
    }
    
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub
    }
}
