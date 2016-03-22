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

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.adorsys.jmspojo.JMSProperties;


public class JMSPropertiesTest {
	
	private ActiveMQConnectionFactory cf;
	private BrokerService broker;
	private QueueConnection qc;
	private QueueSession queueSession;

	@Before
	public void setup() throws Exception {
		broker = new BrokerService();
		broker.setPersistent(false);
		 
		// configure the broker
		broker.addConnector("vm://test");
		broker.setBrokerName("test");
		broker.setUseShutdownHook(false);
		 
		broker.start();
		
		cf = new ActiveMQConnectionFactory("vm://localhost?create=false");
		qc = cf.createQueueConnection();
		queueSession = qc.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
		qc.start();
		
	}
	
	@After
	public void cleanup() throws Exception {
		qc.close();
		broker.stop();
		broker.waitUntilStopped();
	}

	@Test
	public void testToMap() throws JMSException {
		TextMessage message = queueSession.createTextMessage();
		message.setBooleanProperty("boolean", true);
		message.setDoubleProperty("double", 1.1);
		message.setStringProperty("string", "string");
		message.setIntProperty("int", 42);
		JMSProperties jmsProperties = new JMSProperties(message);
		Map<String, Object> map = jmsProperties.toMap();
		assertEquals(true, map.get("boolean"));
		assertEquals(1.1, map.get("double"));
		assertEquals("string", map.get("string"));
		assertEquals(42, map.get("int"));
		assertEquals(null,  map.get("undefined"));
		assertEquals(4,  map.size());
	}

	@Test
	public void testSetProperties() throws JMSException {
		TextMessage message = queueSession.createTextMessage();
		JMSProperties jmsProperties = new JMSProperties(message);

		Map<String, Object> properties = new HashMap<>();
		properties.put("boolean", true);
		properties.put("double", 1.1);
		properties.put("string", "string");
		properties.put("int", 42);
		jmsProperties.setProperties(properties);
		
		assertEquals(true, message.getBooleanProperty("boolean"));
		assertEquals(1.1, message.getDoubleProperty("double"), 0.1);
		assertEquals("string", message.getStringProperty("string"));
		assertEquals(42, message.getIntProperty("int"));
		assertEquals(null,  message.getObjectProperty("undefined"));
	}

}
