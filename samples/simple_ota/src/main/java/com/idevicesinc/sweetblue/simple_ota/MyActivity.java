/*

  Copyright 2022 Hubbell Incorporated

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.

  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

 */

package com.idevicesinc.sweetblue.simple_ota;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig;
import com.idevicesinc.sweetblue.BleTransaction;
import com.idevicesinc.sweetblue.BleWrite;
import com.idevicesinc.sweetblue.BuildConfig;
import com.idevicesinc.sweetblue.DiscoveryListener;
import com.idevicesinc.sweetblue.LogOptions;
import com.idevicesinc.sweetblue.MtuTestCallback;
import com.idevicesinc.sweetblue.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleSetupHelper;
import com.idevicesinc.sweetblue.utils.Uuids;

import java.io.File;
//import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
//import java.io.IOException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A very simple example which shows how to scan for BLE devices, connect to the first one seen, and then perform an over-the-air (OTA) firmware update.
 */
public class MyActivity extends Activity
{
    private BleManager m_bleManager;
    private BleDevice m_bleDevice;
    private boolean m_discovered = false;
    private static final UUID GB_FIRMWARE_UUID = UUID.fromString("23408888-1f40-4cd8-9b89-ca8d45f8a5b0");// what Obe had given me: d6f1d96d-594c-4c53-b1c6-244a1dfde6d8");
    private static final String GEL_BLASTER_UUID ="000000ff-0000-1000-8000-00805f9b34fb";
    private static URL gelBlasterFirmwareUrl = null;
    static {
        try {
            gelBlasterFirmwareUrl = new URL("https://gp-firmware-test.s3.amazonaws.com/hello_world.bin");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private DownloadManager downloadManager;
    private final String firmwareFileName = "GelBlasterFirmware";
    private long firmwareID;
    private File firmwareFile;

    // There's really no need to keep this up here, it's just here for convenience. Here for sample data.
    private static final byte[] MY_DATA = {(byte) 0xC0, (byte) 0xFF, (byte) 0xEE};//  NOTE: Replace with your actual data, not 0xC0FFEE.

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (GB_FIRMWARE_UUID.equals(Uuids.INVALID))
            throw new RuntimeException("You need to set a valid UUID for MY_UUID!");
        startScan();
    }

    private void startScan()
    {
        BleManagerConfig config = new BleManagerConfig();
        // Only enable logging in debug builds
        config.loggingOptions = BuildConfig.DEBUG ? LogOptions.ON : LogOptions.OFF;

        m_bleManager = BleManager.get(this, config);

        // Set the discovery listener. You can pass in a listener when calling startScan() if you want, but in most cases, this is preferred so you don't
        // have to keep passing in the listener when calling any of the startScan methods.
        m_bleManager.setListener_Discovery(this::onDeviceDiscovered);
        BleSetupHelper.runEnabler(m_bleManager, this, result ->
        {
            if (result.getSuccessful())
            {
                m_bleManager.startScan();
            }
        });
    }

    /**
     * Connects to a specified BLE based on the UUID value assigned.
     *
     * @param discoveryEvent
     */
    private void onDeviceDiscovered(DiscoveryListener.DiscoveryEvent discoveryEvent){
        if(!m_discovered)
        {
            BleDevice temp = discoveryEvent.device();
            String uuidOfTempDevice = "";
            for(int i = 0; i < temp.getAdvertisedServices().length; i++)
            {
                uuidOfTempDevice += temp.getAdvertisedServices()[i];
            }

            if(uuidOfTempDevice.equals(GEL_BLASTER_UUID))
            {
                m_bleManager.stopScan();
                m_bleDevice = temp;
                m_discovered = true;

                Log.i("Medrano: ", "UUIDs matched, BLE Device is " + m_bleDevice.getName_normalized());
                try {
                    firmwareFile = downloadFirmware();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                connectToDevice();
            }
        }
    }


    //Code my own methods for the firmware download and transfer to ESP32
    //Will attempt to do this without using SweetBlue library.
    public File downloadFirmware() throws IOException {

        //TODO : REMOVE BEFORE FINAL
        Log.i("Medrano: ", " Made it to downloadFirmware()");

        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse("https://gp-firmware-test.s3.amazonaws.com/hello_world.bin");
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setDescription("Firmware Download").setTitle("Notification Title");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,firmwareFileName);
        request.setVisibleInDownloadsUi(false);
        firmwareID = downloadManager.enqueue(request);

        //TODO: REMOVE FOR FINAL
        Log.i("Medrano: ", " End of downloadFirmware()");

        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), firmwareFileName);

