/**************************************************************************************************
  Filename:       DeviceActivity.java
  Revised:        $Date: 2013-09-05 07:58:48 +0200 (to, 05 sep 2013) $
  Revision:       $Revision: 27616 $

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI (�TI Devices�). 
  No hardware patent is licensed hereunder.

  Redistributions must preserve existing copyright notices and reproduce this license (including the
  above copyright notice and the disclaimer and (if applicable) source code license limitations below)
  in the documentation and/or other materials provided with the distribution

  Redistribution and use in binary form, without modification, are permitted provided that the following
  conditions are met:

    * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
      software provided in binary form.
    * any redistribution and use are licensed by TI for use only with TI Devices.
    * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

  If software source code is provided to you, modification and redistribution of the source code are permitted
  provided that the following conditions are met:

    * any redistribution and use of the source code, including any resulting derivative works, are licensed by
      TI for use only with TI Devices.
    * any redistribution and use of any object code compiled from the source code and any resulting derivative
      works, are licensed by TI for use only with TI Devices.

  Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
  promote products derived from this software without specific prior written permission.

  DISCLAIMER.

  THIS SOFTWARE IS PROVIDED BY TI AND TI�S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI�S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.mooshim.mooshimeter.main;

import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.Block;
import com.mooshim.mooshimeter.common.BluetoothLeService;
import com.mooshim.mooshimeter.common.GattInfo;
import com.mooshim.mooshimeter.common.MooshimeterDevice;

public class DeviceActivity extends FragmentActivity {
	// Activity
	public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
	private static final int PREF_ACT_REQ = 0;
	private static final int FWUPDATE_ACT_REQ = 1;

	// BLE
	private BluetoothLeService mBtLeService = null;
	private List<BluetoothGattService> mServiceList = null;
	private boolean mServicesRdy = false;
    public static MooshimeterDevice mMeter = null;

	// SensorTagGatt
	private List<Sensor> mEnabledSensors = new ArrayList<Sensor>();
	private BluetoothGattService mOadService = null;
	private BluetoothGattService mConnControlService = null;
	private String mFwRev;

    // GUI
    private final TextView[] value_labels = new TextView[2];

    private final Button[] display_set_buttons = new Button[2];
    private final Button[] input_set_buttons   = new Button[2];
    private final Button[] range_auto_buttons  = new Button[2];
    private final Button[] range_buttons       = new Button[2];
    private final Button[] units_buttons       = new Button[2];

    private Button rate_auto_button;
    private Button rate_button;
    private Button logging_button;
    private Button depth_auto_button;
    private Button depth_button;
    private Button zero_button;

    private OrientationEventListener orientation_listener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();

		// BLE
		mBtLeService = BluetoothLeService.getInstance();
		mServiceList = new ArrayList<BluetoothGattService>();

        // GUI
        setContentView(R.layout.meter_view);

        // Bind the GUI elements
        value_labels[0] = (TextView) findViewById(R.id.ch1_value_label);
        value_labels[1] = (TextView) findViewById(R.id.ch2_value_label);

        display_set_buttons[0] = (Button) findViewById(R.id.ch1_display_set_button);
        input_set_buttons  [0] = (Button) findViewById(R.id.ch1_input_set_button);
        range_auto_buttons [0] = (Button) findViewById(R.id.ch1_range_auto_button);
        range_buttons      [0] = (Button) findViewById(R.id.ch1_range_button);
        units_buttons      [0] = (Button) findViewById(R.id.ch1_units_button);

        display_set_buttons[1] = (Button) findViewById(R.id.ch2_display_set_button);
        input_set_buttons  [1] = (Button) findViewById(R.id.ch2_input_set_button);
        range_auto_buttons [1] = (Button) findViewById(R.id.ch2_range_auto_button);
        range_buttons      [1] = (Button) findViewById(R.id.ch2_range_button);
        units_buttons      [1] = (Button) findViewById(R.id.ch2_units_button);

        rate_auto_button  = (Button) findViewById(R.id.rate_auto_button);
        rate_button       = (Button) findViewById(R.id.rate_button);
        logging_button    = (Button) findViewById(R.id.logging_button);
        depth_auto_button = (Button) findViewById(R.id.depth_auto_button);
        depth_button      = (Button) findViewById(R.id.depth_button);
        zero_button = (Button) findViewById(R.id.zero_button);

        // Catch orientation change
        //orientation_listener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
        //    @Override
        //    public void onOrientationChanged(int i) {
        //        Log.i(null,"Orientation changed!");
        //    }
        //};
        //orientation_listener.enable();
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        switch(newConfig.orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                Log.d(null,"LANDSCAPE");
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                Log.d(null,"PORTRAIT");
                break;

        }
    }

    @Override
	public void onDestroy() {
		super.onDestroy();
		finishActivity(PREF_ACT_REQ);
		finishActivity(FWUPDATE_ACT_REQ);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//this.optionsMenu = menu;
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.device_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.opt_prefs:
			startPreferenceActivity();
			break;
		case R.id.opt_fwupdate:
			startOadActivity();
			break;
		case R.id.opt_about:
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	protected void onResume() {
		// Log.d(TAG, "onResume");
		super.onResume();
        mMeter = new MooshimeterDevice(this, new Block() {
            @Override
            public void run() {
                mMeter.meter_settings.target_meter_state = mMeter.METER_RUNNING;
                mMeter.meter_settings.calc_settings |= 0x50;
                mMeter.sendMeterSettings(new Block() {
                    @Override
                    public void run() {
                        Log.i(null,"Mode set");
                        mMeter.enableMeterStreamSample(true, new Block() {
                            @Override
                            public void run() {
                                Log.i(null,"Stream requested");
                            }
                        }, new Block() {
                            @Override
                            public void run() {
                                stream_cb();
                            }
                        });
                    }
                });
            }
        });
	}

	@Override
	protected void onPause() {
		// Log.d(TAG, "onPause");
		super.onPause();
        if(mMeter.isMeterStreamSampleEnabled()) {
            mMeter.enableMeterStreamSample(false, null, null);
            mMeter.close();
        }
	}

	BluetoothGattService getOadService() {
		return mOadService;
	}

	BluetoothGattService getConnControlService() {
		return mConnControlService;
	}

	private void startOadActivity() {
    // For the moment OAD does not work on Galaxy S3 (disconnects on parameter update)
    if (Build.MODEL.contains("I9300")) {
			Toast.makeText(this, "OAD not available on this Android device",
			    Toast.LENGTH_LONG).show();
			return;
    }
    	
		if (mOadService != null && mConnControlService != null) {
			// Disable sensors and notifications when the OAD dialog is open
			//enableDataCollection(false);
			// Launch OAD
			final Intent i = new Intent(this, FwUpdateActivity.class);
			startActivityForResult(i, FWUPDATE_ACT_REQ);
		} else {
			Toast.makeText(this, "OAD not available on this BLE device",
			    Toast.LENGTH_LONG).show();
		}
	}

	private void startPreferenceActivity() {
		// Disable sensors and notifications when the settings dialog is open
		//enableDataCollection(false);
		// Launch preferences
		final Intent i = new Intent(this, PreferencesActivity.class);
		//i.putExtra(PreferencesActivity.EXTRA_METER, mMeter);
		startActivityForResult(i, PREF_ACT_REQ);
	}

	private void checkOad() {
		// Check if OAD is supported (needs OAD and Connection Control service)
		mOadService = null;
		mConnControlService = null;

		for (int i = 0; i < mServiceList.size()
		    && (mOadService == null || mConnControlService == null); i++) {
			BluetoothGattService srv = mServiceList.get(i);
			if (srv.getUuid().equals(GattInfo.OAD_SERVICE_UUID)) {
				mOadService = srv;
			}
			if (srv.getUuid().equals(GattInfo.CC_SERVICE_UUID)) {
				mConnControlService = srv;
			}
		}
	}

	private void setBusy(boolean b) {
		//mDeviceView.setBusy(b);
	}

	private void displayServices() {
		mServicesRdy = true;

		try {
			mServiceList = mBtLeService.getSupportedGattServices();
		} catch (Exception e) {
			e.printStackTrace();
			mServicesRdy = false;
		}

		// Characteristics descriptor readout done
		if (!mServicesRdy) {
			setError("Failed to read services");
		}
	}

	private void setError(String txt) {
		setBusy(false);
		Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
	}

	private void setStatus(String txt) {
		Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
	}

	// Activity result handling
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case PREF_ACT_REQ:
			Toast.makeText(this, "Applying preferences", Toast.LENGTH_SHORT).show();
			break;
		case FWUPDATE_ACT_REQ:
			// FW update cancelled so resume
			//enableDataCollection(true);
			break;
		default:
			setError("Unknown request code");
			break;
		}
	}

    public String formatReading(double val, MooshimeterDevice.SignificantDigits digits) {
        //TODO: Unify prefix handling.  Right now assume that in the area handling the units the correct prefix
        // is being applied
        while(digits.high > 4) {
            digits.high -= 3;
            val /= 1000;
        }
        while(digits.high <=0) {
            digits.high += 3;
            val *= 1000;
        }

        // TODO: Prefixes for units.  This will fail for wrong values of digits
        boolean neg = val<0;
        int left = digits.high;
        int right = -1*(digits.high-digits.n_digits);
        String formatstring = String.format("%s%%0%d.%df",neg?"":" ", left+right+(neg?1:0), right); // To live is to suffer
        String retval = String.format(formatstring, val);
        //Truncate
        retval = retval.substring(0, Math.min(retval.length(), 8));
        return retval;
    }

    private void stream_cb() {
        Log.i(null,"Sample received!");
        valueLabelRefresh(0);
        valueLabelRefresh(1);

        // Handle autoranging
        // Save a local copy of settings
        byte[] save = mMeter.meter_settings.pack();
        mMeter.applyAutorange();
        byte[] compare = mMeter.meter_settings.pack();
        // TODO: There must be a more efficient way to do this.  But I think like a c-person
        // Check if anything changed, and if so apply changes
        if(!save.equals(compare)) {
            mMeter.sendMeterSettings(new Block() {
                @Override
                public void run() {
                    refreshAllControls();
                }
            });
        }
    }

    /////////////////////////
    // Widget Refreshers
    /////////////////////////

    private void refreshAllControls() {
        //TODO
        Log.d(null,"TODO");
        rate_auto_button_refresh();
        rate_button_refresh();
        depth_auto_button_refresh();
        depth_button_refresh();
        logging_button_refresh();
        zero_button_refresh();
        for(int c = 0; c < 2; c++) {
            autoRangeButtonRefresh(c);
            display_set_button_refresh(c);
            input_set_button_refresh(c);
            units_button_refresh(c);
            range_button_refresh(c);
        }
    }

    private void rate_auto_button_refresh() {
        styleAutoButton(rate_auto_button,mMeter.disp_rate_auto);
    }

    private void rate_button_refresh() {
        byte rate_setting = (byte)(mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK);
        int rate = 125 * (1<<rate_setting);
        String title = String.format("%dHz", rate);
        int color = mMeter.disp_rate_auto?Color.GRAY:Color.WHITE;
        rate_button.setText(title);
        rate_button.setBackgroundColor(color);
    }

    private void depth_auto_button_refresh() {
        styleAutoButton(depth_auto_button,mMeter.disp_depth_auto);
    }

    private void depth_button_refresh() {
        byte depth_setting = (byte)(mMeter.meter_settings.calc_settings & MooshimeterDevice.METER_CALC_SETTINGS_DEPTH_LOG2);
        int depth = (1<<depth_setting);
        String title = String.format("%dsmpl", depth);
        int color = mMeter.disp_depth_auto?Color.GRAY:Color.WHITE;
        depth_button.setText(title);
        depth_button.setBackgroundColor(color);
    }

    private void logging_button_refresh() {
        // TODO
    }

    private void zero_button_refresh() {
        int color    = mMeter.offset_on?Color.GREEN:Color.RED;
        zero_button.setBackgroundColor(color);
    }

    private void styleAutoButton(Button button, boolean auto) {
        int color    = auto?Color.GREEN:Color.RED;
        String title = auto?"A":"M";
        button.setText(title);
        button.setBackgroundColor(color);
    }

    private void autoRangeButtonRefresh(final int c) {
        styleAutoButton(range_auto_buttons[c],mMeter.disp_range_auto[c]);
    }

    private void display_set_button_refresh(final int c) {
        display_set_buttons[c].setText(mMeter.getDescriptor(c));
    }

    private void input_set_button_refresh(final int c) {
        input_set_buttons[c].setText(mMeter.getInputLabel(c));
    }

    private void units_button_refresh(final int c) {
        String unit_str;
        if(!mMeter.disp_hex[c]) {
            MooshimeterDevice.SignificantDigits digits = mMeter.getSigDigits(c);
            final String[] prefixes = {"μ","m","","k","M"};
            byte prefix_i = 2;
            //TODO: Unify prefix handling.
            while(digits.high > 4) {
                digits.high -= 3;
                prefix_i++;
            }
            while(digits.high <=0) {
                digits.high += 3;
                prefix_i--;
            }
            unit_str = String.format("%s%s",prefixes[prefix_i],mMeter.getUnits(c));
        } else {
            unit_str = "RAW";
        }
        units_buttons[c].setText(unit_str);
    }

    private void range_button_refresh(final int c) {
        // How many different ranges do we want to support?
        // Supporting a range for every single PGA gain seems mighty excessive.

        byte channel_setting = mMeter.meter_settings.chset[c];
        byte measure_setting = mMeter.meter_settings.measure_settings;
        String lval = "";

        switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(c) {
                    case 0:
                        switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK) {
                            case 0x10:
                                lval = "10A";
                                break;
                            case 0x40:
                                lval = "2.5A";
                                break;
                            case 0x60:
                                lval = "1A";
                                break;
                        }
                        break;
                    case 1:
                        switch(mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_GPIO_MASK) {
                        case 0x00:
                            lval = "1.2V";
                            break;
                        case 0x10:
                            lval = "60V";
                            break;
                        case 0x20:
                            lval = "600V";
                            break;
                    }
                    break;
                }
                break;
            case 0x04:
                // Temp input
                lval = "60C";
                break;
            case 0x09:
                switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                case DIODE:
                    switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK) {
                        case 0x10:
                            lval = "1.2V";
                            break;
                        case 0x40:
                            lval = "300mV";
                            break;
                        case 0x60:
                            lval = "100mV";
                            break;
                    }
                    break;
                case RESISTANCE:
                    switch((channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK) | (measure_setting & MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_LVL)) {
                        case 0x12:
                            lval = "10kΩ";
                            break;
                        case 0x42:
                            lval = "2.5kΩ";
                            break;
                        case 0x62:
                            lval = "1kΩ";
                            break;
                        case 0x10:
                            lval = "10MΩ";
                            break;
                        case 0x40:
                            lval = "2.5MΩ";
                            break;
                        case 0x60:
                            lval = "1MΩ";
                            break;
                    }
                    break;
            }
            break;
        }
        range_buttons[c].setText(lval);
        if(mMeter.disp_range_auto[c]) {
            range_buttons[c].setBackgroundColor(Color.GRAY);
        } else {
            range_buttons[c].setBackgroundColor(Color.WHITE);
        }
    }

    private void valueLabelRefresh(final int c) {
        Log.d(null,"Value update");

        final boolean ac = mMeter.disp_ac[c];
        final TextView v = value_labels[c];
        double val;
        int lsb_int;
        if(ac) { lsb_int = (int)(Math.sqrt(mMeter.meter_sample.reading_ms[c])); }
        else   { lsb_int = mMeter.meter_sample.reading_lsb[c]; }

        if( mMeter.disp_hex[c]) {
            lsb_int &= 0x00FFFFFF;
            String s = String.format("%06X", lsb_int);
            v.setText(s);
        } else {
            // If at the edge of your range, say overload
            // Remember the bounds are asymmetrical
            final int upper_limit_lsb = (int) (1.3*(1<<22));
            final int lower_limit_lsb = (int) (-0.9*(1<<22));

            if(   lsb_int > upper_limit_lsb
                    || lsb_int < lower_limit_lsb ) {
                v.setText("OVERLOAD");
            } else {
                // TODO: implement these methods and revive this segment of code
                val = mMeter.lsbToNativeUnits(lsb_int, c);
                v.setText(formatReading(val, mMeter.getSigDigits(c)));
            }
        }
    }

    /////////////////////////
    // Button Click Handlers
    ////////////////////////

    private void cycleCH3Mode() {
        // Java enums don't have integer values associated with them
        // so I must explicitly build this state machine
        // TODO: Someone who knows java better fix this
        switch(mMeter.disp_ch3_mode) {
            case VOLTAGE:
                mMeter.disp_ch3_mode = MooshimeterDevice.CH3_MODES.RESISTANCE;
                break;
            case RESISTANCE:
                mMeter.disp_ch3_mode = MooshimeterDevice.CH3_MODES.DIODE;
                break;
            case DIODE:
                mMeter.disp_ch3_mode = MooshimeterDevice.CH3_MODES.VOLTAGE;
                break;
        }
    }

    byte pga_cycle(byte chx_set) {
        // FIXME: Shouldn't have two separate pga_cycle routines (main is in MooshimeterDevice)
        byte tmp;
        tmp = (byte)(chx_set & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK);
        tmp >>=4;
        switch(tmp) {
            case 1:
                tmp=4;
                break;
            case 4:
                tmp=6;
                break;
            case 6:
            default:
                tmp=1;
                break;
        }
        tmp <<= 4;
        chx_set &=~MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK;
        chx_set |= tmp;
        return chx_set;
    }

    private void onDisplaySetClick(int c) {
        // If on normal electrode input, toggle between AC and DC display
        // If reading CH3, cycle from VauxDC->VauxAC->Resistance->Diode
        // If reading temp, do nothing
        byte setting = (byte) (mMeter.meter_settings.chset[c] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK);
        switch(setting) {
            case 0x00:
                // Electrode input
                mMeter.disp_ac[c] ^= true;
                break;
            case 0x04:
                // Temp input
                break;
            case 0x09:
                switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                    mMeter.disp_ac[c] ^= true;
                    if(!mMeter.disp_ac[c]){cycleCH3Mode();}
                    break;
                case RESISTANCE:
                    cycleCH3Mode();
                    break;
                case DIODE:
                    cycleCH3Mode();
                    break;
            }
            switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                    mMeter.meter_settings.measure_settings &=~MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON;
                    break;
                case RESISTANCE:
                    mMeter.meter_settings.measure_settings |= MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON;
                    break;
                case DIODE:
                    mMeter.meter_settings.measure_settings |= MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_ON;
                    break;
            }
            break;
        }
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                refreshAllControls();
            }
        });
    }

    private void onInputSetClick(int c) {
        byte setting       = mMeter.meter_settings.chset[c];
        byte other_setting = mMeter.meter_settings.chset[(c+1)%2];
        switch(setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input: Advance to CH3 unless the other channel is already on CH3
                if((other_setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) == 0x09 ) {
                    setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                    setting |= 0x04;
                } else {
                    setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                    setting |= 0x09;
                }
                break;
            case 0x09:
                // CH3 input
                setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                setting |= 0x04;
                break;
            case 0x04:
                // Temp input
                setting &= ~MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK;
                setting |= 0x00;
                break;
        }
        mMeter.meter_settings.chset[c] = setting;
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                refreshAllControls();
            }
        });
    }

    private void onUnitsClick(int c) {
        mMeter.disp_hex[c] ^= true;
        refreshAllControls();
    }

    private void onRangeClick(int c) {
        byte channel_setting = mMeter.meter_settings.chset[c];
        byte tmp;

        if(mMeter.disp_range_auto[c]) {
            return;
        }

        switch(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK) {
            case 0x00:
                // Electrode input
                switch(c) {
                    case 0:
                        // We are measuring current.  We can boost PGA, but that's all.
                        channel_setting = pga_cycle(channel_setting);
                        break;
                    case 1:
                        // Switch the ADC GPIO to activate dividers
                        tmp = (byte)((mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_GPIO_MASK)>>4);
                        tmp++;
                        tmp %= 3;
                        tmp<<=4;
                        mMeter.meter_settings.adc_settings &= ~MooshimeterDevice.ADC_SETTINGS_GPIO_MASK;
                        mMeter.meter_settings.adc_settings |= tmp;
                        channel_setting &= ~MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK;
                        channel_setting |= 0x10;
                        break;
                }
                break;
            case 0x04:
                // Temp input
                break;
            case 0x09:
                switch(mMeter.disp_ch3_mode) {
                case VOLTAGE:
                    channel_setting = pga_cycle(channel_setting);
                    break;
                case RESISTANCE:
                case DIODE:
                    channel_setting = pga_cycle(channel_setting);
                    tmp = (byte)(channel_setting & MooshimeterDevice.METER_CH_SETTINGS_PGA_MASK);
                    tmp >>=4;
                    if(tmp == 1) {
                        // Change the current source setting
                        mMeter.meter_settings.measure_settings ^= MooshimeterDevice.METER_MEASURE_SETTINGS_ISRC_LVL;
                    }
                    break;
            }
            break;
        }
        mMeter.meter_settings.chset[c] = channel_setting;
        mMeter.sendMeterSettings(new Block() {
            @Override
            public void run() {
                refreshAllControls();
            }
        });
    }

    public void onCh1DisplaySetClick(View v) {
        Log.i(null,"onCh1DisplaySetClick");
        onDisplaySetClick(0);
    }

    public void onCh1InputSetClick(View v) {
        Log.i(null,"onCh1InputSetClick");
        onInputSetClick(0);
    }

    public void onCh1RangeAutoClick(View v) {
        Log.i(null,"onCh1RangeAutoClick");
        mMeter.disp_range_auto[0] ^= true;
        refreshAllControls();
    }

    public void onCh1RangeClick(View v) {
        Log.i(null,"onCh1RangeClick");
        onRangeClick(0);
    }

    public void onCh1UnitsClick(View v) {
        Log.i(null,"onCh1UnitsClick");
        onUnitsClick(0);
    }

    public void onCh2DisplaySetClick(View v) {
        Log.i(null,"onCh2DisplaySetClick");
        onDisplaySetClick(1);
    }

    public void onCh2InputSetClick(View v) {
        Log.i(null,"onCh2InputSetClick");
        onInputSetClick(1);
    }

    public void onCh2RangeAutoClick(View v) {
        Log.i(null,"onCh2RangeAutoClick");
        mMeter.disp_range_auto[1] ^= true;
        refreshAllControls();
    }

    public void onCh2RangeClick(View v) {
        Log.i(null,"onCh2RangeClick");
        onRangeClick(1);
    }

    public void onCh2UnitsClick(View v) {
        Log.i(null, "onCh2UnitsClick");
        onUnitsClick(1);
    }

    public void onRateAutoClick(View v) {
        Log.i(null,"onRateAutoClick");
        mMeter.disp_rate_auto ^= true;
        refreshAllControls();
    }

    public void onRateClick(View v) {
        Log.i(null,"onRateClick");
        if(mMeter.disp_rate_auto) {
            // If auto is on, do nothing
        } else {
            byte rate_setting = (byte)(mMeter.meter_settings.adc_settings & MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK);
            rate_setting++;
            rate_setting %= 7;
            mMeter.meter_settings.adc_settings &= ~MooshimeterDevice.ADC_SETTINGS_SAMPLERATE_MASK;
            mMeter.meter_settings.adc_settings |= rate_setting;
            mMeter.sendMeterSettings(new Block() {
                @Override
                public void run() {
                    refreshAllControls();
                }
            });
        }
    }

    public void onLoggingClick(View v) {
        Log.i(null,"onLoggingClick");
        // TODO
    }

    public void onDepthAutoClick(View v) {
        Log.i(null,"onDepthAutoClick");
        mMeter.disp_depth_auto ^= true;
    }

    public void onDepthClick(View v) {
        Log.i(null,"onDepthClick");
        if(mMeter.disp_depth_auto) {
            // If auto is on, do nothing
        } else {
            byte depth_setting = (byte)(mMeter.meter_settings.calc_settings & MooshimeterDevice.METER_CALC_SETTINGS_DEPTH_LOG2);
            depth_setting++;
            depth_setting %= 9;
            mMeter.meter_settings.calc_settings &= ~MooshimeterDevice.METER_CALC_SETTINGS_DEPTH_LOG2;
            mMeter.meter_settings.calc_settings |= depth_setting;
            mMeter.sendMeterSettings(new Block() {
                @Override
                public void run() {
                    refreshAllControls();
                }
            });
        }
    }
    
    public void onZeroClick(View v) {
        Log.i(null,"onZeroClick");
        // TODO: Update firmware to allow saving of user offsets to flash
        // FIXME: Annoying special case: Channel 1 offset in current mode is stored as offset at the ADC
        // because current sense amp drift dominates the offset.  Hardware fix this in Rev2.
        // Toggle
        mMeter.offset_on ^= true;
        if(mMeter.offset_on) {
            byte channel_setting = (byte) (mMeter.meter_settings.chset[0] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK);
            switch(channel_setting) {
                case 0x00: // Electrode input
                    mMeter.offsets[0] = mMeter.lsbToADCInVoltage(mMeter.meter_sample.reading_lsb[0],0);
                    break;
                case 0x09:
                    mMeter.offsets[2] = mMeter.meter_sample.reading_lsb[0];
            }
            channel_setting = (byte) (mMeter.meter_settings.chset[1] & MooshimeterDevice.METER_CH_SETTINGS_INPUT_MASK);
            switch(channel_setting) {
                case 0x00: // Electrode input
                    mMeter.offsets[1] = mMeter.meter_sample.reading_lsb[1];
                    break;
                case 0x09:
                    mMeter.offsets[2] = mMeter.meter_sample.reading_lsb[1];
            }
        } else {
            for(int i=0; i<mMeter.offsets.length; i++) {mMeter.offsets[i]=0;}
        }
    }

}
