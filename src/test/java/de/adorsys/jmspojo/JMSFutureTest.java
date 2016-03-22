/**
 * Copyright (C) 2016 Sandro Sonntag (sso@adorsys.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.adorsys.jmspojo;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import de.adorsys.jmspojo.JMSFuture;

public class JMSFutureTest {

	private final class JMSFutureExtension extends JMSFuture<Void> {
		
		private final long waitTime;
		private final boolean exception;
		
		public JMSFutureExtension(long waitTime, boolean exception) {
			super();
			this.waitTime = waitTime;
			this.exception = exception;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public void close() {
			
		}

		@Override
		public Void get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
			try {
				if (this.waitTime > timeout) {
					Thread.sleep(timeout);
					throw new TimeoutException();
				} else {
					Thread.sleep(waitTime);
				}
				if (exception) {
					throw new ExecutionException(null);
				}
			} catch (InterruptedException e) {
			}
			return null;
		}

		@Override
		public Void get() throws ExecutionException {
			return null;
		}
	}

	@Test
	public void testWaitForAll() throws ExecutionException, TimeoutException {
		JMSFuture.waitForAll(100l, new JMSFutureExtension(50l, false), new JMSFutureExtension(39l, false));
	}
	
	@Test
	public void testWaitForAllTimeout() throws ExecutionException {
		try {
			JMSFuture.waitForAll(100l, new JMSFutureExtension(90l, false), new JMSFutureExtension(10000l, false));
			fail("timeout expected");
		} catch (TimeoutException e) {
		}
	}
	
	@Test
	public void testWaitForAllWithException() throws TimeoutException {
		try {
			JMSFuture.waitForAll(100l, new JMSFutureExtension(50l, false), new JMSFutureExtension(39l, true));
			fail("ExecutionException expected");
		} catch (ExecutionException e) {
		}
	}

}
