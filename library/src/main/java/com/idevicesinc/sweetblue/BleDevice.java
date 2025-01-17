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

package com.idevicesinc.sweetblue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import com.idevicesinc.sweetblue.ReadWriteListener.Type;
import com.idevicesinc.sweetblue.ReadWriteListener.ReadWriteEvent;
import com.idevicesinc.sweetblue.DeviceReconnectFilter.ConnectFailEvent;
import com.idevicesinc.sweetblue.annotations.Immutable;
import com.idevicesinc.sweetblue.internal.IBleDevice;
import com.idevicesinc.sweetblue.annotations.Advanced;
import com.idevicesinc.sweetblue.annotations.Nullable;
import com.idevicesinc.sweetblue.annotations.Nullable.Prevalence;
import com.idevicesinc.sweetblue.internal.P_BleDeviceImpl;
import com.idevicesinc.sweetblue.internal.P_Bridge_Internal;
import com.idevicesinc.sweetblue.utils.BleScanRecord;
import com.idevicesinc.sweetblue.utils.Distance;
import com.idevicesinc.sweetblue.utils.EpochTime;
import com.idevicesinc.sweetblue.utils.EpochTimeRange;
import com.idevicesinc.sweetblue.utils.Event;
import com.idevicesinc.sweetblue.utils.ForEach_Breakable;
import com.idevicesinc.sweetblue.utils.ForEach_Returning;
import com.idevicesinc.sweetblue.utils.ForEach_Void;
import com.idevicesinc.sweetblue.utils.FutureData;
import com.idevicesinc.sweetblue.utils.HistoricalData;
import com.idevicesinc.sweetblue.utils.HistoricalDataCursor;
import com.idevicesinc.sweetblue.utils.Interval;
import com.idevicesinc.sweetblue.utils.Percent;
import com.idevicesinc.sweetblue.utils.Phy;
import com.idevicesinc.sweetblue.utils.PresentData;
import com.idevicesinc.sweetblue.utils.State;
import com.idevicesinc.sweetblue.utils.TimeEstimator;
import com.idevicesinc.sweetblue.utils.Uuids;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * This is the one other class you will use the most besides {@link BleManager}.
 * It acts as a BLE-specific abstraction for the {@link BluetoothDevice} and
 * {@link BluetoothGatt} classes. It does everything you would expect, like
 * providing methods for connecting, reading/writing characteristics, enabling
 * notifications, etc.
 * <br><br>
 * Although instances of this class can be created explicitly through
 * {@link BleManager#newDevice(String, String)}, usually they're created
 * implicitly by {@link BleManager} as a result of a scanning operation (e.g.
 * {@link BleManager#startScan()}) and sent to you through
 * {@link DiscoveryListener# onEvent(Event)}.
 */
public final class BleDevice extends BleNode
{

    /**
     * Special value that is used in place of Java's built-in <code>null</code>.
     */
    @Immutable
    public static final BleDevice NULL = new BleDevice(P_Bridge_Internal.NULL_DEVICE());

    private final P_BleDeviceImpl m_deviceImpl;


    BleDevice(IBleDevice deviceImpl)
    {
        super(deviceImpl);
        m_deviceImpl = (P_BleDeviceImpl) deviceImpl;
    }

    /**
     * Wrapper for {@link BluetoothGatt#beginReliableWrite()} - will return an event such that {@link ReadWriteEvent#isNull()} will
     * return <code>false</code> if there are no problems. After calling this you should do your {@link BleDevice#write(UUID, byte[])}
     * calls then call {@link #reliableWrite_execute()}.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent reliableWrite_begin(final ReadWriteListener listener)
    {

        return m_deviceImpl.reliableWrite_begin(listener);
    }

    /**
     * Wrapper for {@link BluetoothGatt#abortReliableWrite()} - will return an event such that {@link ReadWriteEvent#isNull()} will
     * return <code>false</code> if there are no problems. This call requires a previous call to {@link #reliableWrite_begin(ReadWriteListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent reliableWrite_abort()
    {

        return m_deviceImpl.reliableWrite_abort();
    }

    /**
     * Wrapper for {@link BluetoothGatt#abortReliableWrite()} - will return an event such that {@link ReadWriteEvent#isNull()} will
     * return <code>false</code> if there are no problems. This call requires a previous call to {@link #reliableWrite_begin(ReadWriteListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent reliableWrite_execute()
    {

        return m_deviceImpl.reliableWrite_execute();
    }

    /**
     * Returns a string of all the states this {@link BleDevice} is currently in.
     */
    public final String printState()
    {
        return m_deviceImpl.printState();
    }

    /**
     * Optionally sets overrides for any custom options given to {@link BleManager#get(android.content.Context, BleManagerConfig)}
     * for this individual device.
     */
    public final void setConfig(@Nullable(Prevalence.RARE) BleDeviceConfig config_nullable)
    {
        m_deviceImpl.setConfig(config_nullable);
    }

    /**
     * Return the {@link BleDeviceConfig} this device is set to use. If none has been set explicitly, then the instance
     * of {@link BleManagerConfig} is returned.
     */
    @Nullable(Prevalence.NEVER)
    public final BleDeviceConfig getConfig()
    {
        return m_deviceImpl.getConfig();
    }

    /**
     * How the device was originally created, either from scanning or explicit creation.
     * <br><br>
     * NOTE: That devices for which this returns {@link BleDeviceOrigin#EXPLICIT} may still be
     * {@link DiscoveryListener.LifeCycle#REDISCOVERED} through {@link BleManager#startScan()}.
     */
    public final BleDeviceOrigin getOrigin()
    {
        return m_deviceImpl.getOrigin();
    }

    /**
     * Returns the last time the device was {@link DiscoveryListener.LifeCycle#DISCOVERED}
     * or {@link DiscoveryListener.LifeCycle#REDISCOVERED}. If {@link #getOrigin()} returns
     * {@link BleDeviceOrigin#EXPLICIT} then this will return {@link EpochTime#NULL} unless or until
     * the device is {@link DiscoveryListener.LifeCycle#REDISCOVERED}.
     */
    public final EpochTime getLastDiscoveryTime()
    {
        return m_deviceImpl.getLastDiscoveryTime();
    }

    /**
     * This enum gives you an indication of the last interaction with a device across app sessions or in-app BLE
     * {@link BleManagerState#OFF}-&gt;{@link BleManagerState#ON} cycles or undiscovery-&gt;rediscovery, which
     * basically means how it was last {@link BleDeviceState#BLE_DISCONNECTED}.
     * <br><br>
     * If {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent#NULL}, then the last disconnect is unknown because
     * (a) device has never been seen before,
     * (b) reason for disconnect was app being killed and {@link BleDeviceConfig#manageLastDisconnectOnDisk} was <code>false</code>,
     * (c) app user cleared app data between app sessions or reinstalled the app.
     * <br><br>
     * If {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent#UNINTENTIONAL}, then from a user experience perspective, the user may not have wanted
     * the disconnect to happen, and thus *probably* would want to be automatically connected again as soon as the device is discovered.
     * <br><br>
     * If {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent#INTENTIONAL}, then the last reason the device was {@link BleDeviceState#BLE_DISCONNECTED} was because
     * {@link BleDevice#disconnect()} was called, which most-likely means the user doesn't want to automatically connect to this device again.
     * <br><br>
     * See further explanation at {@link BleDeviceConfig#manageLastDisconnectOnDisk}.
     */
    @Advanced
    public final State.ChangeIntent getLastDisconnectIntent()
    {
        return m_deviceImpl.getLastDisconnectIntent();
    }

    /**
     * Set a listener here to be notified whenever this device's state changes. NOTE: This will clear the stack of {@link DeviceStateListener}s, and set
     * the one provided here to be the only one in the stack.
     *
     * If the provided listener is <code>null</code>, then the stack of listeners will be cleared.
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> set the listener.</b>
     */
    public final boolean setListener_State(@Nullable(Prevalence.NORMAL) DeviceStateListener listener_nullable)
    {
        return m_deviceImpl.setListener_State(listener_nullable);
    }

    /**
     * Push a new {@link DeviceStateListener} onto the stack. This new listener will be the one events are dispatched to, until
     * {@link #popListener_State()} is called.
     * This method will early-out if the provided listener is <code>null</code>
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> push the listener.</b>
     */
    public final boolean pushListener_State(@Nullable(Prevalence.NEVER) DeviceStateListener listener)
    {
        return m_deviceImpl.pushListener_State(listener);
    }

    /**
     * Pop the current {@link DeviceStateListener} out of the stack of listeners.
     * Returns <code>true</code> if a listener was actually removed from the stack (it will only be false if the stack is already empty).
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> pop the listener.</b>
     */
    public final boolean popListener_State()
    {
        return m_deviceImpl.popListener_State();
    }

    /**ƒ this method allows you to pass in the {@link DeviceStateListener} you'd like to "pop" from the stack. This will
     * remove the given listener from the stack, regardless if it's at the head or not. Returns <code>true</code> if the Stack contained the given listener, and
     * it got removed.
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> pop the listener.</b>
     */
    public final boolean popListener_State(DeviceStateListener listener)
    {
        return m_deviceImpl.popListener_State(listener);
    }

    /**
     * Returns the current {@link DeviceStateListener} being used (the top of the stack). This can return <code>null</code> if there
     * are no listeners in the stack.
     */
    public final @Nullable(Prevalence.NORMAL) DeviceStateListener getListener_State()
    {
        return m_deviceImpl.getListener_State();
    }

    /**
     * Set a listener here to be notified whenever this device connects, or gets disconnected. NOTE: This will clear the stack of {@link DeviceStateListener}s, and set
     * the one provided here to be the only one in the stack.
     *
     * If the provided listener is <code>null</code>, then the stack of listeners will be cleared.
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> set the listener.</b>
     */
    public final boolean setListener_Connect(@Nullable(Prevalence.NORMAL) DeviceConnectListener listener)
    {
        return m_deviceImpl.setListener_Connect(listener);
    }

    /**
     * Push a new {@link DeviceConnectListener} onto the stack. This new listener will be the one events are dispatched to, until
     * {@link #popListener_State()} is called.
     * This method will early-out if the provided listener is <code>null</code>
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> push the listener.</b>
     */
    public final boolean pushListener_Connect(@Nullable(Prevalence.NEVER) DeviceConnectListener listener)
    {
        return m_deviceImpl.pushListener_Connect(listener);
    }

    /**
     * Pop the current {@link DeviceConnectListener} out of the stack of listeners.
     * Returns <code>true</code> if a listener was actually removed from the stack (it will only be false if the stack is already empty).
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> pop the listener.</b>
     */
    public final boolean popListener_Connect()
    {
        return m_deviceImpl.popListener_Connect();
    }

    /**
     * Similar to {@link #popListener_Connect()}, only this method allows you to pass in the {@link DeviceConnectListener} you'd like to "pop" from the stack. This will
     * remove the given listener from the stack, regardless if it's at the head or not. Returns <code>true</code> if the Stack contained the given listener, and
     * it got removed.
     *
     * <b>NOTE: If {@link BondFilter#onEvent(BondFilter.ConnectionBugEvent)} returns {@link BondFilter.ConnectionBugEvent.Please#tryFix()}, and the fix is in process,
     * then this method will early out, and will <i>NOT</i> pop the listener.</b>
     */
    public final boolean popListener_Connect(DeviceConnectListener listener)
    {
        return m_deviceImpl.popListener_Connect(listener);
    }

    /**
     * Returns the current {@link DeviceConnectListener} being used (the top of the stack). This can return <code>null</code> if there
     * are no listeners in the stack.
     */
    public final @Nullable(Prevalence.NORMAL) DeviceConnectListener getListener_Connect()
    {
        return m_deviceImpl.getListener_Connect();
    }

    /**
     * Set a listener here to be notified whenever a connection fails and to
     * have control over retry behavior. NOTE: This will clear the stack of {@link DeviceReconnectFilter}s, and set
     * the one provided here to be the only one in the stack. If the provided listener is <code>null</code>, then the stack of listeners will be cleared.
     */
    public final void setListener_Reconnect(@Nullable(Prevalence.NORMAL) DeviceReconnectFilter listener_nullable)
    {
        m_deviceImpl.setListener_Reconnect(listener_nullable);
    }

    /**
     * Pushes the provided {@link DeviceReconnectFilter} on to the top of the stack of listeners.
     * This method will early-out if the provided listener is <code>null</code>
     */
    public final void pushListener_Reconnect(@Nullable(Prevalence.NEVER) DeviceReconnectFilter listener)
    {
        m_deviceImpl.pushListener_Reconnect(listener);
    }

    /**
     * Pops the current {@link DeviceReconnectFilter} off the stack of listeners.
     * Returns <code>true</code> if a listener was actually removed from the stack (it will only be false if the stack is already empty).
     */
    public final boolean popListener_Reconnect()
    {
        return m_deviceImpl.popListener_Reconnect();
    }

    /**
     * Similar to {@link #popListener_Reconnect()}, only this method allows you to pass in the {@link DeviceReconnectFilter} you'd like to "pop" from the stack. This will
     * remove the given listener from the stack, regardless if it's at the head or not. Returns <code>true</code> if the Stack contained the given listener, and
     * it got removed.
     */
    public final boolean popListener_Reconnect(DeviceReconnectFilter listener)
    {
        return m_deviceImpl.popListener_Reconnect(listener);
    }

    /**
     * Returns the current {@link DeviceReconnectFilter} being used (the top of the stack). This can return <code>null</code> if there
     * are no listeners in the stack.
     */
    public final @Nullable(Prevalence.NORMAL) DeviceReconnectFilter getListener_Reconnect()
    {
        return m_deviceImpl.getListener_Reconnect();
    }

    /**
     * Set a listener here to be notified whenever a bond attempt succeeds. This
     * will catch attempts to bond both through {@link #bond()} and when bonding
     * through the operating system settings or from other apps.
     */
    public final void setListener_Bond(@Nullable(Prevalence.NORMAL) BondListener listener_nullable)
    {
        m_deviceImpl.setListener_Bond(listener_nullable);
    }

    /**
     * Sets a default backup {@link ReadWriteListener} that will be called for all calls to {@link #read(UUID, ReadWriteListener)},
     * {@link #write(UUID, byte[], ReadWriteListener)}, {@link #enableNotify(UUID, ReadWriteListener)}, etc.
     * NOTE: This will clear the stack of {@link ReadWriteListener}s, and set
     * the one provided here to be the only one in the stack. If the provided listener is <code>null</code>, then the stack of listeners will be cleared.
     */
    public final void setListener_ReadWrite(@Nullable(Prevalence.NORMAL) final ReadWriteListener listener_nullable)
    {
        m_deviceImpl.setListener_ReadWrite(listener_nullable);
    }

    /**
     * Pushes the provided {@link ReadWriteListener} on to the top of the stack of listeners.
     * This method will early-out if the provided listener is <code>null</code>
     */
    public final void pushListener_ReadWrite(@Nullable(Prevalence.NEVER) ReadWriteListener listener)
    {
        m_deviceImpl.pushListener_ReadWrite(listener);
    }

    /**
     * Pops the current {@link ReadWriteListener} off the stack of listeners.
     * Returns <code>true</code> if a listener was actually removed from the stack (it will only be false if the stack is already empty).
     */
    public final boolean popListener_ReadWrite()
    {
        return m_deviceImpl.popListener_ReadWrite();
    }

    /**
     * Similar to {@link #popListener_ReadWrite()}, only this method allows you to pass in the {@link ReadWriteListener} you'd like to "pop" from the stack. This will
     * remove the given listener from the stack, regardless if it's at the head or not. Returns <code>true</code> if the Stack contained the given listener, and
     * it got removed.
     */
    public final boolean popListener_ReadWrite(ReadWriteListener listener)
    {
        return m_deviceImpl.popListener_ReadWrite(listener);
    }

    /**
     * Returns the current {@link ReadWriteListener} being used (the top of the stack). This can return <code>null</code> if there
     * are no listeners in the stack.
     */
    public final @Nullable(Prevalence.NORMAL) ReadWriteListener getListener_ReadWrite()
    {
        return m_deviceImpl.getListener_ReadWrite();
    }


    /**
     * Sets a default {@link NotificationListener} that will be called when receiving notifications, or indications. This listener will also
     * be called when toggling notifications. This does NOT replace {@link com.idevicesinc.sweetblue.ReadWriteListener}, just adds to it. If
     * a default {@link com.idevicesinc.sweetblue.ReadWriteListener} has been set, it will still fire in addition to this listener.
     * NOTE: This will clear the stack of {@link ReadWriteListener}s, and set
     * the one provided here to be the only one in the stack. If the provided listener is <code>null</code>, then the stack of listeners will be cleared.
     */
    public final void setListener_Notification(@Nullable(Prevalence.NORMAL) NotificationListener listener_nullable)
    {
        m_deviceImpl.setListener_Notification(listener_nullable);
    }

    /**
     * Pushes the provided {@link NotificationListener} on to the top of the stack of listeners.
     * This method will early-out if the provided listener is <code>null</code>
     */
    public final void pushListener_Notification(@Nullable(Prevalence.NEVER) NotificationListener listener)
    {
        m_deviceImpl.pushListener_Notification(listener);
    }

    /**
     * Pops the current {@link NotificationListener} off the stack of listeners.
     * Returns <code>true</code> if a listener was actually removed from the stack (it will only be false if the stack is already empty).
     */
    public final boolean popListener_Notification()
    {
        return m_deviceImpl.popListener_Notification();
    }

    /**
     * Similar to {@link #popListener_Notification()}, only this method allows you to pass in the {@link NotificationListener} you'd like to "pop" from the stack. This will
     * remove the given listener from the stack, regardless if it's at the head or not. Returns <code>true</code> if the Stack contained the given listener, and
     * it got removed.
     */
    public final boolean popListener_Notification(NotificationListener listener)
    {
        return m_deviceImpl.popListener_Notification(listener);
    }

    /**
     * Returns the current {@link NotificationListener} being used (the top of the stack). This can return <code>null</code> if there
     * are no listeners in the stack.
     */
    public final @Nullable(Prevalence.NORMAL) NotificationListener getListener_Notification()
    {
        return m_deviceImpl.getListener_Notification();
    }

    /**
     * Sets a default backup {@link HistoricalDataLoadListener} that will be invoked
     * for all historical data loads to memory for all uuids.
     */
    public final void setListener_HistoricalDataLoad(@Nullable(Prevalence.NORMAL) final HistoricalDataLoadListener listener_nullable)
    {
        m_deviceImpl.setListener_HistoricalDataLoad(listener_nullable);
    }

    /**
     * Returns the connection failure retry count during a retry loop. Basic example use case is to provide a callback to
     * {@link #setListener_Reconnect(DeviceReconnectFilter)} and update your application's UI with this method's return value downstream of your
     * {@link DeviceReconnectFilter#onConnectFailed(ReconnectFilter.ConnectFailEvent)} override.
     */
    public final int getConnectionRetryCount()
    {
        return m_deviceImpl.getConnectionRetryCount();
    }

    /**
     * Returns the bitwise state mask representation of {@link BleDeviceState} for this device.
     *
     * @see BleDeviceState
     */
    @Advanced
    public final int getStateMask()
    {
        return m_deviceImpl.getStateMask();
    }

    /**
     * Returns the actual native state mask representation of the {@link BleDeviceState} for this device.
     * The main purpose of this is to reflect what's going on under the hood while {@link BleDevice#is(BleDeviceState)}
     * with {@link BleDeviceState#RECONNECTING_SHORT_TERM} is <code>true</code>.
     */
    @Advanced
    public final int getNativeStateMask()
    {
        return m_deviceImpl.getNativeStateMask();
    }

    /**
     * See similar explanation for {@link #getAverageWriteTime()}.
     *
     * @see #getAverageWriteTime()
     * @see BleManagerConfig#nForAverageRunningReadTime
     */
    @Advanced
    public final Interval getAverageReadTime()
    {
        return m_deviceImpl.getAverageReadTime();
    }

    /**
     * Returns the average round trip time in seconds for all write operations started with {@link #write(UUID, byte[])} or
     * {@link #write(UUID, byte[], ReadWriteListener)}. This is a running average with N being defined by
     * {@link BleManagerConfig#nForAverageRunningWriteTime}. This may be useful for estimating how long a series of
     * reads and/or writes will take. For example for displaying the estimated time remaining for a firmware update.
     */
    @Advanced
    public final Interval getAverageWriteTime()
    {
        return m_deviceImpl.getAverageWriteTime();
    }

    /**
     * Returns the raw RSSI retrieved from when the device was discovered,
     * rediscovered, or when you call {@link #readRssi()} or {@link #startRssiPoll(Interval)}.
     *
     * @see #getDistance()
     */
    public final int getRssi()
    {
        return m_deviceImpl.getRssi();
    }

    /**
     * Raw RSSI from {@link #getRssi()} is a little cryptic, so this gives you a friendly 0%-100% value for signal strength.
     */
    public final Percent getRssiPercent()
    {
        return m_deviceImpl.getRssiPercent();
    }

    /**
     * Returns the approximate distance in meters based on {@link #getRssi()} and
     * {@link #getTxPower()}. NOTE: the higher the distance, the less the accuracy.
     */
    public final Distance getDistance()
    {
        return m_deviceImpl.getDistance();
    }

    /**
     * Returns the calibrated transmission power of the device. If this can't be
     * figured out from the device itself then it backs up to the value provided
     * in {@link BleDeviceConfig#defaultTxPower}.
     *
     * @see BleDeviceConfig#defaultTxPower
     */
    @Advanced
    public final int getTxPower()
    {
        return m_deviceImpl.getTxPower();
    }

    /**
     * Returns the scan record from when we discovered the device. May be empty but never <code>null</code>.
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) byte[] getScanRecord()
    {
        return m_deviceImpl.getScanRecord();
    }

    /**
     * Returns the {@link BleScanRecord} instance held by this {@link BleDevice}.
     */
    public final @Nullable(Prevalence.NEVER)
    BleScanRecord getScanInfo()
    {
        return m_deviceImpl.getScanInfo();
    }

    /**
     * Returns the advertising flags, if any, parsed from {@link #getScanRecord()}.
     */
    public final int getAdvertisingFlags()
    {
        return m_deviceImpl.getAdvertisingFlags();
    }

    /**
     * Returns the advertised services, if any, parsed from {@link #getScanRecord()}. May be empty but never <code>null</code>.
     */
    public final @Nullable(Prevalence.NEVER) UUID[] getAdvertisedServices()
    {
        return m_deviceImpl.getAdvertisedServices();
    }

    /**
     * Returns the manufacturer data, if any, parsed from {@link #getScanRecord()}. May be empty but never <code>null</code>.
     */
    public final @Nullable(Prevalence.NEVER) byte[] getManufacturerData()
    {
        return m_deviceImpl.getManufacturerData();
    }

    /**
     * Returns the manufacturer id, if any, parsed from {@link #getScanRecord()} }. May be -1 if not set
     */
    public final int getManufacturerId()
    {
        return m_deviceImpl.getManufacturerId();
    }

    /**
     * Returns the manufacturer data, if any, parsed from {@link #getScanRecord()}. May be empty but never <code>null</code>.
     */
    public final @Nullable(Prevalence.NEVER) Map<UUID, byte[]> getAdvertisedServiceData()
    {
        return m_deviceImpl.getAdvertisedServiceData();
    }

    /**
     * Returns the database table name for the underlying store of historical data for the given {@link UUID}.
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) String getHistoricalDataTableName(final UUID uuid)
    {
        return m_deviceImpl.getHistoricalDataTableName(uuid);
    }

    /**
     * Returns a cursor capable of random access to the database-persisted historical data for this device.
     * Unlike calls to methods like {@link #getHistoricalData_iterator(UUID)} and other overloads,
     * this call does not force bulk data load into memory.
     * <br><br>
     * NOTE: You must call {@link HistoricalDataCursor#close()} when you are done with the data.
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) HistoricalDataCursor getHistoricalData_cursor(final UUID uuid)
    {
        return getHistoricalData_cursor(uuid, EpochTimeRange.FROM_MIN_TO_MAX);
    }

    /**
     * Same as {@link #getHistoricalData_cursor(UUID)} but constrains the results to the given time range.
     * <br><br>
     * NOTE: You must call {@link HistoricalDataCursor#close()} when you are done with the data.
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) HistoricalDataCursor getHistoricalData_cursor(final UUID uuid, final EpochTimeRange range)
    {
        return m_deviceImpl.getHistoricalData_cursor(uuid, range);
    }

    /**
     * Loads all historical data to memory for this device.
     */
    @Advanced
    public final void loadHistoricalData()
    {
        loadHistoricalData(null, null);
    }

    /**
     * Loads all historical data to memory for this device for the given {@link UUID}.
     */
    @Advanced
    public final void loadHistoricalData(final UUID uuid)
    {
        loadHistoricalData(uuid, null);
    }

    /**
     * Loads all historical data to memory for this device with a callback for when it's complete.
     */
    @Advanced
    public final void loadHistoricalData(final HistoricalDataLoadListener listener)
    {
        loadHistoricalData(null, listener);
    }

    /**
     * Loads all historical data to memory for this device for the given {@link UUID}.
     */
    @Advanced
    public final void loadHistoricalData(final UUID uuid, final HistoricalDataLoadListener listener)
    {
        m_deviceImpl.loadHistoricalData(uuid, listener);
    }

    /**
     * Returns whether the device is currently loading any historical data to memory, either through
     * {@link #loadHistoricalData()} (or overloads) or {@link #getHistoricalData_iterator(UUID)} (or overloads).
     */
    @Advanced
    public final boolean isHistoricalDataLoading()
    {
        return isHistoricalDataLoading(null);
    }

    /**
     * Returns whether the device is currently loading any historical data to memory for the given uuid, either through
     * {@link #loadHistoricalData()} (or overloads) or {@link #getHistoricalData_iterator(UUID)} (or overloads).
     */
    @Advanced
    public final boolean isHistoricalDataLoading(final UUID uuid)
    {
        return m_deviceImpl.isHistoricalDataLoading(uuid);
    }

    /**
     * Returns <code>true</code> if the historical data for all historical data for
     * this device is loaded into memory.
     * Use {@link HistoricalDataLoadListener}
     * to listen for when the load actually completes. If {@link #hasHistoricalData(UUID)}
     * returns <code>false</code> then this will also always return <code>false</code>.
     */
    @Advanced
    public final boolean isHistoricalDataLoaded()
    {
        return isHistoricalDataLoaded(null);
    }

    /**
     * Returns <code>true</code> if the historical data for a given uuid is loaded into memory.
     * Use {@link HistoricalDataLoadListener}
     * to listen for when the load actually completes. If {@link #hasHistoricalData(UUID)}
     * returns <code>false</code> then this will also always return <code>false</code>.
     */
    @Advanced
    public final boolean isHistoricalDataLoaded(final UUID uuid)
    {
        return m_deviceImpl.isHistoricalDataLoaded(uuid);
    }

    /**
     * Returns the cached data from the latest successful read or notify received for a given uuid.
     * Basically if you receive a {@link ReadWriteListener.ReadWriteEvent} for which {@link ReadWriteListener.ReadWriteEvent#isRead()}
     * and {@link ReadWriteListener.ReadWriteEvent#wasSuccess()} both return <code>true</code> then {@link ReadWriteListener.ReadWriteEvent#data()},
     * will be cached and is retrievable by this method.
     *
     * @return The cached value from a previous read or notify, or {@link HistoricalData#NULL} otherwise.
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) HistoricalData getHistoricalData_latest(final UUID uuid)
    {
        return getHistoricalData_atOffset(uuid, getHistoricalDataCount(uuid) - 1);
    }

    /**
     * Returns an iterator that will iterate through all {@link HistoricalData} entries.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) Iterator<HistoricalData> getHistoricalData_iterator(final UUID uuid)
    {
        return getHistoricalData_iterator(uuid, EpochTimeRange.FROM_MIN_TO_MAX);
    }

    /**
     * Returns an iterator that will iterate through all {@link HistoricalData} entries within the range provided.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) Iterator<HistoricalData> getHistoricalData_iterator(final UUID uuid, final EpochTimeRange range)
    {
        return m_deviceImpl.getHistoricalData_iterator(uuid, range);
    }

    /**
     * Provides all historical data through the "for each" provided.
     *
     * @return <code>true</code> if there are any entries, <code>false</code> otherwise.
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean getHistoricalData_forEach(final UUID uuid, final ForEach_Void<HistoricalData> forEach)
    {
        return getHistoricalData_forEach(uuid, EpochTimeRange.FROM_MIN_TO_MAX, forEach);
    }

    /**
     * Provides all historical data through the "for each" provided within the range provided.
     *
     * @return <code>true</code> if there are any entries, <code>false</code> otherwise.
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean getHistoricalData_forEach(final UUID uuid, final EpochTimeRange range, final ForEach_Void<HistoricalData> forEach)
    {
        return m_deviceImpl.getHistoricalData_forEach(uuid, range, forEach);
    }

    /**
     * Provides all historical data through the "for each" provided.
     *
     * @return <code>true</code> if there are any entries, <code>false</code> otherwise.
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean getHistoricalData_forEach(final UUID uuid, final ForEach_Breakable<HistoricalData> forEach)
    {
        return getHistoricalData_forEach(uuid, EpochTimeRange.FROM_MIN_TO_MAX, forEach);
    }

    /**
     * Provides all historical data through the "for each" provided within the range provided.
     *
     * @return <code>true</code> if there are any entries, <code>false</code> otherwise.
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean getHistoricalData_forEach(final UUID uuid, final EpochTimeRange range, final ForEach_Breakable<HistoricalData> forEach)
    {
        return m_deviceImpl.getHistoricalData_forEach(uuid, range, forEach);
    }

    /**
     * Same as {@link #addHistoricalData(UUID, byte[], EpochTime)} but returns the data from the chronological offset, i.e. <code>offsetFromStart=0</code>
     * will return the earliest {@link HistoricalData}. Use in combination with {@link #getHistoricalDataCount(java.util.UUID)} to iterate
     * "manually" through this device's historical data for the given characteristic.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) HistoricalData getHistoricalData_atOffset(final UUID uuid, final int offsetFromStart)
    {
        return getHistoricalData_atOffset(uuid, EpochTimeRange.FROM_MIN_TO_MAX, offsetFromStart);
    }

    /**
     * Same as {@link #getHistoricalData_atOffset(java.util.UUID, int)} but offset is relative to the time range provided.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final @Nullable(Nullable.Prevalence.NEVER) HistoricalData getHistoricalData_atOffset(final UUID uuid, final EpochTimeRange range, final int offsetFromStart)
    {
        return m_deviceImpl.getHistoricalData_atOffset(uuid, range, offsetFromStart);
    }

    /**
     * Returns the number of historical data entries that have been logged for the device's given characteristic.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final int getHistoricalDataCount(final UUID uuid)
    {
        return getHistoricalDataCount(uuid, EpochTimeRange.FROM_MIN_TO_MAX);
    }

    /**
     * Returns the number of historical data entries that have been logged
     * for the device's given characteristic within the range provided.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final int getHistoricalDataCount(final UUID uuid, final EpochTimeRange range)
    {
        return m_deviceImpl.getHistoricalDataCount(uuid, range);
    }

    /**
     * Returns <code>true</code> if there is any historical data at all for this device.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean hasHistoricalData()
    {
        return hasHistoricalData(EpochTimeRange.FROM_MIN_TO_MAX);
    }

    /**
     * Returns <code>true</code> if there is any historical data at all for this device within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean hasHistoricalData(final EpochTimeRange range)
    {
        return hasHistoricalData(null, range);
    }

    /**
     * Returns <code>true</code> if there is any historical data for the given uuid.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean hasHistoricalData(final UUID uuid)
    {
        return hasHistoricalData(uuid, EpochTimeRange.FROM_MIN_TO_MAX);
    }

    /**
     * Returns <code>true</code> if there is any historical data for any of the given uuids.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean hasHistoricalData(final UUID[] uuids)
    {
        for (int i = 0; i < uuids.length; i++)
        {
            if (hasHistoricalData(uuids[i], EpochTimeRange.FROM_MIN_TO_MAX))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns <code>true</code> if there is any historical data for the given uuid within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final boolean hasHistoricalData(final UUID uuid, final EpochTimeRange range)
    {
        return m_deviceImpl.hasHistoricalData(uuid, range);
    }

    /**
     * Manual way to add data to the historical data list managed by this device. You may want to use this if,
     * for example, your remote BLE device is capable of taking and caching independent readings while not connected.
     * After you connect with this device and download the log you can add it manually here.
     * Really you can use this for any arbitrary historical data though, even if it's not associated with a characteristic.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final byte[] data, final EpochTime epochTime)
    {
        m_deviceImpl.addHistoricalData(uuid, new HistoricalData(epochTime, data));
    }

    /**
     * Just an overload of {@link #addHistoricalData(UUID, byte[], EpochTime)} with the data and epochTime parameters switched around.
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final EpochTime epochTime, final byte[] data)
    {
        addHistoricalData(uuid, data, epochTime);
    }

    /**
     * Same as {@link #addHistoricalData(UUID, byte[], EpochTime)} but uses {@link System#currentTimeMillis()} for the timestamp.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final byte[] data)
    {
        addHistoricalData(uuid, data, new EpochTime());
    }

    /**
     * Same as {@link #addHistoricalData(UUID, byte[], EpochTime)}.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final HistoricalData historicalData)
    {
        m_deviceImpl.addHistoricalData(uuid, historicalData);
    }

    /**
     * Same as {@link #addHistoricalData(UUID, byte[], EpochTime)} but for large datasets this is more efficient when writing to disk.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final Iterator<HistoricalData> historicalData)
    {
        m_deviceImpl.addHistoricalData(uuid, historicalData);
    }

    /**
     * Same as {@link #addHistoricalData(UUID, byte[], EpochTime)} but for large datasets this is more efficient when writing to disk.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final List<HistoricalData> historicalData)
    {
        addHistoricalData(uuid, historicalData.iterator());
    }

    /**
     * Same as {@link #addHistoricalData(UUID, byte[], EpochTime)} but for large datasets this is more efficient when writing to disk.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void addHistoricalData(final UUID uuid, final ForEach_Returning<HistoricalData> historicalData)
    {
        m_deviceImpl.addHistoricalData(uuid, historicalData);
    }

    /**
     * Returns whether the device is in any of the provided states.
     *
     * @see #is(BleDeviceState)
     */
    public final boolean isAny(BleDeviceState... states)
    {
        return m_deviceImpl.isAny(states);
    }

    /**
     * Returns whether the device is in all of the provided states.
     *
     * @see #isAny(BleDeviceState...)
     */
    public final boolean isAll(BleDeviceState... states)
    {
        return m_deviceImpl.isAll(states);
    }

    /**
     * Convenience method to tell you whether a call to {@link #connect()} (or overloads) has a chance of succeeding.
     * For example if the device is {@link BleDeviceState#CONNECTING_OVERALL} or {@link BleDeviceState#INITIALIZED}
     * then this will return <code>false</code>.
     */
    public final boolean isConnectable()
    {
        return m_deviceImpl.isConnectable();
    }

    /**
     * Returns whether the device is in the provided state.
     *
     * @see #isAny(BleDeviceState...)
     */
    public final boolean is(final BleDeviceState state)
    {
        return m_deviceImpl.is(state);
    }

    /**
     * Returns <code>true</code> if there is any bitwise overlap between the provided value and {@link #getStateMask()}.
     *
     * @see #isAll(int)
     */
    public final boolean isAny(final int mask_BleDeviceState)
    {
        return m_deviceImpl.isAny(mask_BleDeviceState);
    }

    /**
     * Returns <code>true</code> if there is complete bitwise overlap between the provided value and {@link #getStateMask()}.
     *
     * @see #isAny(int)
     */
    public final boolean isAll(final int mask_BleDeviceState)
    {
        return m_deviceImpl.isAll(mask_BleDeviceState);
    }

    /**
     * Similar to {@link #is(BleDeviceState)} and {@link #isAny(BleDeviceState...)} but allows you to give a simple query
     * made up of {@link BleDeviceState} and {@link Boolean} pairs. So an example would be
     * <code>myDevice.is({@link BleDeviceState#BLE_CONNECTING}, true, {@link BleDeviceState#RECONNECTING_LONG_TERM}, false)</code>.
     */
    public final boolean is(Object... query)
    {
        return m_deviceImpl.is(query);
    }


    /**
     * If {@link #is(BleDeviceState)} returns true for the given state (i.e. if
     * the device is in the given state) then this method will (a) return the
     * amount of time that the device has been in the state. Otherwise, this
     * will (b) return the amount of time that the device was *previously* in
     * that state. Otherwise, if the device has never been in the state, it will
     * (c) return 0.0 seconds. Case (b) might be useful for example for checking
     * how long you <i>were</i> connected for after becoming
     * {@link BleDeviceState#BLE_DISCONNECTED}, for analytics purposes or whatever.
     */
    public final Interval getTimeInState(BleDeviceState state)
    {
        return m_deviceImpl.getTimeInState(state);
    }

    /**
     * Overload of {@link #refreshGattDatabase(Interval)} which uses the default gatt refresh delay of {@link BleDeviceConfig#DEFAULT_GATT_REFRESH_DELAY}.
     */
    public final void refreshGattDatabase()
    {
        refreshGattDatabase(Interval.millis(BleDeviceConfig.DEFAULT_GATT_REFRESH_DELAY));
    }

    /**
     * This only applies to a device which is {@link BleDeviceState#BLE_CONNECTED}. This is meant to be used mainly after performing a
     * firmware update, and the Gatt database has changed. This will clear the device's gatt cache, and perform discover services again.
     * The device will drop out of {@link BleDeviceState#SERVICES_DISCOVERED}, and enter {@link BleDeviceState#DISCOVERING_SERVICES}. So,
     * you can listen in your device's {@link DeviceStateListener} for when it enters {@link BleDeviceState#SERVICES_DISCOVERED} to know
     * when the operation is complete.
     */
    public final void refreshGattDatabase(Interval gattPause)
    {
        m_deviceImpl.refreshGattDatabase(gattPause);
    }

    /**
     * Same as {@link #setName(String, UUID, ReadWriteListener)} but will not attempt to propagate the
     * name change to the remote device. Only {@link #getName_override()} will be affected by this.
     */
    public final void setName(final String name)
    {
        setName(name, null, null);
    }

    /**
     * Same as {@link #setName(String, UUID, ReadWriteListener)} but you can use this
     * if you don't care much whether the device name change actually successfully reaches
     * the remote device. The write will be attempted regardless.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent setName(final String name, final UUID characteristicUuid)
    {
        return setName(name, characteristicUuid, null);
    }

    /**
     * Sets the local name of the device and also attempts a {@link #write(java.util.UUID, byte[], ReadWriteListener)}
     * using the given {@link UUID}. Further calls to {@link #getName_override()} will immediately reflect the name given here.
     * Further calls to {@link #getName_native()}, {@link #getName_debug()} and {@link #getName_normalized()} will only reflect
     * the name given here if the write is successful. It is somewhat assumed that doing this write will cause the remote device
     * to use the new name given here for its device information service {@link Uuids#DEVICE_NAME}.
     * If {@link BleDeviceConfig#saveNameChangesToDisk} is <code>true</code> then this name
     * will always be returned for {@link #getName_override()}, even if you kill/restart the app.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent setName(final String name, final UUID characteristicUuid, final ReadWriteListener listener)
    {
        return m_deviceImpl.setName(name, characteristicUuid, listener);
    }

    /**
     * Clears any name previously provided through {@link #setName(String)} or overloads.
     */
    public final void clearName()
    {
        m_deviceImpl.clearName();
    }

    /**
     * By default returns the same value as {@link #getName_native()}.
     * If you call {@link #setName(String)} (or overloads)
     * then calling this will return the same string provided in that setter.
     */
    public final @Nullable(Prevalence.NEVER) String getName_override()
    {
        return m_deviceImpl.getName_override();
    }

    /**
     * Returns the raw, unmodified device name retrieved from the stack.
     * Equivalent to {@link BluetoothDevice#getName()}. It's suggested to use
     * {@link #getName_normalized()} if you're using the name to match/filter
     * against something, e.g. an entry in a config file or for advertising
     * filtering.
     */
    public final @Nullable(Prevalence.NEVER) String getName_native()
    {
        return m_deviceImpl.getName_native();
    }

    /**
     * The name retrieved from {@link #getName_native()} can change arbitrarily,
     * like the last 4 of the MAC address can get appended sometimes, and spaces
     * might get changed to underscores or vice-versa, caps to lowercase, etc.
     * This may somehow be standard, to-the-spec behavior but to the newcomer
     * it's confusing and potentially time-bomb-bug-inducing, like if you're
     * using device name as a filter for something and everything's working
     * until one day your app is suddenly broken and you don't know why. This
     * method is an attempt to normalize name behavior and always return the
     * same name regardless of the underlying stack's whimsy. The target format
     * is all lowercase and underscore-delimited with no trailing MAC address.
     */
    public final @Nullable(Prevalence.NEVER) String getName_normalized()
    {
        return m_deviceImpl.getName_normalized();
    }

    /**
     * Returns a name useful for logging and debugging. As of this writing it is
     * {@link #getName_normalized()} plus the last four digits of the device's
     * MAC address from {@link #getMacAddress()}. {@link BleDevice#toString()}
     * uses this.
     */
    public final @Nullable(Prevalence.NEVER) String getName_debug()
    {
        return m_deviceImpl.getName_debug();
    }

    /**
     * Provides just-in-case lower-level access to the native device instance.
     * <br><br>
     * WARNING: Be careful with this. It generally should not be needed. Only
     * invoke "mutators" of this object in times of extreme need.
     * <br><br>
     * NOTE: If you are forced to use this please contact library developers to
     * discuss possible feature addition or report bugs.
     */
    @Advanced
    public final @Nullable(Prevalence.RARE) BluetoothDevice getNative()
    {
        return m_deviceImpl.getNative().getNativeDevice();
    }

    /**
     * See pertinent warning for {@link #getNative()}. Generally speaking, this
     * will return <code>null</code> if the BleDevice is {@link BleDeviceState#BLE_DISCONNECTED}.
     * <br><br>
     * NOTE: If you are forced to use this please contact library developers to
     * discuss possible feature addition or report bugs.
     */
    @Advanced
    public final @Nullable(Prevalence.NORMAL) BluetoothGatt getNativeGatt()
    {
        return m_deviceImpl.getNativeGatt().getGatt();
    }

    /**
     * Returns the MAC address of this device, as retrieved from the native stack or provided through {@link BleManager#newDevice(String)} (or overloads thereof).
     * You may treat this as the unique ID of the device, suitable as a key in a {@link java.util.HashMap}, {@link android.content.SharedPreferences}, etc.
     */
    @Override public final @Nullable(Prevalence.NEVER) String getMacAddress()
    {
        return m_deviceImpl.getMacAddress();
    }

    /**
     * Same as {@link #bond()} but you can pass a listener to be notified of the details behind success or failure.
     *
     * @return same as {@link #bond()}.
     */
    public final @Nullable(Prevalence.NEVER) BondListener.BondEvent bond(BondListener listener)
    {
        return m_deviceImpl.bond(listener);
    }

    /**
     * Attempts to create a bond. Analogous to {@link BluetoothDevice#createBond()} This is also sometimes called
     * pairing, but while pairing and bonding are closely related, they are technically different from each other.
     * <br><br>
     * Bonding is required for reading/writing encrypted characteristics and,
     * anecdotally, may improve connection stability in some cases. This is
     * mentioned here and there on Internet threads complaining about Android
     * BLE so take it with a grain of salt because it has been directly observed
     * by us to degrade stability in some cases as well.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     * @see #unbond()
     */
    public final @Nullable(Prevalence.NEVER) BondListener.BondEvent bond()
    {
        return bond(null);
    }

    /**
     * Opposite of {@link #bond()}.
     *
     * @return <code>true</code> if successfully {@link BleDeviceState#UNBONDED}, <code>false</code> if already {@link BleDeviceState#UNBONDED}.
     * @see #bond()
     */
    public final boolean unbond()
    {
        return unbond(null);
    }

    /**
     * Opposite of {@link #bond(BondListener)}.
     *
     * @return <code>true</code> if successfully {@link BleDeviceState#UNBONDED}, <code>false</code> if already {@link BleDeviceState#UNBONDED}.
     * @see #bond()
     */
    public final boolean unbond(BondListener listener)
    {
        return m_deviceImpl.unbond(listener);
    }

    /**
     * Starts a connection process, or does nothing if already {@link BleDeviceState#BLE_CONNECTED} or {@link BleDeviceState#BLE_CONNECTING}.
     * Use {@link #setListener_Reconnect(DeviceReconnectFilter)} and {@link #setListener_State(DeviceStateListener)} to receive callbacks for
     * progress and errors.
     *
     * @return same as {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ConnectFailEvent connect()
    {
        return connect(null, null);
    }

    /**
     * Starts a connection process, or does nothing if already {@link BleDeviceState#BLE_CONNECTED} or {@link BleDeviceState#BLE_CONNECTING}.
     * Use {@link #setListener_Reconnect(DeviceReconnectFilter)} and {@link #setListener_State(DeviceStateListener)} to receive callbacks for more
     * thorough progress and errors.
     *
     * @return same as {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ConnectFailEvent connect(DeviceConnectListener connectListener)
    {
        return connect(null, null, connectListener);
    }

    /**
     * Same as {@link #connect()} but provides a hook for the app to do some kind of authentication handshake if it wishes. This is popular with
     * commercial BLE devices where you don't want hobbyists or competitors using your devices for nefarious purposes - like releasing a better application
     * for your device than you ;-). Usually the characteristics read/written inside this transaction are encrypted and so one way or another will require
     * the device to become {@link BleDeviceState#BONDED}. This should happen automatically for you, i.e you shouldn't need to call {@link #bond()} yourself.
     *
     * @return same as {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     * @see #connect()
     * @see BleDeviceState#AUTHENTICATING
     * @see BleDeviceState#AUTHENTICATED
     */
    public final @Nullable(Prevalence.NEVER) ConnectFailEvent connect(BleTransaction.Auth authenticationTxn)
    {
        return connect(authenticationTxn, null);
    }

    /**
     * Same as {@link #connect()} but provides a hook for the app to do some kind of initialization before it's considered fully
     * {@link BleDeviceState#INITIALIZED}. For example if you had a BLE-enabled thermometer you could use this transaction to attempt an initial
     * temperature read before updating your UI to indicate "full" connection success, even though BLE connection itself already succeeded.
     *
     * @return same as {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     * @see #connect()
     * @see BleDeviceState#INITIALIZING
     * @see BleDeviceState#INITIALIZED
     */
    public final @Nullable(Prevalence.NEVER) ConnectFailEvent connect(BleTransaction.Init initTxn)
    {
        return connect(null, initTxn);
    }

    /**
     * Combination of {@link #connect(BleTransaction.Auth)} and {@link #connect(BleTransaction.Init)}. See those two methods for explanation.
     *
     * @return same as {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     * @see #connect()
     * @see #connect(BleTransaction.Auth)
     * @see #connect(BleTransaction.Init)
     */
    public final @Nullable(Prevalence.NEVER) ConnectFailEvent connect(BleTransaction.Auth authenticationTxn, BleTransaction.Init initTxn)
    {
        return connect(authenticationTxn, initTxn, null);
    }

    /**
     * Same as {@link #connect(BleTransaction.Auth, BleTransaction.Init)} but calls {@link #setListener_State(DeviceStateListener)} and
     * {@link #setListener_Reconnect(DeviceReconnectFilter)} for you.
     *
     * @return If the attempt could not even "leave the gate" for some reason, a valid {@link ConnectFailEvent} is returned telling you why. Otherwise
     * this method will still return a non-null instance but {@link ConnectFailEvent#isNull()} will be <code>true</code>.
     * <br><br>
     * NOTE: your {@link DeviceReconnectFilter} will still be called even if this method early-outs.
     * <br><br>
     * TIP:	You can use the return value as an optimization. Many apps will call this method (or its overloads) and throw up a spinner until receiving a
     * callback to {@link DeviceReconnectFilter}. However if {@link ConnectFailEvent#isNull()} for the return value is <code>false</code>, meaning
     * the connection attempt couldn't even start for some reason, then you don't have to throw up the spinner in the first place.
     */
    public final @Nullable(Prevalence.NEVER) ConnectFailEvent connect(BleTransaction.Auth authenticationTxn, BleTransaction.Init initTxn, DeviceConnectListener connectionListener)
    {
        return m_deviceImpl.connect(authenticationTxn, initTxn, connectionListener);
    }

    /**
     * Disconnects from a connected device or does nothing if already {@link BleDeviceState#BLE_DISCONNECTED}. You can call this at any point
     * during the connection process as a whole, during reads and writes, during transactions, whenever, and the device will cleanly cancel all ongoing
     * operations. This method will also bring the device out of the {@link BleDeviceState#RECONNECTING_LONG_TERM} state.
     *
     * @return <code>true</code> if this call "had an effect", such as if the device was previously {@link BleDeviceState#RECONNECTING_LONG_TERM},
     * {@link BleDeviceState#CONNECTING_OVERALL}, or {@link BleDeviceState#INITIALIZED}
     * @see DeviceReconnectFilter.Status#EXPLICIT_DISCONNECT
     */
    public final boolean disconnect()
    {
        return m_deviceImpl.disconnect();
    }

    /**
     * Similar to {@link #disconnect()} with the difference being the disconnect task is set to a low priority. This allows all current calls to finish
     * executing before finally disconnecting. Note that this can cause issues if you keep executing reads/writes, as they have a higher priority.
     *
     * @return <code>true</code> if this call "had an effect", such as if the device was previously {@link BleDeviceState#RECONNECTING_LONG_TERM},
     * {@link BleDeviceState#CONNECTING_OVERALL}, or {@link BleDeviceState#INITIALIZED}
     * @see DeviceReconnectFilter.Status#EXPLICIT_DISCONNECT
     */
    public final boolean disconnectWhenReady()
    {
        return m_deviceImpl.disconnectWhenReady();
    }

    /**
     * Same as {@link #disconnect()} but this call roughly simulates the disconnect as if it's because of the remote device going down, going out of range, etc.
     * For example {@link #getLastDisconnectIntent()} will be {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent#UNINTENTIONAL} instead of
     * {@link com.idevicesinc.sweetblue.utils.State.ChangeIntent#INTENTIONAL}.
     * <br><br>
     * If the device is currently {@link BleDeviceState#CONNECTING_OVERALL} then your
     * {@link DeviceReconnectFilter#onConnectFailed(ReconnectFilter.ConnectFailEvent)}
     * implementation will be called with {@link DeviceReconnectFilter.Status#ROGUE_DISCONNECT}.
     * <br><br>
     * NOTE: One major difference between this and an actual remote disconnect is that this will not cause the device to enter
     * {@link BleDeviceState#RECONNECTING_SHORT_TERM} or {@link BleDeviceState#RECONNECTING_LONG_TERM}.
     */
    public final boolean disconnect_remote()
    {
        return m_deviceImpl.disconnect_remote();
    }

    /**
     * Convenience method that calls {@link BleManager#undiscover(BleDevice)}.
     *
     * @return <code>true</code> if the device was successfully {@link BleDeviceState#UNDISCOVERED}, <code>false</code> if BleDevice isn't known to the {@link BleManager}.
     * @see BleManager#undiscover(BleDevice)
     */
    public final boolean undiscover()
    {
        return m_deviceImpl.undiscover();
    }

    /**
     * Convenience forwarding of {@link BleManager#clearSharedPreferences(String)}.
     *
     * @see BleManager#clearSharedPreferences(BleDevice)
     */
    public final void clearSharedPreferences()
    {
        m_deviceImpl.clearSharedPreferences();
    }

    /**
     * First checks referential equality and if <code>false</code> checks
     * equality of {@link #getMacAddress()}. Note that ideally this method isn't
     * useful to you and never returns true (besides the identity case, which
     * isn't useful to you). Otherwise it probably means your app is holding on
     * to old references that have been undiscovered, and this may be a bug or
     * bad design decision in your code. This library will (well, should) never
     * hold references to two devices such that this method returns true for them.
     */
    public final boolean equals(@Nullable(Prevalence.NORMAL) final BleDevice device_nullable)
    {
        return m_deviceImpl.equals(device_nullable.getIBleDevice());
    }

    /**
     * Returns {@link #equals(BleDevice)} if object is an instance of {@link BleDevice}. Otherwise calls super.
     *
     * @see BleDevice#equals(BleDevice)
     */
    @Override public final boolean equals(@Nullable(Prevalence.NORMAL) final Object object_nullable)
    {
        return m_deviceImpl.equals(object_nullable);
    }

    /**
     * Starts a periodic read of a particular characteristic. Use this wherever you can in place of {@link #enableNotify(UUID, ReadWriteListener)}. One
     * use case would be to periodically read wind speed from a weather device. You *could* develop your device firmware to send notifications to the app
     * only when the wind speed changes, but Android has observed stability issues with notifications, so use them only when needed.
     * <br><br>
     * TIP: You can call this method when the device is in any {@link BleDeviceState}, even {@link BleDeviceState#BLE_DISCONNECTED}.
     *
     * @see #startChangeTrackingPoll(UUID, Interval, ReadWriteListener)
     * @see #enableNotify(UUID, ReadWriteListener)
     * @see #stopPoll(UUID, ReadWriteListener)
     */
    public final void startPoll(final UUID characteristicUuid, final Interval interval, final ReadWriteListener listener)
    {
        startPoll(null, characteristicUuid, interval, listener);
    }

    /**
     * Same as {@link #startPoll(java.util.UUID, Interval, ReadWriteListener)} but without a listener.
     * <br><br>
     * See {@link #read(java.util.UUID)} for an explanation of why you would do this.
     */
    public final void startPoll(final UUID characteristicUuid, final Interval interval)
    {
        startPoll(characteristicUuid, interval, null);
    }

    /**
     * Overload of {@link #startPoll(UUID, Interval, ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     */
    public final void startPoll(final UUID serviceUuid, final UUID characteristicUuid, final Interval interval, final ReadWriteListener listener)
    {
        startPoll(serviceUuid, characteristicUuid, null, interval, listener);
    }

    /**
     * Overload of {@link #startPoll(UUID, UUID, Interval, ReadWriteListener)} for when you have characteristics with identical uuids under the same service.
     */
    public final void startPoll(final UUID serviceUuid, final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final Interval interval, final ReadWriteListener listener)
    {
        final BleRead read = new BleRead(serviceUuid, characteristicUuid).setDescriptorFilter(descriptorFilter).setReadWriteListener(listener);
        startPoll(read, interval);
    }

    /**
     * Starts a periodic read of a particular characteristic. Use this wherever you can in place of {@link #enableNotify(UUID, ReadWriteListener)}. One
     * use case would be to periodically read wind speed from a weather device. You *could* develop your device firmware to send notifications to the app
     * only when the wind speed changes, but Android has observed stability issues with notifications, so use them only when needed.
     * <br><br>
     * TIP: You can call this method when the device is in any {@link BleDeviceState}, even {@link BleDeviceState#BLE_DISCONNECTED}.
     *
     * @see #startChangeTrackingPoll(UUID, Interval, ReadWriteListener)
     * @see #enableNotify(UUID, ReadWriteListener)
     * @see #stopPoll(UUID, ReadWriteListener)
     */
    public final void startPoll(BleRead read, Interval interval)
    {
        m_deviceImpl.startPoll(read, interval);
    }

    /**
     * Overload of {@link #startPoll(UUID, Interval)} for when you have characteristics with identical uuids under different services.
     */
    public final void startPoll(final UUID serviceUuid, final UUID characteristicUuid, final Interval interval)
    {
        startPoll(serviceUuid, characteristicUuid, interval, null);
    }

    /**
     * Convenience to call {@link #startPoll(java.util.UUID, Interval, ReadWriteListener)} for multiple
     * characteristic uuids all at once.
     */
    public final void startPoll(final UUID[] charUuids, final Interval interval, final ReadWriteListener listener)
    {
        for (int i = 0; i < charUuids.length; i++)
        {
            startPoll(charUuids[i], interval, listener);
        }
    }

    /**
     * Same as {@link #startPoll(java.util.UUID[], Interval, ReadWriteListener)} but without a listener.
     * <br><br>
     * See {@link #read(java.util.UUID)} for an explanation of why you would do this.
     */
    public final void startPoll(final UUID[] charUuids, final Interval interval)
    {
        startPoll(charUuids, interval, null);
    }

    /**
     * Convenience to call {@link #startPoll(java.util.UUID, Interval, ReadWriteListener)} for multiple
     * characteristic uuids all at once.
     */
    public final void startPoll(final Iterable<UUID> charUuids, final Interval interval, final ReadWriteListener listener)
    {
        final Iterator<UUID> iterator = charUuids.iterator();

        while (iterator.hasNext())
        {
            final UUID ith = iterator.next();

            startPoll(ith, interval, listener);
        }
    }

    /**
     * Same as {@link #startPoll(java.util.UUID[], Interval, ReadWriteListener)} but without a listener.
     * <br><br>
     * See {@link #read(java.util.UUID)} for an explanation of why you would do this.
     */
    public final void startPoll(final Iterable<UUID> charUuids, final Interval interval)
    {
        startPoll(charUuids, interval, null);
    }

    /**
     * Convenience to call {@link #startChangeTrackingPoll(java.util.UUID, Interval, ReadWriteListener)} for multiple
     * characteristic uuids all at once.
     */
    public final void startChangeTrackingPoll(final UUID[] charUuids, final Interval interval, final ReadWriteListener listener)
    {
        for (int i = 0; i < charUuids.length; i++)
        {
            startChangeTrackingPoll(charUuids[i], interval, listener);
        }
    }

    /**
     * Same as {@link #startChangeTrackingPoll(java.util.UUID[], Interval, ReadWriteListener)} but without a listener.
     * <br><br>
     * See {@link #read(java.util.UUID)} for an explanation of why you would do this.
     */
    public final void startChangeTrackingPoll(final UUID[] charUuids, final Interval interval)
    {
        startChangeTrackingPoll(charUuids, interval, null);
    }

    /**
     * Convenience to call {@link #startChangeTrackingPoll(java.util.UUID, Interval, ReadWriteListener)} for multiple
     * characteristic uuids all at once.
     */
    public final void startChangeTrackingPoll(final Iterable<UUID> charUuids, final Interval interval, final ReadWriteListener listener)
    {
        final Iterator<UUID> iterator = charUuids.iterator();

        while (iterator.hasNext())
        {
            final UUID ith = iterator.next();

            startChangeTrackingPoll(ith, interval, listener);
        }
    }

    /**
     * Same as {@link #startChangeTrackingPoll(java.util.UUID[], Interval, ReadWriteListener)} but without a listener.
     * <br><br>
     * See {@link #read(java.util.UUID)} for an explanation of why you would do this.
     */
    public final void startChangeTrackingPoll(final Iterable<UUID> charUuids, final Interval interval)
    {
        startChangeTrackingPoll(charUuids, interval, null);
    }

    /**
     * Similar to {@link #startPoll(UUID, Interval, ReadWriteListener)} but only
     * invokes a callback when a change in the characteristic value is detected.
     * Use this in preference to {@link #enableNotify(UUID, ReadWriteListener)} if possible,
     * due to instability issues (rare, but still) with notifications on Android.
     * <br><br>
     * TIP: You can call this method when the device is in any {@link BleDeviceState}, even {@link BleDeviceState#BLE_DISCONNECTED}.
     */
    public final void startChangeTrackingPoll(final UUID characteristicUuid, final Interval interval, final ReadWriteListener listener)
    {
        startChangeTrackingPoll(null, characteristicUuid, null, interval, listener);
    }

    /**
     * Overload of {@link #startChangeTrackingPoll(UUID, Interval, ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     */
    public final void startChangeTrackingPoll(final UUID serviceUuid, final UUID characteristicUuid, final Interval interval, final ReadWriteListener listener)
    {
        startChangeTrackingPoll(serviceUuid, characteristicUuid, null, interval, listener);
    }

    /**
     * Overload of {@link #startChangeTrackingPoll(UUID, UUID, Interval, ReadWriteListener)} for when you have characteristics with identical uuids under the same service.
     */
    public final void startChangeTrackingPoll(final UUID serviceUuid, final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final Interval interval, final ReadWriteListener listener)
    {
        final BleRead read = new BleRead(serviceUuid, characteristicUuid).setDescriptorFilter(descriptorFilter).setReadWriteListener(listener);
        startChangeTrackingPoll(read, interval);
    }

    /**
     * Similar to {@link #startPoll(BleRead, Interval)} but only
     * invokes a callback when a change in the characteristic value is detected.
     * Use this in preference to {@link #enableNotify(UUID, ReadWriteListener)} if possible,
     * due to instability issues (rare, but still) with notifications on Android.
     * <br><br>
     * TIP: You can call this method when the device is in any {@link BleDeviceState}, even {@link BleDeviceState#BLE_DISCONNECTED}.
     */
    public final void startChangeTrackingPoll(BleRead read, Interval interval)
    {
        m_deviceImpl.startChangeTrackingPoll(read, interval);
    }

    /**
     * Stops a poll(s) started by either {@link #startPoll(UUID, Interval, ReadWriteListener)} or
     * {@link #startChangeTrackingPoll(UUID, Interval, ReadWriteListener)}. This will stop all polls matching the provided parameters.
     *
     * @see #startPoll(UUID, Interval, ReadWriteListener)
     * @see #startChangeTrackingPoll(UUID, Interval, ReadWriteListener)
     */
    public final void stopPoll(final UUID characteristicUuid, final ReadWriteListener listener)
    {
        stopPoll(null, characteristicUuid, listener);
    }

    /**
     * Same as {@link #stopPoll(java.util.UUID, ReadWriteListener)} but without the listener.
     */
    public final void stopPoll(final UUID characteristicUuid)
    {
        stopPoll(characteristicUuid, (ReadWriteListener) null);
    }

    /**
     * Same as {@link #stopPoll(UUID, ReadWriteListener)} but with added filtering for the poll {@link Interval}.
     */
    public final void stopPoll(final UUID characteristicUuid, final Interval interval, final ReadWriteListener listener)
    {
        stopPoll(null, characteristicUuid, interval, listener);
    }

    /**
     * Same as {@link #stopPoll(java.util.UUID, Interval, ReadWriteListener)} but without the listener.
     */
    public final void stopPoll(final UUID characteristicUuid, final Interval interval)
    {
        stopPoll(null, characteristicUuid, interval);
    }

    /**
     * Overload of {@link #stopPoll(UUID, ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     */
    public final void stopPoll(final UUID serviceUuid, final UUID characteristicUuid, final ReadWriteListener listener)
    {
        stopPoll(serviceUuid, characteristicUuid, null, listener);
    }

    /**
     * Overload of {@link #stopPoll(UUID)} for when you have characteristics with identical uuids under different services.
     */
    public final void stopPoll(final UUID serviceUuid, final UUID characteristicUuid)
    {
        stopPoll(serviceUuid, characteristicUuid, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #stopPoll(UUID, Interval, ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     */
    public final void stopPoll(final UUID serviceUuid, final UUID characteristicUuid, final Interval interval, final ReadWriteListener listener)
    {
        stopPoll(serviceUuid, characteristicUuid, null, interval, listener);
    }

    /**
     * Overload of {@link #stopPoll(UUID, Interval)} for when you have characteristics with identical uuids under different services.
     */
    public final void stopPoll(final UUID serviceUuid, final UUID characteristicUuid, final Interval interval)
    {
        stopPoll(serviceUuid, characteristicUuid, interval, null);
    }

    /**
     * Overload of {@link #stopPoll(UUID, UUID, Interval, ReadWriteListener)} for when you have characteristics with identical uuids under the same service.
     */
    public final void stopPoll(final UUID serviceUuid, final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final Interval interval, final ReadWriteListener listener)
    {
        final BleRead read = new BleRead(serviceUuid, characteristicUuid).setDescriptorFilter(descriptorFilter).setReadWriteListener(listener);
        stopPoll(read, interval);
    }

    /**
     * Stop a poll with the given {@link BleRead}, and interval.
     */
    public final void stopPoll(BleRead read, Interval interval)
    {
        m_deviceImpl.stopPoll(read, interval);
    }

    /**
     * Calls {@link #stopPoll(java.util.UUID, Interval, ReadWriteListener)} multiple times for you.
     */
    public final void stopPoll(final UUID[] uuids, final Interval interval, final ReadWriteListener listener)
    {
        for (int i = 0; i < uuids.length; i++)
        {
            stopPoll(uuids[i], interval, listener);
        }
    }

    /**
     * Calls {@link #stopPoll(java.util.UUID, Interval)} multiple times for you.
     */
    public final void stopPoll(final UUID[] uuids, final Interval interval)
    {
        stopPoll(uuids, interval, null);
    }

    /**
     * Calls {@link #stopPoll(java.util.UUID)} multiple times for you.
     */
    public final void stopPoll(final UUID[] uuids)
    {
        stopPoll(uuids, null, null);
    }

    /**
     * Calls {@link #stopPoll(java.util.UUID, Interval, ReadWriteListener)} multiple times for you.
     */
    public final void stopPoll(final Iterable<UUID> uuids, final Interval interval, final ReadWriteListener listener)
    {
        final Iterator<UUID> iterator = uuids.iterator();

        while (iterator.hasNext())
        {
            final UUID ith = iterator.next();

            stopPoll(ith, interval, listener);
        }
    }

    /**
     * Calls {@link #stopPoll(java.util.UUID, Interval)} multiple times for you.
     */
    public final void stopPoll(final Iterable<UUID> uuids, final Interval interval)
    {
        stopPoll(uuids, interval, null);
    }

    /**
     * Calls {@link #stopPoll(java.util.UUID)} multiple times for you.
     */
    public final void stopPoll(final Iterable<UUID> uuids)
    {
        stopPoll(uuids, null, null);
    }


    /**
     * Overload for {@link #write(BleWrite)}.
     */
    public final void writeMany(BleWrite[] writes)
    {
        for (int i = 0; i < writes.length; i++)
        {
            final BleWrite write = writes[i];

            write(write);
        }
    }

    /**
     * Overload for {@link #write(BleWrite)}.
     */
    public final void writeMany(Iterable<BleWrite> writes)
    {
        final Iterator<BleWrite> iterator = writes.iterator();

        while (iterator.hasNext())
        {
            final BleWrite write = iterator.next();

            write(write);
        }
    }

    /**
     * Writes to the device without a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(BleWrite bleWrite)
    {
        return m_deviceImpl.write(bleWrite);
    }

    /**
     * Writes to the device with a callback.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(BleWrite bleWrite, ReadWriteListener listener)
    {
        return write(bleWrite.setReadWriteListener(listener));
    }

    /**
     * Writes to the device without a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final byte[] data)
    {
        return write(characteristicUuid, new PresentData(data), (ReadWriteListener) null);
    }

    /**
     * Writes to the device without a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final byte[] data)
    {
        return write(characteristicUuid, new PresentData(data), descriptorFilter, (ReadWriteListener) null);
    }

    /**
     * Writes to the device with a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final byte[] data, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return write(serviceUuid, characteristicUuid, new PresentData(data), listener);
    }

    /**
     * Writes to the device with a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final byte[] data, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return write(serviceUuid, characteristicUuid, new PresentData(data), descriptorFilter, listener);
    }

    /**
     * Overload of {@link #write(UUID, byte[])} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final byte[] data)
    {
        return write(serviceUuid, characteristicUuid, new PresentData(data), (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #write(UUID, DescriptorFilter, byte[])} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final DescriptorFilter filter, final byte[] data)
    {
        return write(serviceUuid, characteristicUuid, new PresentData(data), filter, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #write(UUID, byte[], ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final byte[] data, final ReadWriteListener listener)
    {
        return write(serviceUuid, characteristicUuid, new PresentData(data), listener);
    }

    /**
     * Overload of {@link #write(UUID, byte[], ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final byte[] data, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        return write(serviceUuid, characteristicUuid, new PresentData(data), descriptorFilter, listener);
    }

    /**
     * Writes to the device without a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final FutureData futureData)
    {
        return write(characteristicUuid, futureData, (ReadWriteListener) null);
    }

    /**
     * Writes to the device with a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final FutureData futureData, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return write(serviceUuid, characteristicUuid, futureData, listener);
    }

    /**
     * Writes to the device with a callback.
     *
     * @return same as {@link #write(BleWrite, ReadWriteListener)}.
     * @see #write(BleWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID characteristicUuid, final FutureData futureData, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return write(serviceUuid, characteristicUuid, futureData, descriptorFilter, listener);
    }

    /**
     * Overload of {@link #write(UUID, FutureData)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final FutureData futureData)
    {
        return write(serviceUuid, characteristicUuid, futureData, (ReadWriteListener) null);
    }


    /**
     * Overload of {@link #write(UUID, FutureData)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final FutureData futureData, final DescriptorFilter descriptorFilter)
    {
        return write(serviceUuid, characteristicUuid, futureData, descriptorFilter, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #write(UUID, FutureData, ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final FutureData futureData, final ReadWriteListener listener)
    {
        return write(serviceUuid, characteristicUuid, futureData, null, listener);
    }

    /**
     * Overload of {@link #write(UUID, FutureData, ReadWriteListener)} for when you have characteristics with identical uuids under the same service.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleWrite)}, or {@link #write(BleWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(final UUID serviceUuid, final UUID characteristicUuid, final FutureData futureData, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        final BleWrite builder = new BleWrite(serviceUuid, characteristicUuid).setDescriptorFilter(descriptorFilter).
                setReadWriteListener(listener).setData(futureData);
        return write(builder);
    }

    /**
     * Writes to the device descriptor without a callback.
     *
     * @return same as {@link #write(BleDescriptorWrite, ReadWriteListener)}.
     * @see #write(BleDescriptorWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID descriptorUuid, final byte[] data)
    {
        return writeDescriptor(descriptorUuid, data, (ReadWriteListener) null);
    }

    /**
     * Writes to the device descriptor with a callback.
     *
     * @return same as {@link #write(BleDescriptorWrite, ReadWriteListener)}.
     * @see #write(BleDescriptorWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID descriptorUuid, final byte[] data, final ReadWriteListener listener)
    {
        final UUID characteristicUuid = null;

        return writeDescriptor(characteristicUuid, descriptorUuid, data, listener);
    }

    /**
     * Overload of {@link #writeDescriptor(UUID, byte[])} for when you have descriptors with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID characteristicUuid, final UUID descriptorUuid, final byte[] data)
    {
        return writeDescriptor(characteristicUuid, descriptorUuid, data, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #writeDescriptor(UUID, byte[], ReadWriteListener)} for when you have descriptors with identical uuids under different characteristics.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID characteristicUuid, final UUID descriptorUuid, final byte[] data, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return writeDescriptor(serviceUuid, characteristicUuid, descriptorUuid, data, listener);
    }

    /**
     * Overload of {@link #writeDescriptor(UUID, byte[], ReadWriteListener)} for when you have descriptors with identical uuids under different characteristics and/or services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID serviceUuid, final UUID characteristicUuid, final UUID descriptorUuid, final byte[] data, final ReadWriteListener listener)
    {
        return writeDescriptor(serviceUuid, characteristicUuid, descriptorUuid, new PresentData(data), listener);
    }

    /**
     * Writes to the device descriptor without a callback.
     *
     * @return same as {@link #write(BleDescriptorWrite, ReadWriteListener)}.
     * @see #write(BleDescriptorWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID descriptorUuid, final FutureData futureData)
    {
        return writeDescriptor(descriptorUuid, futureData, (ReadWriteListener) null);
    }

    /**
     * Writes to the device with a callback.
     *
     * @return same as {@link #write(BleDescriptorWrite, ReadWriteListener)}.
     * @see #write(BleDescriptorWrite, ReadWriteListener)
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID descriptorUuid, final FutureData futureData, final ReadWriteListener listener)
    {
        final UUID characteristicUuid = null;

        return writeDescriptor(characteristicUuid, descriptorUuid, futureData, listener);
    }

    /**
     * Overload of {@link #writeDescriptor(UUID, byte[])} for when you have descriptors with identical uuids under different services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID characteristicUuid, final UUID descriptorUuid, final FutureData futureData)
    {
        return writeDescriptor(characteristicUuid, descriptorUuid, futureData, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #writeDescriptor(UUID, byte[], ReadWriteListener)} for when you have descriptors with identical uuids under different characteristics.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID characteristicUuid, final UUID descriptorUuid, final FutureData futureData, final ReadWriteListener listener)
    {
        return writeDescriptor(null, characteristicUuid, descriptorUuid, futureData, listener);
    }

    /**
     * Overload of {@link #writeDescriptor(UUID, byte[], ReadWriteListener)} for when you have descriptors with identical uuids under different characteristics and/or services.
     *
     * @deprecated to be removed in 3.1, use {@link #write(BleDescriptorWrite)}, or {@link #write(BleDescriptorWrite, ReadWriteListener)} instead
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent writeDescriptor(final UUID serviceUuid, final UUID characteristicUuid, final UUID descriptorUuid, final FutureData futureData, final ReadWriteListener listener)
    {
        final BleDescriptorWrite builder = new BleDescriptorWrite(serviceUuid, characteristicUuid, descriptorUuid).setData(futureData).
                setReadWriteListener(listener);
        return write(builder);
    }

    /**
     * Writes to the device descriptor without a callback.
     *
     * @return same as {@link #write(BleDescriptorWrite, ReadWriteListener)}.
     * @see #write(BleDescriptorWrite, ReadWriteListener)
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(BleDescriptorWrite write)
    {
        return m_deviceImpl.write(write);
    }

    /**
     * Writes to the device descriptor with a callback.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent write(BleDescriptorWrite write, ReadWriteListener listener)
    {
        write.setReadWriteListener(listener);
        return write(write);
    }

    /**
     * Overload of {@link #read(BleDescriptorRead)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleDescriptorRead)} instead.
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent readDescriptor(final UUID descriptorUuid)
    {
        return readDescriptor(descriptorUuid, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #read(BleDescriptorRead)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleDescriptorRead)} instead.
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent readDescriptor(final UUID descriptorUuid, final ReadWriteListener listener)
    {
        final UUID characteristicUuid = null;

        return readDescriptor(characteristicUuid, descriptorUuid, listener);
    }

    /**
     * Overload of {@link #read(BleDescriptorRead)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleDescriptorRead)} instead.
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent readDescriptor(final UUID characteristicUuid, final UUID descriptorUuid)
    {
        return readDescriptor(characteristicUuid, descriptorUuid, (ReadWriteListener) null);
    }

    /**
     * Overload of {@link #read(BleDescriptorRead)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleDescriptorRead)} instead.
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent readDescriptor(final UUID characteristicUuid, final UUID descriptorUuid, final ReadWriteListener listener)
    {
        return readDescriptor(null, characteristicUuid, descriptorUuid, listener);
    }

    /**
     * Overload of {@link #read(BleDescriptorRead)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleDescriptorRead)} instead.
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent readDescriptor(final UUID serviceUuid, final UUID characteristicUuid, final UUID descriptorUuid)
    {
        return readDescriptor(serviceUuid, characteristicUuid, descriptorUuid, null);
    }

    /**
     * Overload of {@link #read(BleDescriptorRead)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleDescriptorRead)} instead.
     */
    @Deprecated
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent readDescriptor(final UUID serviceUuid, final UUID characteristicUuid, final UUID descriptorUuid, final ReadWriteListener listener)
    {
        final BleDescriptorRead read = new BleDescriptorRead(serviceUuid, characteristicUuid, descriptorUuid).setReadWriteListener(listener);
        return read(read);
    }

    /**
     * Reads a descriptor from the device with a callback, if one is set in the provided {@link BleRead}.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent read(final BleDescriptorRead read)
    {
        return m_deviceImpl.read(read);
    }

    /**
     * Same as {@link #readRssi(ReadWriteListener)} but use this method when you don't much care when/if the RSSI is actually updated.
     *
     * @return same as {@link #readRssi(ReadWriteListener)}.
     */
    public final ReadWriteListener.ReadWriteEvent readRssi()
    {
        return readRssi(null);
    }

    /**
     * Wrapper for {@link BluetoothGatt#readRemoteRssi()}. This will eventually update the value returned by {@link #getRssi()} but it is not
     * instantaneous. When a new RSSI is actually received the given listener will be called. The device must be {@link BleDeviceState#BLE_CONNECTED} for
     * this call to succeed. When the device is not {@link BleDeviceState#BLE_CONNECTED} then the value returned by
     * {@link #getRssi()} will be automatically updated every time this device is discovered (or rediscovered) by a scan operation.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final ReadWriteListener.ReadWriteEvent readRssi(final ReadWriteListener listener)
    {
        return m_deviceImpl.readRssi(listener);
    }

    /**
     * Same as {@link #setConnectionPriority(BleConnectionPriority, ReadWriteListener)} but use this method when you don't much care when/if the connection priority is updated.
     *
     * @return same as {@link #setConnectionPriority(BleConnectionPriority, ReadWriteListener)}.
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent setConnectionPriority(final BleConnectionPriority connectionPriority)
    {
        return setConnectionPriority(connectionPriority, null);
    }

    /**
     * Wrapper for {@link BluetoothGatt#requestConnectionPriority(int)} which attempts to change the connection priority for a given connection.
     * This will eventually update the value returned by {@link #getConnectionPriority()} but it is not
     * instantaneous. When we receive confirmation from the native stack then this value will be updated. The device must be {@link BleDeviceState#BLE_CONNECTED} for
     * this call to succeed.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     * @see #setConnectionPriority(BleConnectionPriority, ReadWriteListener)
     * @see #getConnectionPriority()
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) ReadWriteListener.ReadWriteEvent setConnectionPriority(final BleConnectionPriority connectionPriority, final ReadWriteListener listener)
    {
        return m_deviceImpl.setConnectionPriority(connectionPriority, listener);
    }

    /**
     * Returns the connection priority value set by {@link #setConnectionPriority(BleConnectionPriority, ReadWriteListener)}, or {@link BleDeviceConfig#DEFAULT_MTU_SIZE} if
     * it was never set explicitly.
     */
    @Advanced
    public final BleConnectionPriority getConnectionPriority()
    {
        return m_deviceImpl.getConnectionPriority();
    }

    /**
     * Returns the "maximum transmission unit" value set by {@link #negotiateMtu(int, ReadWriteListener)}, or {@link BleDeviceConfig#DEFAULT_MTU_SIZE} if
     * it was never set explicitly.
     */
    @Advanced
    public final int getMtu()
    {
        return m_deviceImpl.getMtu();
    }

    /**
     * Same as {@link #negotiateMtuToDefault(ReadWriteListener)} but use this method when you don't much care when/if the "maximum transmission unit" is actually updated.
     *
     * @return same as {@link #negotiateMtu(int, ReadWriteListener)}.
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent negotiateMtuToDefault()
    {
        return negotiateMtuToDefault(null);
    }

    /**
     * Overload of {@link #negotiateMtu(int, ReadWriteListener)} that returns the "maximum transmission unit" to the default.
     * Unlike {@link #negotiateMtu(int)}, this can be called when the device is {@link BleDeviceState#BLE_DISCONNECTED} in the event that you don't want the
     * MTU to be auto-set upon next reconnection.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent negotiateMtuToDefault(final ReadWriteListener listener)
    {
        return m_deviceImpl.negotiateMtuToDefault(listener);
    }

    /**
     * Same as {@link #negotiateMtu(int, ReadWriteListener)}, which passes <code>null</code> for the listener, if you don't care about the result, or are using
     * a global listener via {@link #setListener_ReadWrite(ReadWriteListener)}, or {@link BleManager#setListener_Read_Write(ReadWriteListener)}.
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent negotiateMtu(int mtu)
    {
        return negotiateMtu(mtu, null);
    }

    /**
     * Wrapper for {@link BluetoothGatt#requestMtu(int)} which attempts to change the "maximum transmission unit" for a given connection.
     * This will eventually update the value returned by {@link #getMtu()} but it is not
     * instantaneous. When we receive confirmation from the native stack then this value will be updated. The device must be {@link BleDeviceState#BLE_CONNECTED} for
     * this call to succeed. Note that this is a negotiation between android and the bluetooth peripheral, so it's possible to request a different MTU,
     * and end up with it not changing, but the callback comes back as a success.
     *
     * <b>NOTE 1:</b> This will only work on devices running Android Lollipop (5.0) or higher. Otherwise it will be ignored.
     * <b>NOTE 2:</b> Some phones will request an MTU, and accept a higher number, but will fail (time out) when writing a characteristic with a large
     * payload. Namely, we've found the Moto Pure X, and the OnePlus OnePlus2 to have this behavior. For those phones any MTU above
     * 50 failed.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    @Advanced
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent negotiateMtu(final int mtu, final ReadWriteListener listener)
    {
        return m_deviceImpl.negotiateMtu(mtu, listener);
    }

    /**
     * Overload of {@link #setPhyOptions(Phy, ReadWriteListener)}, which passes no listener.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent setPhyOptions(Phy phyOptions)
    {
        return setPhyOptions(phyOptions, null);
    }

    /**
     * Use this method to manually set the bluetooth 5 physical layer to use a bluetooth 5 feature (high speed/long range).
     * <b>NOTE: This only works on Android Oreo and above, and not all devices on this OS have bluetooth 5 hardware.</b>
     *
     * @see BleManager#isBluetooth5Supported()
     * @see BleManager#isBluetooth5HighSpeedSupported()
     * @see BleManager#isBluetooth5LongRangeSupported()
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent setPhyOptions(Phy phyOptions, ReadWriteListener listener)
    {
        return m_deviceImpl.setPhyOptions(phyOptions, listener);
    }

    /**
     * Overload of {@link #readPhyOptions(ReadWriteListener)}, which passes no listener.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent readPhyOptions()
    {
        return readPhyOptions(null);
    }

    /**
     * Method to get the current "phy options" (physical layer), or current bluetooth 5 feature. This shouldn't need to be called, as SweetBlue caches this info for you, but it's here
     * for flexibility, and just-in-case scenarios.
     */
    public final @Nullable(Prevalence.NEVER) ReadWriteEvent readPhyOptions(ReadWriteListener listener)
    {
        return m_deviceImpl.readPhyOptions(listener);
    }

    /**
     * Same as {@link #startPoll(UUID, Interval, ReadWriteListener)} but for when you don't care when/if the RSSI is actually updated.
     * <br><br>
     * TIP: You can call this method when the device is in any {@link BleDeviceState}, even {@link BleDeviceState#BLE_DISCONNECTED}, however, a scan must
     * be running in order to receive any updates (and of course, the device must be found in the scan).
     */
    public final void startRssiPoll(final Interval interval)
    {
        startRssiPoll(interval, null);
    }

    /**
     * Kicks off a poll that automatically calls {@link #readRssi(ReadWriteListener)} at the {@link Interval} frequency
     * specified. This can be called before the device is actually {@link BleDeviceState#BLE_CONNECTED}. If you call this more than once in a
     * row then the most recent call's parameters will be respected.
     * <br><br>
     * TIP: You can call this method when the device is in any {@link BleDeviceState}, even {@link BleDeviceState#BLE_DISCONNECTED}, however, a scan must
     * be running in order to receive any updates (and of course, the device must be found in the scan).
     */
    public final void startRssiPoll(final Interval interval, final ReadWriteListener listener)
    {
        m_deviceImpl.startRssiPoll(interval, listener);
    }

    /**
     * Stops an RSSI poll previously started either by {@link #startRssiPoll(Interval)} or {@link #startRssiPoll(Interval, ReadWriteListener)}.
     */
    public final void stopRssiPoll()
    {
        m_deviceImpl.stopRssiPoll();
    }

    /**
     * One method to remove absolutely all "metadata" related to this device that is stored on disk and/or cached in memory in any way.
     * This method is useful if for example you have a "forget device" feature in your app.
     */
    public final void clearAllData()
    {
        m_deviceImpl.clearAllData();
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData()
    {
        m_deviceImpl.clearHistoricalData();
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final long count)
    {
        clearHistoricalData(EpochTimeRange.FROM_MIN_TO_MAX, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final EpochTimeRange range)
    {
        clearHistoricalData(range, Long.MAX_VALUE);
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final EpochTimeRange range, final long count)
    {
        m_deviceImpl.clearHistoricalData(range, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID}.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final UUID uuid)
    {
        clearHistoricalData(uuid, EpochTimeRange.FROM_MIN_TO_MAX, Long.MAX_VALUE);
    }

    /**
     * Overload of {@link #clearHistoricalData(UUID)} that just calls that method multiple times.
     */
    public final void clearHistoricalData(final UUID... uuids)
    {
        for (int i = 0; i < uuids.length; i++)
        {
            final UUID ith = uuids[i];

            clearHistoricalData(ith);
        }
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID}.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final UUID uuid, final long count)
    {
        clearHistoricalData(uuid, EpochTimeRange.FROM_MIN_TO_MAX, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID} within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final UUID uuid, final EpochTimeRange range)
    {
        clearHistoricalData(uuid, range, Long.MAX_VALUE);
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID} within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData(final UUID uuid, final EpochTimeRange range, final long count)
    {
        m_deviceImpl.clearHistoricalData(uuid, range, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly()
    {
        clearHistoricalData_memoryOnly(EpochTimeRange.FROM_MIN_TO_MAX, Long.MAX_VALUE);
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final long count)
    {
        clearHistoricalData_memoryOnly(EpochTimeRange.FROM_MIN_TO_MAX, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final EpochTimeRange range)
    {
        clearHistoricalData_memoryOnly(range, Long.MAX_VALUE);
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final EpochTimeRange range, final long count)
    {
        m_deviceImpl.clearHistoricalData_memoryOnly(range, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID}.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final UUID uuid)
    {
        clearHistoricalData_memoryOnly(uuid, EpochTimeRange.FROM_MIN_TO_MAX, Long.MAX_VALUE);
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID}.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final UUID uuid, final long count)
    {
        clearHistoricalData_memoryOnly(uuid, EpochTimeRange.FROM_MIN_TO_MAX, count);
    }

    /**
     * Clears all {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID} within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final UUID characteristicUuid, final EpochTimeRange range)
    {
        clearHistoricalData_memoryOnly(characteristicUuid, range, Long.MAX_VALUE);
    }

    /**
     * Clears the first <code>count</code> number of {@link HistoricalData} tracked by this device for a particular
     * characteristic {@link java.util.UUID} within the given range.
     *
     * @see BleNodeConfig.HistoricalDataLogFilter
     * @see BleNodeConfig.DefaultHistoricalDataLogFilter
     */
    @Advanced
    public final void clearHistoricalData_memoryOnly(final UUID characteristicUuid, final EpochTimeRange range, final long count)
    {
        m_deviceImpl.clearHistoricalData_memoryOnly(characteristicUuid, range, count);
    }

    /**
     * Overload of {@link #read(UUID)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #readMany(BleRead[])} instead.
     */
    @Deprecated
    public final void read(final UUID[] charUuids)
    {
        read(charUuids, null);
    }

    /**
     * Overload of {@link #read(UUID, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #readMany(BleRead[])} instead.
     */
    @Deprecated
    public final void read(final UUID[] charUuids, final ReadWriteListener listener)
    {
        for (int i = 0; i < charUuids.length; i++)
        {
            read(charUuids[i], listener);
        }
    }

    /**
     * Overload of {@link #read(BleRead)}.
     */
    public final void readMany(final BleRead[] bleReads)
    {
        for (int i = 0; i < bleReads.length; i++)
        {
            read(bleReads[i]);
        }
    }

    /**
     * Overload of {@link #read(BleRead)}.
     */
    public final void readMany(final Iterable<BleRead> bleReads)
    {
        final Iterator<BleRead> it = bleReads.iterator();

        while (it.hasNext())
        {
            final BleRead read = it.next();

            read(read);
        }
    }

    /**
     * Overload of {@link #read(UUID)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #readMany(Iterable)} instead.
     */
    @Deprecated
    public final void read(final Iterable<UUID> charUuids)
    {
        read(charUuids, null);
    }

    /**
     * Overload of {@link #read(UUID, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #readMany(Iterable)} instead.
     */
    @Deprecated
    public final void read(final Iterable<UUID> charUuids, final ReadWriteListener listener)
    {
        final Iterator<UUID> iterator = charUuids.iterator();

        while (iterator.hasNext())
        {
            final UUID charUuid = iterator.next();

            read(charUuid, listener);
        }
    }

    /**
     * Same as {@link #read(java.util.UUID, ReadWriteListener)} but you can use this
     * if you don't immediately care about the result. The callback will still be posted to {@link ReadWriteListener}
     * instances (if any) provided to {@link BleDevice#setListener_ReadWrite(ReadWriteListener)} and
     * {@link BleManager#setListener_Read_Write(ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID characteristicUuid)
    {
        final UUID serviceUuid = null;

        return read(serviceUuid, characteristicUuid, null, null);
    }

    /**
     * Same as {@link #read(java.util.UUID, DescriptorFilter, ReadWriteListener)} but you can use this
     * if you don't immediately care about the result. The callback will still be posted to {@link ReadWriteListener}
     * instances (if any) provided to {@link BleDevice#setListener_ReadWrite(ReadWriteListener)} and
     * {@link BleManager#setListener_Read_Write(ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID characteristicUuid, final DescriptorFilter descriptorFilter)
    {
        final UUID serviceUuid = null;

        return read(serviceUuid, characteristicUuid, descriptorFilter, null);
    }

    /**
     * Reads a characteristic from the device.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID characteristicUuid, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return read(serviceUuid, characteristicUuid, null, listener);
    }

    /**
     * Reads a characteristic from the device. The provided {@link DescriptorFilter} will grab the correct {@link BluetoothGattCharacteristic} in the case there are
     * more than one with the same {@link UUID} in the same {@link BluetoothGattService}.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return read(serviceUuid, characteristicUuid, descriptorFilter, listener);
    }

    /**
     * Overload of {@link #read(UUID)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID serviceUuid, final UUID characteristicUuid)
    {
        return read(serviceUuid, characteristicUuid, null, null);
    }

    /**
     * Overload of {@link #read(UUID, DescriptorFilter)} for when you have characteristics with identical uuids under the same service.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID serviceUuid, final UUID characteristicUuid, DescriptorFilter descriptorFilter)
    {
        return read(serviceUuid, characteristicUuid, descriptorFilter, null);
    }

    /**
     * Overload of {@link #read(UUID, ReadWriteListener)} for when you have characteristics with identical uuids under different services.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID serviceUuid, final UUID characteristicUuid, final ReadWriteListener listener)
    {
        return read(serviceUuid, characteristicUuid, null, listener);
    }

    /**
     * Overload of {@link #read(UUID, DescriptorFilter, ReadWriteListener)} for when you have characteristics with identical uuids under the same service.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #read(BleRead)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent read(final UUID serviceUuid, final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        BleRead read = new BleRead(serviceUuid, characteristicUuid).setDescriptorFilter(descriptorFilter).setReadWriteListener(listener);
        return read(read);
    }

    /**
     * Reads a characteristic from the device. The provided {@link DescriptorFilter} (if set in the {@link BleRead} will grab the correct {@link BluetoothGattCharacteristic} in the case there are
     * more than one with the same {@link UUID} in the same {@link BluetoothGattService}.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final ReadWriteListener.ReadWriteEvent read(final BleRead read)
    {
        return m_deviceImpl.read(read);
    }

    /**
     * Read the battery level of this device. This just calls {@link #read(UUID, UUID, ReadWriteListener)} using {@link Uuids#BATTERY_SERVICE_UUID},
     * and {@link Uuids#BATTERY_LEVEL}. If your battery service/characteristic uses a custom UUID, then use {@link #read(UUID, UUID, ReadWriteListener)} with
     * your custom UUIDs.
     */
    public final ReadWriteEvent readBatteryLevel(ReadWriteListener listener)
    {
        final BleRead read = new BleRead(Uuids.BATTERY_SERVICE_UUID, Uuids.BATTERY_LEVEL).setReadWriteListener(listener);
        return read(read);
    }

    /**
     * Returns <code>true</code> if notifications are enabled for the given uuid.
     * NOTE: {@link #isNotifyEnabling(UUID)} may return true even if this method returns false.
     *
     * @see #isNotifyEnabling(UUID)
     */
    public final boolean isNotifyEnabled(final UUID uuid)
    {
        return m_deviceImpl.isNotifyEnabled(uuid);
    }

    /**
     * Returns <code>true</code> if SweetBlue is in the process of enabling notifications for the given uuid.
     *
     * @see #isNotifyEnabled(UUID)
     */
    public final boolean isNotifyEnabling(final UUID uuid)
    {
        return m_deviceImpl.isNotifyEnabling(uuid);
    }

    /**
     * Overload for {@link #enableNotify(UUID)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void enableNotify(final UUID[] charUuids)
    {
        this.enableNotify(charUuids, Interval.INFINITE, null);
    }

    /**
     * Overload for {@link #enableNotify(UUID, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void enableNotify(final UUID[] charUuids, ReadWriteListener listener)
    {
        this.enableNotify(charUuids, Interval.INFINITE, listener);
    }

    /**
     * Overload for {@link #enableNotify(UUID, Interval)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void enableNotify(final UUID[] charUuids, final Interval forceReadTimeout)
    {
        this.enableNotify(charUuids, forceReadTimeout, null);
    }

    /**
     * Overload for {@link #enableNotify(UUID, Interval, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void enableNotify(final UUID[] charUuids, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        for (int i = 0; i < charUuids.length; i++)
        {
            final UUID ith = charUuids[i];

            enableNotify(ith, forceReadTimeout, listener);
        }
    }

    /**
     * Overload for {@link #enableNotify(BleNotify)}.
     */
    public final void enableNotifies(BleNotify[] notifies)
    {
        if (notifies == null)
        {
            m_deviceImpl.logger().e("Passed in a null array!");
            return;
        }
        for (int i = 0; i < notifies.length; i++)
        {
            final BleNotify notify = notifies[i];

            if (notify == null)
            {
                m_deviceImpl.logger().e(String.format("Entry %d of the notify array was null!", i));
                continue;
            }

            enableNotify(notify);
        }
    }

    /**
     * Overload for {@link #enableNotify(BleNotify)}.
     */
    public final void enableNotifies(final Iterable<BleNotify> notifies)
    {
        if (notifies == null)
        {
            m_deviceImpl.logger().e("Passed in a null iterable!");
            return;
        }
        final Iterator<BleNotify> iterator = notifies.iterator();

        while (iterator.hasNext())
        {
            final BleNotify notify = iterator.next();
            if (notify == null)
            {
                m_deviceImpl.logger().e("Got a null notify item in the Iterable!");
                continue;
            }
            enableNotify(notify);
        }
    }

    /**
     * Overload for {@link #enableNotify(UUID)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void enableNotify(final Iterable<UUID> charUuids)
    {
        this.enableNotify(charUuids, Interval.INFINITE, null);
    }

    /**
     * Overload for {@link #enableNotify(UUID, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void enableNotify(final Iterable<UUID> charUuids, ReadWriteListener listener)
    {
        this.enableNotify(charUuids, Interval.INFINITE, listener);
    }

    /**
     * Overload for {@link #enableNotify(UUID, Interval)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void enableNotify(final Iterable<UUID> charUuids, final Interval forceReadTimeout)
    {
        this.enableNotify(charUuids, forceReadTimeout, null);
    }

    /**
     * Overload for {@link #enableNotify(UUID, Interval, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void enableNotify(final Iterable<UUID> charUuids, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        final Iterator<UUID> iterator = charUuids.iterator();

        while (iterator.hasNext())
        {
            final UUID ith = iterator.next();

            enableNotify(ith, forceReadTimeout, listener);
        }
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID characteristicUuid)
    {
        return enableNotify(null, characteristicUuid, Interval.INFINITE, null, null);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID characteristicUuid, ReadWriteListener listener)
    {
        return enableNotify(null, characteristicUuid, Interval.INFINITE, null, listener);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID characteristicUuid, final Interval forceReadTimeout)
    {
        return enableNotify(null, characteristicUuid, forceReadTimeout, null, null);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID characteristicUuid, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        return enableNotify(null, characteristicUuid, forceReadTimeout, null, listener);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID serviceUuid, final UUID characteristicUuid)
    {
        return enableNotify(serviceUuid, characteristicUuid, Interval.INFINITE, null, null);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID serviceUuid, final UUID characteristicUuid, ReadWriteListener listener)
    {
        return enableNotify(serviceUuid, characteristicUuid, Interval.INFINITE, null, listener);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID serviceUuid, final UUID characteristicUuid, final Interval forceReadTimeout)
    {
        return enableNotify(serviceUuid, characteristicUuid, forceReadTimeout, null, null);
    }

    /**
     * Overload of {@link #enableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #enableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent enableNotify(final UUID serviceUuid, final UUID characteristicUuid, final Interval forceReadTimeout, final DescriptorFilter descriptorFilter, final ReadWriteListener listener)
    {
        final BleNotify notify = new BleNotify(serviceUuid, characteristicUuid).setForceReadTimeout(forceReadTimeout).setReadWriteListener(listener).setDescriptorFilter(descriptorFilter);
        return enableNotify(notify);
    }

    /**
     * Enables notification on the given characteristic. The listener will be called both for the notifications themselves and for the actual
     * registration for the notification. <code>switch</code> on {@link NotificationListener.Type#ENABLING_NOTIFICATION}
     * and {@link NotificationListener.Type#NOTIFICATION} (or {@link NotificationListener.Type#INDICATION}) in your listener to distinguish between these.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final ReadWriteListener.ReadWriteEvent enableNotify(BleNotify notify)
    {
        return m_deviceImpl.enableNotify(notify);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID characteristicUuid, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return disableNotify(serviceUuid, characteristicUuid, null, listener);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID characteristicUuid, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        final UUID serviceUuid = null;

        return disableNotify(serviceUuid, characteristicUuid, forceReadTimeout, listener);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID characteristicUuid)
    {
        final UUID serviceUuid = null;

        return disableNotify(serviceUuid, characteristicUuid, (DescriptorFilter) null, null);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID characteristicUuid, final Interval forceReadTimeout)
    {
        final UUID serviceUuid = null;

        return disableNotify(serviceUuid, characteristicUuid, forceReadTimeout, null);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID serviceUuid, final UUID characteristicUuid, final ReadWriteListener listener)
    {
        return disableNotify(serviceUuid, characteristicUuid, null, listener);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID serviceUuid, final UUID characteristicUuid, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        BleNotify notify = new BleNotify(serviceUuid, characteristicUuid).setForceReadTimeout(forceReadTimeout).setReadWriteListener(listener);
        return disableNotify(notify);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID serviceUuid, final UUID characteristicUuid)
    {
        return disableNotify(serviceUuid, characteristicUuid, (DescriptorFilter) null, null);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID serviceUuid, final UUID characteristicUuid, final Interval forceReadTimeout)
    {
        return disableNotify(serviceUuid, characteristicUuid, null, forceReadTimeout);
    }

    /**
     * Overload of {@link #disableNotify(BleNotify)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotify(BleNotify)} instead.
     */
    @Deprecated
    public final ReadWriteListener.ReadWriteEvent disableNotify(final UUID serviceUuid, final UUID characteristicUuid, final DescriptorFilter descriptorFilter, final Interval forceReadTimeout)
    {
        final BleNotify notify = new BleNotify(serviceUuid, characteristicUuid).setDescriptorFilter(descriptorFilter).setForceReadTimeout(forceReadTimeout);
        return disableNotify(notify);
    }

    /**
     * Disables all notifications enabled by {@link #enableNotify(BleNotify)} or
     * any of it's overloads. The listener provided should be the same one that you passed to {@link #enableNotify(BleNotify)}. Listen for
     * {@link Type#DISABLING_NOTIFICATION} in your listener to know when the remote device actually confirmed.
     *
     * @return see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, DeviceConnectListener)}.
     */
    public final ReadWriteListener.ReadWriteEvent disableNotify(BleNotify notify)
    {
        return m_deviceImpl.disableNotify(notify);
    }

    /**
     * Overload for {@link #disableNotify(BleNotify)}.
     */
    public final void disableNotifies(final BleNotify[] notifies)
    {
        if (notifies == null)
        {
            m_deviceImpl.logger().e("Passed in a null array!");
            return;
        }
        for (int i = 0; i < notifies.length; i++)
        {
            final BleNotify notify = notifies[i];

            if (notify == null)
            {
                m_deviceImpl.logger().e("Got a null notify item in the array!");
                continue;
            }

            disableNotify(notify);
        }
    }

    /**
     * Overload for {@link #disableNotify(BleNotify)}.
     */
    public final void disableNotifies(final Iterable<BleNotify> notifies)
    {
        if (notifies == null)
        {
            m_deviceImpl.logger().e("Passed in a null iterable!");
            return;
        }
        final Iterator<BleNotify> iterator = notifies.iterator();

        while (iterator.hasNext())
        {
            final BleNotify notify = iterator.next();

            if (notify == null)
            {
                m_deviceImpl.logger().e("Got a null notify item in the Iterable!");
                continue;
            }

            disableNotify(notify);
        }
    }

    /**
     * Overload for {@link #disableNotify(UUID, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void disableNotify(final UUID[] uuids, final ReadWriteListener listener)
    {
        disableNotify(uuids, null, listener);
    }

    /**
     * Overload for {@link #disableNotify(UUID)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void disableNotify(final UUID[] uuids)
    {
        disableNotify(uuids, null, null);
    }

    /**
     * Overload for {@link #disableNotify(UUID, Interval)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void disableNotify(final UUID[] uuids, final Interval forceReadTimeout)
    {
        disableNotify(uuids, forceReadTimeout, null);
    }

    /**
     * Overload for {@link #disableNotify(UUID, Interval, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(BleNotify[])} instead.
     */
    @Deprecated
    public final void disableNotify(final UUID[] uuids, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        for (int i = 0; i < uuids.length; i++)
        {
            final UUID ith = uuids[i];

            disableNotify(ith, forceReadTimeout, listener);
        }
    }

    /**
     * Overload for {@link #disableNotify(UUID)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void disableNotify(final Iterable<UUID> charUuids)
    {
        disableNotify(charUuids, Interval.INFINITE, null);
    }

    /**
     * Overload for {@link #disableNotify(UUID, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void disableNotify(final Iterable<UUID> charUuids, ReadWriteListener listener)
    {
        disableNotify(charUuids, Interval.INFINITE, listener);
    }

    /**
     * Overload for {@link #disableNotify(UUID, Interval)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void disableNotify(final Iterable<UUID> charUuids, final Interval forceReadTimeout)
    {
        disableNotify(charUuids, forceReadTimeout, null);
    }

    /**
     * Overload for {@link #disableNotify(UUID, Interval, ReadWriteListener)}.
     *
     * @deprecated - This will be removed in v3.1. Please use {@link #disableNotifies(Iterable)} instead.
     */
    @Deprecated
    public final void disableNotify(final Iterable<UUID> charUuids, final Interval forceReadTimeout, final ReadWriteListener listener)
    {
        final Iterator<UUID> iterator = charUuids.iterator();

        while (iterator.hasNext())
        {
            final UUID ith = iterator.next();

            disableNotify(ith, forceReadTimeout, listener);
        }
    }

    /**
     * Kicks off an "over the air" long-term transaction if it's not already
     * taking place and the device is {@link BleDeviceState#INITIALIZED}. This
     * will put the device into the {@link BleDeviceState#PERFORMING_OTA} state
     * if <code>true</code> is returned. You can use this to do firmware
     * updates, file transfers, etc.
     * <br><br>
     * TIP: Use the {@link TimeEstimator} class to let your users know roughly
     * how much time it will take for the ota to complete.
     * <br><br>
     * TIP: For shorter-running transactions consider using {@link #performTransaction(BleTransaction)}.
     *
     * @return <code>true</code> if OTA has started, otherwise <code>false</code> if device is either already
     * {@link BleDeviceState#PERFORMING_OTA} or is not {@link BleDeviceState#INITIALIZED}.
     * @see BleManagerConfig#includeOtaReadWriteTimesInAverage
     * @see BleManagerConfig#autoScanDuringOta
     * @see #performTransaction(BleTransaction)
     */
    public final boolean performOta(final BleTransaction.Ota txn)
    {
        return m_deviceImpl.performOta(txn);
    }

    /**
     * Allows you to perform an arbitrary transaction that is not associated with any {@link BleDeviceState} like
     * {@link BleDeviceState#PERFORMING_OTA}, {@link BleDeviceState#AUTHENTICATING} or {@link BleDeviceState#INITIALIZING}.
     * Generally this transaction should be short, several reads and writes. For longer-term transaction consider using
     * {@link #performOta(BleTransaction.Ota)}.
     * <br><br>
     * The device must be {@link BleDeviceState#INITIALIZED}.
     * <br><br>
     * TIP: For long-term transactions consider using {@link #performOta(BleTransaction.Ota)}.
     *
     * @return <code>true</code> if the transaction successfully started, <code>false</code> otherwise if device is not {@link BleDeviceState#INITIALIZED}.
     */
    public final boolean performTransaction(final BleTransaction txn)
    {
        return m_deviceImpl.performTransaction(txn);
    }

    /**
     * Returns the effective MTU size for a write. BLE has an overhead when reading and writing, so that eats out of the MTU size.
     * The write overhead is defined via {@link BleManagerConfig#GATT_WRITE_MTU_OVERHEAD}. The method simply returns the MTU size minus
     * the overhead. This is just used internally, but is exposed in case it's needed for some other use app-side.
     */
    public final int getEffectiveWriteMtuSize()
    {
        return m_deviceImpl.getEffectiveWriteMtuSize();
    }

    /**
     * Returns the device's name and current state for logging and debugging purposes.
     */
    @Override public final String toString()
    {
        return m_deviceImpl.toString();
    }

    /**
     * Returns <code>true</code> if <code>this</code> is referentially equal to {@link BleDevice#NULL}.
     */
    @Override public final boolean isNull()
    {
        return m_deviceImpl.isNull();
    }


    IBleDevice getIBleDevice()
    {
        return m_deviceImpl;
    }

}
