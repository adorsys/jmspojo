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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

public class JMSResourceHousekeeper {
	
	private static final ScheduledExecutorService CLEANUP_SCHEDULER = Executors.newScheduledThreadPool(1);
	
	public void shutdown() {
		CLEANUP_SCHEDULER.shutdown();
	}
	
	public static void close(long timeoutMs, final AutoCloseable... closeable) {
		CLEANUP_SCHEDULER.schedule(new Runnable() {
			
			@Override
			public void run() {
				for (AutoCloseable autoCloseable : closeable) {
					close(autoCloseable);
				}
			}
		}, timeoutMs, TimeUnit.MILLISECONDS);
	}
	
	public static void closeAll(Connection jmsConnection, Session jmsSession, MessageProducer sender) {
		close(sender);
		close(jmsSession);
		close(jmsConnection);
	}
	
	public static void close(Connection jmsConnection) {
		try {
			if (jmsConnection != null) {
				jmsConnection.stop();
				jmsConnection.close();
			}
		} catch (JMSException e) {
		}
	}


	public static void close(Session jmsSession) {
		try {
			if (jmsSession != null) {
				jmsSession.close();
			}
		} catch (JMSException e) {
		}
	}


	public static void close(MessageProducer sender) {
		try {
			if (sender != null) {
				sender.close();
			}
		} catch (JMSException e) {
		}
	}

	public static void close(MessageConsumer jmsConsumer) {
		try {
			if (jmsConsumer != null) {
				jmsConsumer.close();
			}
		} catch (JMSException e) {
		}
	}

	public static void close(AutoCloseable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (Exception e) {
		}
		
	}

}
