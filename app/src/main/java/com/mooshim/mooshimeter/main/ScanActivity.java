package com.mooshim.mooshimeter.main;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.BLEUtil;
import com.mooshim.mooshimeter.common.BleDeviceInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by First on 2/4/2015.
 */
public class ScanActivity extends FragmentActivity {
    // Defines
    private static String TAG = "ScanActivity";
    private enum ScanViewState {
        IDLE,
        SCANNING,
        CONNECTING,
    };
    private static final int NO_DEVICE = -1;

    private static final int REQ_ENABLE_BT = 0;
    private static final int REQ_DEVICE_ACT = 1;

    // Housekeeping
    private ScanViewState mScanViewState = ScanViewState.IDLE;
    private Timer mTimer = null;

    private int mConnIndex = NO_DEVICE;         // The list index of the connected device
    private List<BleDeviceInfo> mDeviceInfoList;// The list of detected Mooshimeters
    private BLEUtil mBleUtil = null;            // The singleton BLEUtil.  Used for managing connection


    // GUI Widgets
    private DeviceListAdapter mDeviceAdapter = null;
    private TextView mEmptyMsg;
    private TextView mStatus;
    private Button mBtnScan = null;
    private ListView mDeviceListView = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
        }

        // Checks if Bluetooth is supported on the device.
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_LONG).show();
            setError("BLE not supported on this device");
        }

        if( !BluetoothAdapter.getDefaultAdapter().isEnabled() ) {
            // Request BT adapter to be turned on
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQ_ENABLE_BT);
        }

        setContentView(R.layout.fragment_scan);
        // Initialize widgets
        mStatus         = (TextView) findViewById(R.id.status);
        mBtnScan        = (Button)   findViewById(R.id.btn_scan);
        mDeviceListView = (ListView) findViewById(R.id.device_list);
        mEmptyMsg       = (TextView) findViewById(R.id.no_device);

        mDeviceListView.setClickable(true);
        mDeviceListView.setOnItemClickListener(mDeviceClickListener);

        mBleUtil = BLEUtil.getInstance(this);
        mDeviceInfoList = new ArrayList<BleDeviceInfo>();
        mDeviceAdapter = new DeviceListAdapter(this);
        mDeviceListView.setAdapter(mDeviceAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        moveState(ScanViewState.SCANNING);
    }

    // Master state machine for the scan view

    private void moveState(ScanViewState newState) {
        switch(mScanViewState) {
            case IDLE:
                switch(newState) {
                    case SCANNING:
                        stopTimer();
                        mTimer = new Timer();
                        mTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        moveState(ScanViewState.IDLE);
                                    }
                                });
                            }
                        }, 10000);
                        updateScanningButton(true);
                        mDeviceInfoList.clear();
                        mDeviceAdapter.notifyDataSetChanged();
                        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        if( !bluetoothAdapter.startLeScan(mLeScanCallback) ) {
                            // Starting the scan failed!
                            Log.e(TAG,"Failed to start BLE Scan");
                        }
                        break;
                    case CONNECTING:
                        stopTimer();
                        mTimer = new Timer();
                        mTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        moveState(ScanViewState.IDLE);
                                        setStatus("Connection timed out.");
                                    }
                                });
                            }
                        }, 10000);
                        mBtnScan.setEnabled(false);
                        mDeviceAdapter.notifyDataSetChanged(); // Force disabling of all Connect buttons
                        setStatus("Connecting...");
                        final BluetoothDevice bluetoothDevice   = mDeviceInfoList.get(mConnIndex).getBluetoothDevice();
                        mBleUtil.clear(); // fixme shouldn't be necessary
                        mBleUtil.connect(bluetoothDevice.getAddress(), new BLEUtil.BLEUtilCB() {
                            @Override
                            public void run() {
                                stopTimer();
                                if (error == BluetoothGatt.GATT_SUCCESS) {
                                    startDeviceActivity();
                                } else	{
                                    setError("Connect failed. Status: " + error);
                                    moveState(ScanViewState.IDLE);
                                }
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                mBleUtil.clear();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setError("Device disconnected!");
                                        moveState(ScanViewState.IDLE);
                                        stopDeviceActivity();
                                    }
                                });
                            }
                        });
                        break;
                    default:
                        Log.e(TAG, "Illegal transition");
                        break;
                }
                break;
            case SCANNING:
                switch(newState) {
                    case IDLE:
                        updateScanningButton(false);
                        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                        bluetoothAdapter.stopLeScan(mLeScanCallback);
                        break;
                    default:
                        Log.e(TAG, "Illegal transition");
                        break;
                }
                break;
            case CONNECTING:
                switch(newState) {
                    case IDLE:
                        mBleUtil.disconnect();
                        mBtnScan.setEnabled(true);
                        mDeviceAdapter.notifyDataSetChanged(); // Force enabling of all Connect buttons
                        break;
                    default:
                        Log.e(TAG, "Illegal transition");
                        break;
                }
                break;
        }
        mScanViewState = newState;
    }




    /////////////////////////////
    // GUI Element Manipulation
    /////////////////////////////

    void updateScanningButton(boolean scanning) {
        if (mBtnScan == null)
            return; // UI not ready
        if (scanning) {
            // Indicate that scanning has started
            mBtnScan.setText("Stop");
            mBtnScan.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_action_cancel, 0);
            mStatus.setTextAppearance(this, R.style.statusStyle_Busy);
            mStatus.setText("Scanning...");
            mEmptyMsg.setText(R.string.nodevice);
            if(mDeviceInfoList.size() == 0) {
                mEmptyMsg.setVisibility(View.VISIBLE);
            } else {
                mEmptyMsg.setVisibility(View.GONE);
            }
        } else {
            // Indicate that scanning has stopped
            mStatus.setTextAppearance(this, R.style.statusStyle_Success);
            mBtnScan.setText("Scan");
            mBtnScan.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_action_refresh, 0);
            mEmptyMsg.setText(R.string.scan_advice);
            mDeviceAdapter.notifyDataSetChanged();
        }
    }

    void setStatus(String txt) {
        mStatus.setText(txt);
        mStatus.setTextAppearance(this, R.style.statusStyle_Success);
    }

    void setError(String txt) {
        mStatus.setText(txt);
        mStatus.setTextAppearance(this, R.style.statusStyle_Failure);
    }

    private void addDevice(BleDeviceInfo device) {
        mEmptyMsg.setVisibility(View.GONE);
        mDeviceInfoList.add(device);
        mDeviceAdapter.notifyDataSetChanged();
        if (mDeviceInfoList.size() > 1)
            setStatus(mDeviceInfoList.size() + " devices");
        else
            setStatus("1 device");
    }

    /////////////////////////////
    // Data Structure Manipulation
    /////////////////////////////

    private boolean deviceInfoExists(String address) {
        return findDeviceInfo(address) != null;
    }

    private BleDeviceInfo findDeviceInfo(String address) {
        for (int i = 0; i < mDeviceInfoList.size(); i++) {
            if (mDeviceInfoList.get(i).getBluetoothDevice().getAddress().equals(address)) {
                return mDeviceInfoList.get(i);
            }
        }
        return null;
    }

    void stopTimer() {
        if(mTimer!=null){mTimer.cancel();}
    }

    private void startDeviceActivity() {
        Intent deviceIntent = new Intent(this, DeviceActivity.class);
        startActivityForResult(deviceIntent, REQ_DEVICE_ACT);
    }
    private void stopDeviceActivity() {
        finishActivity(REQ_DEVICE_ACT);
    }

    /////////////////////////////
    // Listeners for BLE Events
    /////////////////////////////

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // Filter devices
                    // FIXME: Android doesn't seem to be filtering devices correctly based on UUIDs
                    // FIXME: For now I will examine the scan record manually
                    boolean is_meter = false;
                    int build_time = 0;
                    int field_length = 0;
                    int field_id = 0;
                    for(int i = 0; i < scanRecord.length;) {
                        if(field_length == 0) {
                            field_length = scanRecord[i] & 0xFF;
                            field_id = 0;
                            i++;
                        }
                        else if(field_id == 0) {
                            field_id = scanRecord[i] & 0xFF;
                            field_length--;
                            i++;
                        } else {
                            switch(field_id) {
                                case 6:
                                    // This is a UUID listing
                                    // TODO: Move these hardcoded values to a seperate file
                                    final byte[] moosh_service_uuid = {(byte)0xd4, (byte)0xdb, (byte)0x05, (byte)0xe0, (byte)0x54, (byte)0xf2, (byte)0x11, (byte)0xe4, (byte)0xab, (byte)0x62, (byte)0x00, (byte)0x02, (byte)0xa0, (byte)0xff, (byte)0xc5, (byte)0x1b};
                                    // Check expected length
                                    if(field_length != 16) { break; }
                                    // Check that there's enough data in the buffer
                                    if(i+field_length >= scanRecord.length) {break;}
                                    // Check the value against the expected service UUID
                                    byte[] received_uuid = Arrays.copyOfRange(scanRecord, i, i + field_length);
                                    if(Arrays.equals(received_uuid, moosh_service_uuid)) {
                                        Log.i(null, "Mooshimeter found");
                                        is_meter = true;
                                    } else {
                                        Log.i(null, "Scanned device is not a meter");
                                    }
                                    break;
                                case 255:
                                    // This is a manufacturer-specific data listing
                                    // In the case of the Mooshimeter this is the build time
                                    // in UTC seconds
                                    // Check expected length
                                    if(field_length != 4) { break; }
                                    // Check that there's enough data in the buffer
                                    if(i+field_length >= scanRecord.length) {break;}
                                    for(int j = 3; j >= 0; j--) {
                                        build_time |= (scanRecord[i+j] & 0xFF) << (8*j);
                                    }
                                    break;
                            }
                            // Cleanup
                            i += field_length;
                            field_length = 0;
                            field_id = 0;
                        }
                    }

                    // TODO: Check the firmware version against the bundled binary and prompt for upload if meter is out of date
                    // TODO: Display the firmware version in the scan result
                    if(is_meter) {
                        if (!deviceInfoExists(device.getAddress())) {
                            // New device
                            BleDeviceInfo deviceInfo = new BleDeviceInfo(device, rssi, build_time);
                            addDevice(deviceInfo);
                        } else {
                            // Already in list, update RSSI info
                            BleDeviceInfo deviceInfo = findDeviceInfo(device.getAddress());
                            deviceInfo.updateRssi(rssi);
                            mDeviceAdapter.notifyDataSetChanged();
                        }
                    }
                }
            });
        }
    };

    /////////////////////////////
    // Listeners for GUI Events
    /////////////////////////////

    public void onBtnScan(View view) {
        switch(mScanViewState) {
            case IDLE:
                moveState(ScanViewState.SCANNING);
                break;
            case SCANNING:
                moveState(ScanViewState.IDLE);
                break;
            case CONNECTING:
                Log.e(TAG,"This button should be disabled when connecting");
                break;
        }
    }

    // Listener for device list
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
            mConnIndex = pos;
            moveState(ScanViewState.IDLE);
            moveState(ScanViewState.CONNECTING);
        }
    };

    class DeviceListAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public DeviceListAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return mDeviceInfoList.size();
        }

        public Object getItem(int position) {
            return mDeviceInfoList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) mInflater.inflate(R.layout.element_device, null);
            }

            BleDeviceInfo deviceInfo = mDeviceInfoList.get(position);
            BluetoothDevice device = deviceInfo.getBluetoothDevice();
            int rssi = deviceInfo.getRssi();
            String name;
            name = device.getName();
            if (name == null) {
                name = new String("Unknown device");
            }

            String descr = name + "\n" + device.getAddress() + "\nRssi: " + rssi + " dBm";
            ((TextView) vg.findViewById(R.id.descr)).setText(descr);

            // Disable connect button when connecting or connected
            Button bv = (Button)vg.findViewById(R.id.btnConnect);
            bv.setEnabled(mScanViewState != ScanViewState.CONNECTING);

            return vg;
        }
    }
}
