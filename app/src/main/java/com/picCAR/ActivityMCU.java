package com.picCAR;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

public class ActivityMCU  extends Activity{
	
	private cBluetooth bl = null;
	private Button btn_flash_Read, btn_flash_Write;
	private static CheckBox cb_AutoOFF;
	private static EditText edit_AutoOFF;
	private static String flash_success;
	private static String error_get_data;
	
	private String address;			// MAC-address from settings 
	private static StringBuilder sb = new StringBuilder();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcu);

        address = (String) getResources().getText(R.string.default_BtDevice);
        
        btn_flash_Read = (Button) findViewById(R.id.flash_Read);
        btn_flash_Write = (Button) findViewById(R.id.flash_Write);
        cb_AutoOFF = (CheckBox) findViewById(R.id.cBox_AutoOFF);
        edit_AutoOFF = (EditText) findViewById(R.id.AutoOFF);

    	flash_success = (String) getResources().getText(R.string.flash_success);
    	error_get_data = (String) getResources().getText(R.string.error_get_data);
		loadPref();
		
	    bl = new cBluetooth(mHandler);
	    // bl.checkBTState();
	    
	    cb_AutoOFF.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				String str_to_send = "T=";
            	if (isChecked) edit_AutoOFF.setEnabled(true);
            	else if (!isChecked) {
            		edit_AutoOFF.setEnabled(false) ;
					str_to_send += "000";
				    Log.i(cBluetooth.TAG, "Send Flash Op:" + str_to_send);
				    bl.sendData(str_to_send);
            		edit_AutoOFF.setText("0.0");
            	}
            }
        });
        
        btn_flash_Read.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				edit_AutoOFF.setText("XX.X");
				bl.sendData(String.valueOf("T?"));
	    	}
	    });
        
        btn_flash_Write.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				float num1 = 0;
				String str_to_send = "T=";
				
    		
				try {
					num1 = Float.parseFloat(edit_AutoOFF.getText().toString());
				} catch (NumberFormatException e) {
					String err_data_entry = getString(R.string.err_data_entry); 
					Toast.makeText(getBaseContext(), err_data_entry, Toast.LENGTH_SHORT).show();
				}
				
				if(num1 > 0 && num1 < 100){	
					DecimalFormat myFormatter = new DecimalFormat("00.0");
					String output = myFormatter.format(num1);
					str_to_send += String.valueOf(output.charAt(0)) + String.valueOf(output.charAt(1)) + String.valueOf(output.charAt(3));
					Globals g = Globals.getInstance();	// store timeout in global variable
					g.setData(((output.charAt(0) - '0') *100 + (output.charAt(1) - '0') * 10 + (output.charAt(3) - '0'))*100); // convert to millis
				    Log.d(cBluetooth.TAG, "Send Flash Op:" + str_to_send);
				    bl.sendData(str_to_send);
					//Toast.makeText(getBaseContext(), str_to_send, Toast.LENGTH_SHORT).show();
				}
				else{
					String err_range = getString(R.string.mcu_error_range); 
					Toast.makeText(getBaseContext(), err_range, Toast.LENGTH_SHORT).show();
				}
			}
	    });
        
//        mHandler.postDelayed(sRunnable, 600000);
        
    }
    
    private static class MyHandler extends Handler {
        private final WeakReference<ActivityMCU> mActivity;
     
        public MyHandler(ActivityMCU activity) {
          mActivity = new WeakReference<ActivityMCU>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
        	ActivityMCU activity = mActivity.get();
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
	            	Toast.makeText(activity.getBaseContext(), "Socket failed", Toast.LENGTH_SHORT).show();
	            	activity.finish();
	                break;
	            case cBluetooth.RECEIVE_MESSAGE:								// if message is received 
	            	byte[] readBuf = (byte[]) msg.obj;
	            	String strIncom = new String(readBuf, 0, msg.arg1);
	            	sb.append(strIncom);								// append string 
					Log.i(cBluetooth.TAG, "Received: " + strIncom);
	            	int myNum = 9999;
	            	strIncom = strIncom.replace("\r","").replace("\n","");
	            	if (strIncom.length() >= 3) {
	            		try {
	            			myNum = Integer.parseInt(strIncom);
	            		} catch(NumberFormatException nfe) {
	            			Toast.makeText(activity.getBaseContext(), "Could not parse " + nfe, Toast.LENGTH_SHORT).show();
	            		} 
	            	}    	
	    	        
	            	// int FDataLineIndex = sb.indexOf("FData:");					// string with Flash-data 
	            	// int FWOKLineIndex = sb.indexOf("FWOK");						// string with the message of the successful record in Flash 
	            	// int endOfLineIndex = sb.indexOf("\r\n");
	
	            	// if (FDataLineIndex >= 0 && endOfLineIndex > 0 && endOfLineIndex > FDataLineIndex) {
	            	if (myNum < 1000)	{
//	            		String sbprint = sb.substring(3, 5);
		            	
	            		//sbprint = sbprint.replace("\r","").replace("\n","");	            		
	
	            		// if(sbprint.substring(0, 1).equals("1")) cb_AutoOFF.setChecked(true);
	            		// else cb_AutoOFF.setChecked(false);
	            		
	            		// Float edit_data_AutoOFF = Float.parseFloat(sbprint.substring(1, 3))/10;
	            		Float edit_data_AutoOFF = Float.parseFloat(strIncom)/10;
	            		edit_AutoOFF.setText(String.valueOf(edit_data_AutoOFF));
	            		
	            		if (edit_data_AutoOFF != 0)  cb_AutoOFF.setChecked(true); 
	            		else cb_AutoOFF.setChecked(false);

		            	Toast.makeText(activity.getBaseContext(), "Reading timeout data completed", Toast.LENGTH_SHORT).show();
	            		
	            		// sb.delete(0, sb.length());
	                }
	            	// else if (FWOKLineIndex >= 0 && endOfLineIndex > 0 && endOfLineIndex > FWOKLineIndex) {
	            	else if (strIncom.length() == 1)	{ 	// received '!' as acknoledge of flushing
	            		Toast.makeText(activity.getBaseContext(), flash_success, Toast.LENGTH_SHORT).show();
	            		sb.delete(0, sb.length());
	            	}
	            	// else if(endOfLineIndex > 0) {
	            	else if (strIncom.length() > 0) {		// received something...
	            		Toast.makeText(activity.getBaseContext(), error_get_data, Toast.LENGTH_SHORT).show();
	            		// sb.delete(0, sb.length());
	            	}
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
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	if(cb_AutoOFF.isChecked()) edit_AutoOFF.setEnabled(true);
    	else edit_AutoOFF.setEnabled(false);
    	
    	bl.BT_Connect(address, true);
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
