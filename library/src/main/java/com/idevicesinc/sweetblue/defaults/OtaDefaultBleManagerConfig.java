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

package com.idevicesinc.sweetblue.defaults;


import com.idevicesinc.sweetblue.BleManagerConfig;
import com.idevicesinc.sweetblue.utils.Interval;


/**
 * Default instance of {@link BleManagerConfig} with some options set for optimal OTA performance.
 * NOTE: Callbacks will not be guaranteed to be on the main thread (unles otherwise configured by
 * subclassing this class). This helps to cut down on thread switching.
 */
public class OtaDefaultBleManagerConfig extends BleManagerConfig
{
    {
        autoScanDuringOta = true;
        autoUpdateRate = Interval.millis(1);
        clearGattOnOtaSuccess = true;
        postCallbacksToMainThread = false;

    }
}
