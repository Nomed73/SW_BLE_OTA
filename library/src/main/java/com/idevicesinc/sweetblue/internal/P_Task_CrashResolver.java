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

package com.idevicesinc.sweetblue.internal;


import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleTask;

final class P_Task_CrashResolver extends PA_Task_RequiresBleOn implements PA_Task.I_StateListener
{
	private final P_BluetoothCrashResolver m_resolver;

	private volatile boolean m_startedRecovery = false;
	private final boolean m_partOfReset;
	
	public P_Task_CrashResolver(IBleManager manager, P_BluetoothCrashResolver resolver, final boolean partOfReset)
	{
		super(manager, null);
		
		m_resolver = resolver;
		m_partOfReset = partOfReset;
	}

	public boolean isForReset()
	{
		return m_partOfReset;
	}
	
	@Override public void execute()
	{
		if( true == m_resolver.isRecoveryInProgress() )
		{
			getManager().ASSERT(false, "CrashResolver recovery already in progress!");

			//--- DRK > Previously was just letting this task continuously spin if it's already running,
			//---		but 99% of the time it means it won't ever stop so the task takes a while to timeout
			//---		and doesn't do anything anyway.
			fail();
		}
		else
		{
			m_resolver.start();

			getManager().getPostManager().runOrPostToUpdateThread(() -> {
                m_resolver.forceFlush();

                m_startedRecovery = true;
            });
		}
	}

	@Override public boolean isCancellableBy(PA_Task task)
	{
		if( task instanceof P_Task_TurnBleOff )
		{
			final P_Task_TurnBleOff task_cast = (P_Task_TurnBleOff) task;

			if( task_cast.isImplicit() || false == this.m_partOfReset )
			{
				return true;
			}
			else
			{
				return false;
			}
		}
		else
		{
			return super.isCancellableBy(task);
		}
	}
	
	@Override public PE_TaskPriority getPriority()
	{
		return PE_TaskPriority.CRITICAL;
	}
	
	@Override protected void update(double timeStep)
	{
		if( getState() == PE_TaskState.EXECUTING && true == m_startedRecovery )
		{
			if( false == m_resolver.isRecoveryInProgress() )
			{
				succeed();
			}
		}
	}
	
	@Override protected BleTask getTaskType()
	{
		return BleTask.RESOLVE_CRASHES;
	}

	@Override public void onStateChange(PA_Task task, PE_TaskState state)
	{
		if( state.isEndingState() )
		{
			m_resolver.stop();
		}
	}
}
