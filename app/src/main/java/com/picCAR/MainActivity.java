/**
 *  picCAR
 *  @version 1.0
 *  http://www.pikoder.com
 *  @author Gregor Schlechtriem
 * 
 */

package com.picCAR;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {

	Button btnActAccelerometer, btnActWheel, btnActButtons, btnActTouch;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_main);
	    
	    btnActAccelerometer = (Button) findViewById(R.id.button_accel);
	    btnActAccelerometer.setOnClickListener(this);
	    
	    btnActWheel = (Button) findViewById(R.id.button_wheel);
	    btnActWheel.setOnClickListener(this);
	    
	    btnActButtons = (Button) findViewById(R.id.button_buttons);
	    btnActButtons.setOnClickListener(this);

	    btnActTouch = (Button) findViewById(R.id.button_touch);
	    btnActTouch.setOnClickListener(this);
	    
	}

	public void onClick(View v) {
		switch (v.getId()) {
	    case R.id.button_accel:
	    	Intent intent_accel = new Intent(this, ActivityAccelerometer.class);
	    	startActivity(intent_accel);
	    	break;
	    case R.id.button_wheel:
	    	Intent intent_wheel = new Intent(this, ActivityWheel.class);
	    	startActivity(intent_wheel);
	    	break;	    	
	    case R.id.button_buttons:
	    	Intent intent_buttons = new Intent(this, ActivityButtons.class);
	    	startActivity(intent_buttons);
	    	break;  
	    case R.id.button_touch:
	    	Intent intent_touch = new Intent(this, ActivityTouch.class);
	    	startActivity(intent_touch);
	    	break;
	    default:
	    	break;
	    }
	}
  
	@Override
	public boolean onCreateOptionsMenu (Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = new Intent();
		switch (item.getItemId()) {
			case R.id.menu_app_settings:
				intent.setClass(MainActivity.this, SetPreferenceActivity.class);
				startActivityForResult(intent, 0);
				return true;
			case R.id.menu_ssc_settings:
				Intent intent_mcu = new Intent(this, ActivityMCU.class);
				startActivity(intent_mcu);
				return true;
			case R.id.menu_about:
				Intent intent_about = new Intent(this, ActivityAbout.class);
				startActivity(intent_about);
				return true;
		}
		return true;
	}
}