        //convertFileToByteArray();
    }



    /**
     * Recieve the location of the file and convert the file to a byteArray
     * This method may change from a void method to return the byte ArrayList later.
     */
    public byte[] convertFileToByteArray(File file)
    {
        //TODO: REMOVE FOR FINAL
        Log.i("Medrano: ", " START of convertFileToByteArray()");

        //File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), firmwareFileName);

        FileInputStream fileStream = null;
        try {
            fileStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        byte[] fileInBytes = new byte[(int) file.length()];

        try {
            fileStream.read(fileInBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fileStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //TODO: REMOVE FOR FINAL
        Log.i("Medrano : ", "Array Contents : " + Arrays.toString(fileInBytes));
        Log.i("Medrano : ", "Length of array : " + fileInBytes.length);
        Log.i("Medrano: ", " END of readFileToBytes()");

        //createDataPackets(fileInBytes);
        return fileInBytes;
    }

    public List<byte[]> createDataPackets(byte[] fileInBytes){

        //This works already
        //int packetSize = m_bleDevice.getEffectiveWriteMtuSize();
        int packetSize = 253;

        m_bleDevice.negotiateMtu(packetSize);

        //TODO : Remove, here only for testing and debugging
        Log.i("Medrano: ", " Max MTU Size: " + packetSize);

        List<byte[]> dataPackets = new ArrayList<byte[]>();
        int byteIdx = 0;
        //int dataPacketIdx = 0;

        Log.i("Medrano : ", "File Length : " + fileInBytes.length);
        while( byteIdx < fileInBytes.length){
            Log.i("Medrano : ", "byteIdx =  : " + byteIdx);

            if(fileInBytes.length - byteIdx < packetSize){
                packetSize = fileInBytes.length % packetSize;
            }
            byte[] temp = new byte[packetSize];
            for(int i = 0; i < packetSize; i++){
                temp[i] = fileInBytes[byteIdx];
                byteIdx++;
            }
            dataPackets.add(temp);
            //dataPacketIdx++;
        }

        //TODO : Remove, here only for testing and debugging
        Log.i("Medrano : ", "Data Packets : " + Arrays.toString(dataPackets.get(0)));
        Log.i("Medrano : ", "Data Packets : " + Arrays.toString(dataPackets.get(1)));
        return dataPackets;

    }

    /**
     * Below are the provided Methods in the simple_ota app from sweetblue
     * private void connectToDevice()
     * private static class SimpleOtaTransaction extends BleTransaction.Ota
     *
     **/
    private void connectToDevice()
    {
        //TODO: REMOVE FOR FINAL
        Log.i("Medrano: ", " Made it to connectToDevice");
        Log.i("Medrano: ", " Current BLE Device: " + m_bleDevice.getName_normalized());
        // Connect to the device, and pass in a device connect listener, so we know when we are connected, and also to know if/when the connection failed.
        // In this instance, we're only worried about when the connection fails, and SweetBlue has given up trying to connect.
        m_bleDevice.connect(connectEvent ->
        {
            if (connectEvent.wasSuccess())
            {
                Log.i("Medrano : ", connectEvent.device().getName_debug() + " just got connected and is ready to use!");
                final List<byte[]> writeQueue = createDataPackets(convertFileToByteArray(firmwareFile));
                connectEvent.device().performOta(new SimpleOtaTransaction(writeQueue));
                downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                downloadManager.remove(firmwareID);

            }
            else
            {
                if (!connectEvent.isRetrying())
                {
                    // If the connectEvent says it's NOT a retry, then SweetBlue has given up trying to connect, so let's print an error log
                    // The ConnectEvent also keeps an instance of the ConnectionFailEvent, so you can find out the reason for the failure.
                    Log.e("Medrano : ", connectEvent.device().getName_debug() + " failed to connect with a status of " + connectEvent.failEvent().status().name());
                }
            }
            //TODO: REMOVE FOR FINAL, here for testing
            Log.i("Medrano : ", "Connected to " + m_bleDevice.getName_normalized());

        });

    }

    // A simple implementation of an OTA transaction class. This simply holds a list of byte arrays. Each array will be sent in it's own
    // write operation.
    private static class SimpleOtaTransaction extends BleTransaction.Ota
    {
        private final List<byte[]> m_dataQueue;
        private int m_currentIndex = 0;

        // A ReadWriteListener for listening to the result of each write.
        private final ReadWriteListener m_readWriteListener = readWriteEvent ->
        {
            if (readWriteEvent.wasSuccess())
                doNextWrite();
            else
            {
                fail();
            }
        };

        private final BleWrite m_bleWrite = new BleWrite(GB_FIRMWARE_UUID).setReadWriteListener(m_readWriteListener);

        public SimpleOtaTransaction(final List<byte[]> dataQueue)
        {
            m_dataQueue = dataQueue;
        }

        @Override
        protected void start()
        {
            doNextWrite();
        }

        private void doNextWrite()
        {
            if (m_currentIndex == m_dataQueue.size())
            {
                // Now that we've sent all data, we succeed the transaction, so that other operations may be performed on the device.
                succeed();
            }
            else
            {
                final byte[] nextData = m_dataQueue.get(m_currentIndex);
                m_bleWrite.setBytes(nextData);
                write(m_bleWrite);
                m_currentIndex++;
            }
        }
    }
}