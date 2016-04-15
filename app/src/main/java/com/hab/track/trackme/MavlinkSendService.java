package com.hab.track.trackme;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.mavlink.messages.MAV_AUTOPILOT;
import org.mavlink.messages.MAV_MODE_FLAG;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.common.msg_gps_raw_int;
import org.mavlink.messages.common.msg_heartbeat;

import java.io.IOException;

/**
 * Created by adrienp on 4/15/2016.
 */
public class MavlinkSendService extends Service {

    /** binder to give to clients */
    private final IBinder mBinder = new sendBinder();


    // location stuff
    private LocationManager mLocationManager;
    LocationListener mLocationListener;

    // usb stuff
    UsbManager mUsbManager;

    // UsbSerial stuff
    /** reference to the actual usb device itself, first thing needed */
    private UsbDevice mDevice;

    /** given a usb device, create a connection to the device */
    private UsbDeviceConnection mConnection;

    /** given a connection and a device, can then create a serial port from it to handle serial read and write */
    private UsbSerialDevice mSerialPort;

    // config stuff
    /** buadrate */
    private int mBaudrate = 57600;


    // globals to send to activity
    private double mLat;
    private double mLon;
    private double mAlt;
    private float mSignalStrength;


    @Override
    public void onCreate() {
        super.onCreate();

        // get the usb manager
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    /**
     * implementation of the onBind method.
     * Simply returns a sendBinder to access service methods.
     * @param intent the intent (not used)
     * @return sendBinder binder
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    // probably need to override the onstartcommand method too


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // get the device
        mDevice = intent.getParcelableExtra("usb_device");

        // now need to configure and connect to the device
        if (mDevice != null) {
            Log.d("MavlinkSendService", "USB device attached: name: " + mDevice.getDeviceName());
            mConnection = mUsbManager.openDevice(mDevice);
        }

        // configure the serial port
        setupSerialPort();

        // this is when we want to start shit up
        configureGPSListener();


        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // need to close connection to serial device...
        // check for permission to access location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: log error
            return;
        }

        // stop listening to the gps
        mLocationManager.removeUpdates(mLocationListener);

        // TODO: close serial port
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }


    // functions for bound activity to get

    public double getLatitude() {
        return mLat;
    }

    public double getLongitude() {
        return mLon;
    }

    public double getAltitude() {
        return mAlt;
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

        // Define a listener that responds to location updates
        mLocationListener = new LocationListener() {

            public void onLocationChanged(Location location) {

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

                mLat = location.getLatitude();
                mLon = location.getLongitude();
                mAlt = location.getAltitude();
                mSignalStrength = 0;

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

                // TODO: log working

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
                // TODO: log error

            }
        } else {
            // No driver for given device, even generic CDC driver could not be loaded
            // TODO: log error
        }
    }



    /**
     * classed used for the client Binder.
     */
    public class sendBinder extends Binder {

        /**
         * binder function to be able to access the service,
         * and therefore the public methods in the service.
         * @return  MavlinkSendService object - to use public methods
         */
        MavlinkSendService getService() {
            return MavlinkSendService.this;
        }
    }

}
