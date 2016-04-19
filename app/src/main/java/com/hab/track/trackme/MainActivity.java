package com.hab.track.trackme;

import android.Manifest;
import android.app.IntentService;
import android.app.PendingIntent;
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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;
import android.widget.ViewSwitcher;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.mavlink.MAVLinkReader;
import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.MAV_AUTOPILOT;
import org.mavlink.messages.MAV_MODE_FLAG;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.common.msg_gps_global_origin;
import org.mavlink.messages.common.msg_gps_raw_int;
import org.mavlink.messages.common.msg_heartbeat;
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements GpsStatus.Listener, View.OnClickListener {

    // debug
    private static final String TAG = "MainActivity";

    // usb stuff
    UsbManager mUsbManager;

    // UsbSerial stuff
    /** reference to the actual usb device itself, first thing needed */
    private UsbDevice mDevice;

    /** given a usb device, create a connection to the device */
    private UsbDeviceConnection mConnection;

    /** given a connection and a device, can then create a serial port from it to handle serial read and write */
    private UsbSerialDevice mSerialPort;

    // status stuff
    private TextView mStatusText;
    private TextView mDetailText;
    private TextView mExtrasText;

    // config stuff
    /** mode of operation selected */
    public static final int MODE_GROUND = 0;
    public static final int MODE_AIR = 1;
    private int mMode = MODE_GROUND;

    /** buadrate */
    private int mBaudrate = 57600;

    // location stuff
    private LocationManager mLocationManager;
    LocationListener mLocationListener;


    private ViewFlipper mSwitcher;

    MAVLinkReader mMavReader;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // get the switcher
        mSwitcher = (ViewFlipper) findViewById(R.id.main_switcher);

        // add listeners to all the buttons
        findViewById(R.id.mode_button).setOnClickListener(this);
        findViewById(R.id.start_button).setOnClickListener(this);
        findViewById(R.id.stop_button).setOnClickListener(this);

        // setup the mode spinner
        Spinner modeSpinner = (Spinner) findViewById(R.id.mode_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.mode_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mStatusText.setText("selected option " + position);
                mMode = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        // get the status textviews - so others can update it
        mStatusText = (TextView) findViewById(R.id.main_status_text);
        mDetailText = (TextView) findViewById(R.id.main_details_text);
        mExtrasText = (TextView) findViewById(R.id.main_extra_details_text);

        // TESTING
        // get the usb manager
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // TESTING
        // create a new reader
        mMavReader = new MAVLinkReader();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // retrieve the USB device that has been plugged in
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                mDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE); // get the actual device!
                if (mDevice != null) {
                    Log.d("onResume", "USB device attached: name: " + mDevice.getDeviceName());
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


    /**
     * setup and configure the serial port.
     * sends out a heartbeat message once the serial port is configured.
     */
    private void setupSerialPort() {

        // should have our device now
        mSerialPort = UsbSerialDevice.createUsbSerialDevice(mDevice, mConnection);
        if (mSerialPort != null) {
            if (mSerialPort.open()) {
                mSerialPort.setBaudRate(mBaudrate);
                mSerialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                mSerialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                mSerialPort.setParity(UsbSerialInterface.PARITY_NONE);
                mSerialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

                mStatusText.setText("shit it worked! with baudrate of " + mBaudrate);

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


                // write the heartbeat message
                /*
                mSerialPort.read(new UsbSerialInterface.UsbReadCallback() {
                    @Override
                    public void onReceivedData(byte[] bytes) {
                        mDetailText.setText("attempting to read message");
                        // parse data here
                        try {
                            MAVLinkMessage msg = mMavReader.getNextMessage(bytes, bytes.length);

                            mStatusText.setText("received message of type: " + msg.messageType);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
                */
            } else {
                // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                // Send an Intent to Main Activity

                mStatusText.setText("failed to open");

            }
        } else {
            // No driver for given device, even generic CDC driver could not be loaded
            mStatusText.setText("no serial port!");
        }
    }

    /**
     * configure everything for listening to the gps
     */
    private void configureGPSListener() {

        /*
        // Register the listener with the Location Manager to receive location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        } */
        // check for permission to access location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Acquire a reference to the system Location Manager
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        mLocationManager.addGpsStatusListener(this);

        // Define a listener that responds to location updates
        mLocationListener = new LocationListener() {

            public void onLocationChanged(Location location) {

                mDetailText.setText(location.toString());

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

                int lat = (int) (location.getLatitude() * 10000000.);
                int lon = (int) (location.getLongitude() * 10000000.);
                int alt = (int) (location.getAltitude() * 1000.);

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

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    }

    /**
     * disable the location listener, which effectively stops sending position updates
     */
    private void stopGettingGPSPosition() {

        mStatusText.setText("ended");

        // check for permission to access location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mStatusText.setText("do not have permission to access location information");
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

            mExtrasText.setText(satDetails);
        }
    }


    /**
     * override of the onClick method for all the buttons that can be clicked.
     *
     * Moved everything here just to keep it a bit cleaner.
     * @param v  view that was clicked
     */
    @Override
    public void onClick(View v) {

        // actual code will depend on what view was clicked
        switch (v.getId()) {
            case R.id.mode_button:

                // show the next view
                mSwitcher.showNext(); // show the next view

                break;
            case R.id.start_button:

                // TODO: change what is done based on the mode of operation
                switch (mMode) {
                    case MODE_AIR:
                        // start sending service
                        break;
                    case MODE_GROUND:
                        // start listening service
                        break;
                }

                // start the sending service
                // TODO: move this into MODE_AIR
                Intent intent = new Intent(MainActivity.this, MavlinkSendService.class);
                intent.putExtra("usb_device", mDevice); // send the device to the service
                startService(intent);

                // either way probably show the same display activity for now....
                // or maybe have 2 display activities....
                // TODO: move this into MODE_AIR
                intent = new Intent(MainActivity.this, ReceiveDisplayActivity.class);
                intent.putExtra("usb_device", mDevice); // send the device to the service
                //startActivity(intent);

                // TESTING
                // have a third view to simply display that has a stop button for testing
                // also can use the setup serial port and config listener functions to test what
                // will eventually be in the services
                //mSwitcher.showNext();
                //setupSerialPort();
                //configureGPSListener();

                break;
            case R.id.stop_button:
                // this is an "emergency stop" button used with the third testing layout

                Toast.makeText(MainActivity.this, "stopping...", Toast.LENGTH_LONG).show();

                // stop the mavlink stuff and go back to previous view
                mSwitcher.showPrevious();
                stopGettingGPSPosition();

                break;
        }

    }
}
