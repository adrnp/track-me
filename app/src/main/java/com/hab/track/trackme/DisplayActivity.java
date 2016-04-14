package com.hab.track.trackme;

import android.Manifest;
import android.app.Activity;
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

import org.mavlink.messages.MAV_AUTOPILOT;
import org.mavlink.messages.MAV_MODE_FLAG;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.common.msg_gps_raw_int;
import org.mavlink.messages.common.msg_heartbeat;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by adrienp on 4/14/2016.
 */
public class DisplayActivity extends AppCompatActivity implements GpsStatus.Listener {

    // debug
    private static final String TAG = "DisplayActivity";

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // set up the toggle button
        ToggleButton toggle = (ToggleButton) findViewById(R.id.startStopToggle);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    startGettingGPSPosition();
                    //startMavlinkStream();
                    readData();

                } else {
                    // The toggle is disabled
                    stopGettingGPSPosition();
                    //stopMavlinkStream();
                }
            }
        });

        // get the textviews
        mDisplayText = (TextView) findViewById(R.id.gps_display);
        mDetailText = (TextView) findViewById(R.id.gps_details);

        // get the usb manager
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }


    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                mDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (mDevice != null) {
                    Log.d("onResume", "USB device attached: name: " + mDevice.getDeviceName());
                    mDetailText.setText("USB device attached: name: " + mDevice.getDeviceName());
                    mConnection = mUsbManager.openDevice(mDevice);
                }
            }
        }
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


    // turn on the getting and sending of the GPS position
    private void startGettingGPSPosition() {
        mDisplayText.setText("acquiring position...");

        // check for permission to access location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mDisplayText.setText("do not have permission to access location information");
            return;
        }

        // Acquire a reference to the system Location Manager
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);


        mLocationManager.addGpsStatusListener(this);

        // Define a listener that responds to location updates
        mLocationListener = new LocationListener() {

            public void onLocationChanged(Location location) {
                mDisplayText.setText(location.toString());

                int sequence = 0;
                long custom_mode = 3;

                msg_heartbeat hb = new msg_heartbeat(2, 12);
                hb.sequence = sequence++;
                hb.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_PX4;
                hb.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_STABILIZE_ENABLED;
                hb.custom_mode = custom_mode;
                hb.system_status = MAV_STATE.MAV_STATE_POWEROFF;
                byte[] result = new byte[0];
                try {
                    result = hb.encode();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // write the heartbeat message
                mSerialPort.write(result);

                int lat = (int) (location.getLatitude()*10000000.);
                int lon = (int) (location.getLongitude()*10000000.);
                int alt = (int) (location.getAltitude()*1000.);

                msg_gps_raw_int gps = new msg_gps_raw_int();
                gps.alt = alt;
                gps.lat = lat;
                gps.lon = lon;
                result = new byte[0];
                try {
                    result = gps.encode();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mSerialPort.write(result);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        // Register the listener with the Location Manager to receive location updates
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);

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

                int sequence = 0;
                long custom_mode = 3;

                msg_heartbeat hb = new msg_heartbeat(2, 12);
                hb.sequence = sequence++;
                hb.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_PX4;
                hb.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_STABILIZE_ENABLED;
                hb.custom_mode = custom_mode;
                hb.system_status = MAV_STATE.MAV_STATE_POWEROFF;
                byte[] result = new byte[0];
                try {
                    result = hb.encode();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // write the heartbeat message
                mSerialPort.write(result);

            } else {
                // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                // Send an Intent to Main Activity

                mDetailText.setText("failed to open");

            }
        } else {
            // No driver for given device, even generic CDC driver could not be loaded
            mDetailText.setText("no serial port!");
        }

        /*
        // Get UsbManager from Android.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Find the first available driver.
        UsbSerialDriver driver = UsbSerialProber.acquire(manager);

        if (driver != null) {
            try {
                driver.open();
                driver.setBaudRate(57600);
                byte[] result = new byte[500];

                for (int i = 1; i < 100; i++) {
                    int numBytes = driver.read(result, 500);
                    mDetailText.setText("number of bytes read: " + Integer.toString(numBytes) + "\n" + result);
                }

            } catch (IOException e) {
                // Deal with error.
                mDetailText.setText(e.toString());
            } finally {
                try {
                    driver.close();
                } catch (IOException e) {
                    // who cares about this error...
                }
            }
        } else {
            mDetailText.setText("driver is null");
        }
        */
    }

    private void startMavlinkStream() {
        // start the intent service
        mMavlinkService = new Intent(this, MavlinkSender.class);
        startService(mMavlinkService);
    }


    private void stopMavlinkStream() {
        stopService(mMavlinkService);
    }


    // turn on the getting and sending of the GPS position
    private void stopGettingGPSPosition() {

        mDisplayText.setText("ended");

        // check for permission to access location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mDisplayText.setText("do not have permission to access location information");
            return;
        }

        mLocationManager.removeUpdates(mLocationListener);

    }


    @Override
    public void onGpsStatusChanged(int event) {

        // get the details of the gps satellites
        GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
        String satDetails = "";
        if (gpsStatus != null) {
            Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
            Iterator<GpsSatellite> sat = satellites.iterator();
            int i = 0;
            while (sat.hasNext()) {
                GpsSatellite satellite = sat.next();
                satDetails += (i++) + ": " + satellite.getPrn() + "," + satellite.usedInFix() + "," + satellite.getSnr() + "," + satellite.getAzimuth() + "," + satellite.getElevation() + "\n";
            }

            if (!gettingPos) {
                mDetailText.setText(satDetails);
            }
        }
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    /*
    private void requestUserPermission() {
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPendingIntent);
    } */

}
