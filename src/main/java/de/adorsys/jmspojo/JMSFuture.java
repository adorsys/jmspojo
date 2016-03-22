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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class JMSFuture<V> implements Future<V>, AutoCloseable {
	
	public abstract void close() ;
	
	public abstract V get(long timeout, TimeUnit unit)
            throws ExecutionException, TimeoutException;
    
	public abstract V get() throws ExecutionException;
	
	public static void waitForAll(long timeout, JMSFuture<?>... futures) throws ExecutionException, TimeoutException {
		long currentTimeMillis = System.currentTimeMillis();
		for (JMSFuture<?> jmsFuture : futures) {
			long leftTime = timeout - (System.currentTimeMillis() - currentTimeMillis);
			if (leftTime <= 0) {
				throw new TimeoutException();
			}
			jmsFuture.get(leftTime, TimeUnit.MILLISECONDS);
		}		
	}
    
}
