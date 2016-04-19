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
    private final IBinder mBinder = new SendBinder();


    // location stuff
    private LocationManager mLocationManager;
    LocationListener mLocationListener = new MyLocationListener();

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

    /**
     * classed used for the client Binder.
     */
    public class SendBinder extends Binder {

        /**
         * binder function to be able to access the service,
         * and therefore the public methods in the service.
         * @return  MavlinkSendService object - to use public methods
         */
        MavlinkSendService getService() {
            return MavlinkSendService.this;
        }
    }


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


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // get passed information
        mDevice = intent.getParcelableExtra("usb_device");  // the usb device
        mBaudrate = intent.getIntExtra("baudrate", 57600);  // the baudrate for the serial connection

        // now need to configure and connect to the device
        if (mDevice != null) {
            Log.d("MavlinkSendService", "USB device attached: name: " + mDevice.getDeviceName());
            mConnection = mUsbManager.openDevice(mDevice);
        }

        // configure the serial port
        setupSerialPort();

        // this is when we want to start shit up
        //configureGPSListener();


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

        // close the serial port (if it was opened)
        if (mSerialPort != null) {
            mSerialPort.close();
        }
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

    /**
     * get the most recent latitude update - for display activity.
     * @return  double latitude (in degrees)
     */
    public double getLatitude() {
        return mLat;
    }

    /**
     * get the most recent longitude update - for display activity.
     * @return  double longitude (in degrees)
     */
    public double getLongitude() {
        return mLon;
    }

    /**
     * get the most recent altitude update - for display activity.
     * @return  double altitude (in ft?)
     */
    public double getAltitude() {
        return mAlt;
    }


    /**
     * configure everything for listening to the gps
     */
    private void configureGPSListener() {

        // check for permission to access location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Acquire a reference to the system Location Manager
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // request updates
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    }




    /**
     * setup and configure the serial port.
     * sends out a heartbeat message once the serial port is configured.
     */
    private void setupSerialPort() {

        // should have our device now
        mSerialPort = UsbSerialDevice.createUsbSerialDevice(mDevice, mConnection);

        // if serial port failed to create, just exit
        if (mSerialPort == null) {
            // TODO: log error
            return;
        }

        // open and configure serial connection and send a heartbeat out
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
    }


    /**
     * LocationListener implementation to handle location updates.
     *
     * When location is updated, send a mavlink message out with the new location.
     * Note: this is effectively rate limited by the setting when registering the location
     * listeners for updates, so there is no rate limiting here.
     * If you really wanted to, you could probably throw something here, but ideally should
     * be adjusting it at the registration side for battery performance reasons.
     */
    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            int sequence = 0;
            long custom_mode = 3;

            // send a heartbeat with each gps message
            // this is because radio messages are added after every heartbeat
            // TODO: move heartbeat sending outside of here at a regular interval
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

            // get the GPS data for the gps message and format it accordingly
            mLat = location.getLatitude();
            mLon = location.getLongitude();
            mAlt = location.getAltitude();
            mSignalStrength = 0;

            int lat = (int) (location.getLatitude() * 10000000.);
            int lon = (int) (location.getLongitude() * 10000000.);
            int alt = (int) (location.getAltitude() * 1000.);

            // build and send the gps message
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

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

}
