package com.hab.track.trackme;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.mavlink.MAVLinkReader;
import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.MAV_AUTOPILOT;
import org.mavlink.messages.MAV_MODE_FLAG;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.common.msg_gps_raw_int;
import org.mavlink.messages.common.msg_heartbeat;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by adrienp on 4/15/2016.
 */
public class ReceiveDisplayActivity extends AppCompatActivity {

    // debug
    private static final String TAG = "ReceiveDisplayActivity";

    // textviews for displaying info
    private TextView mDisplayText;
    private TextView mDetailText;


    // location stuff
    private LocationManager mLocationManager;
    LocationListener mLocationListener;

    // mavlink stuff
    private Intent mMavlinkService;

    // usb stuff
    UsbManager mUsbManager;

    // UsbSerial stuff
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbSerialDevice mSerialPort;

    // status boolean
    private boolean gettingPos = false;

    MAVLinkReader mMavReader;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        // set up the toggle button
        ToggleButton toggle = (ToggleButton) findViewById(R.id.startStopToggle);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled

                    readData();

                } else {
                    // The toggle is disabled

                }
            }
        });

        // get the textviews
        mDisplayText = (TextView) findViewById(R.id.gps_display);
        mDetailText = (TextView) findViewById(R.id.gps_details);

        // get the usb manager
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // get the device
        mDevice = getIntent().getParcelableExtra("usb_device");

        // now need to configure and connect to the device
        if (mDevice != null) {
            Log.d("MavlinkSendService", "USB device attached: name: " + mDevice.getDeviceName());
            mConnection = mUsbManager.openDevice(mDevice);
        }


        // create a new reader
        mMavReader = new MAVLinkReader();
    }


    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void readData() {

        // should have our device now
        mSerialPort = UsbSerialDevice.createUsbSerialDevice(mDevice, mConnection);
        if (mSerialPort != null) {
            if (mSerialPort.open()) {
                mSerialPort.setBaudRate(57600);
                mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

                mDetailText.setText("shit it worked!");


                // write the heartbeat message
                mSerialPort.read(new UsbSerialInterface.UsbReadCallback() {
                    @Override
                    public void onReceivedData(byte[] bytes) {
                        mDetailText.setText("attempting to read message");
                        // parse data here
                        try {
                            MAVLinkMessage msg = mMavReader.getNextMessage(bytes, bytes.length);

                            mDisplayText.setText("received message of type: " + msg.messageType);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });

            } else {
                // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                // Send an Intent to Main Activity

                mDetailText.setText("failed to open");

            }
        } else {
            // No driver for given device, even generic CDC driver could not be loaded
            mDetailText.setText("no serial port!");
        }

    }



}
