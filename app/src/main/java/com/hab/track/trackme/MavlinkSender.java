package com.hab.track.trackme;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.mavlink.messages.MAV_AUTOPILOT;
import org.mavlink.messages.MAV_MODE_FLAG;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.common.msg_gps_global_origin;
import org.mavlink.messages.common.msg_heartbeat;

import java.io.IOException;

/**
 * Created by adrienp on 2/4/2016.
 */
public class MavlinkSender extends IntentService {

    public MavlinkSender() {
        super("MavlinkSender");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        // Get UsbManager from Android.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Find the first available driver.
        UsbSerialDriver driver = UsbSerialProber.acquire(manager);

        int sequence = 1;
        long custom_mode = 2;

        if (driver != null) {
            try {
                driver.open();
                driver.setBaudRate(57600);

                while (true) {
                    // send a heartbeat message
                    msg_heartbeat hb = new msg_heartbeat(2, 12);
                    hb.sequence = sequence++;
                    hb.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_PX4;
                    hb.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_STABILIZE_ENABLED;
                    hb.custom_mode = custom_mode;
                    hb.system_status = MAV_STATE.MAV_STATE_POWEROFF;
                    byte[] result = hb.encode();

                    // write the heartbeat message
                    driver.write(result, result.length);

                    msg_gps_global_origin gps = new msg_gps_global_origin();
                    gps.altitude = 100;
                    gps.latitude = 200;
                    gps.longitude = 300;
                    result = gps.encode();
                    driver.write(result, result.length);
                }

            } catch (IOException e) {
                // Deal with error.
            } finally {
                try {
                    driver.close();
                } catch (IOException e) {
                    // who cares about this error...
                }
            }
        }


    }
}
