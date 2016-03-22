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

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

public class JMSCloseable<T> implements AutoCloseable {
	
	private T resource;
	private AutoCloseable ac;
	
	public JMSCloseable(T resource, AutoCloseable ac) {
		this.resource = resource;
		this.ac = ac;
	}

	@Override
	public void close()  {
		try {
			ac.close();
		} catch (Exception e) {
		}
	}
	
	public T get() {
		return resource;
	}
	
	public static <T extends Connection> JMSCloseable<T> wrap(final T connection) {
		return new JMSCloseable<T>(connection, new AutoCloseable() {
			
			@Override
			public void close() throws Exception {
				JMSResourceHousekeeper.close(connection);
			}
		});
	}
	public static <T extends Session> JMSCloseable<T> wrap(final T session) {
		return new JMSCloseable<T>(session, new AutoCloseable() {
			
			@Override
			public void close() throws Exception {
				JMSResourceHousekeeper.close(session);
			}
		});
	}
	public static <T extends MessageProducer> JMSCloseable<T> wrap(final T producer) {
		return new JMSCloseable<T>(producer, new AutoCloseable() {
			
			@Override
			public void close() throws Exception {
				JMSResourceHousekeeper.close(producer);
			}
		});
	}
	public static <T extends MessageConsumer> JMSCloseable<T> wrap(final T consumer) {
		return new JMSCloseable<T>(consumer, new AutoCloseable() {
			
			@Override
			public void close() throws Exception {
				JMSResourceHousekeeper.close(consumer);
			}
		});
	}
}
