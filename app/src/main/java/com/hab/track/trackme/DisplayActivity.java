package com.hab.track.trackme;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import org.w3c.dom.Text;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by adrienp on 4/14/2016.
 */
public class DisplayActivity extends Activity {

    // DEBUG
    private static final String TAG = "DisplayActivity";

    // textviews
    private TextView mLatText;
    private TextView mLonText;
    private TextView mAltText;
    private TextView mDetailsText;
    private TextView mStatusText;


    private BroadcastReceiver mMavlinkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                double lat = bundle.getDouble(MavlinkSendService.LATITUDE);
                double lon = bundle.getDouble(MavlinkSendService.LONGITUDE);
                double alt = bundle.getDouble(MavlinkSendService.ALTITUDE);

                // set these values to the textview
                mLatText.setText(Double.toString(lat));
                mLonText.setText(Double.toString(lon));
                mAltText.setText(Double.toString(alt));
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        // get all the textviews
        mLatText = (TextView) findViewById(R.id.display_lat_value);
        mLonText = (TextView) findViewById(R.id.display_lon_value);
        mAltText = (TextView) findViewById(R.id.display_alt_value);
        mDetailsText = (TextView) findViewById(R.id.gps_details);
        mStatusText = (TextView) findViewById(R.id.gps_display);

        // configure the stop button
        Button button = (Button) findViewById(R.id.send_stop);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // stop the service
                Intent intent = new Intent(DisplayActivity.this, MavlinkSendService.class);
                stopService(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register the broadcast receiver here
        LocalBroadcastManager.getInstance(DisplayActivity.this).registerReceiver(mMavlinkReceiver, new IntentFilter(MavlinkSendService.GPS_UPDATE_NOTIFICATION));
    }

    @Override
    protected void onPause() {
        super.onPause();

        // unregister the broadcast receiver here
        LocalBroadcastManager.getInstance(DisplayActivity.this).unregisterReceiver(mMavlinkReceiver);
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


}
