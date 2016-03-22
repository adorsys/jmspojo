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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.adorsys.jmspojo.JMSJacksonMapper;
import de.adorsys.jmspojo.JMSMessageHeaders;
import de.adorsys.jmspojo.JMSMessageListenerServiceAdapter;
import de.adorsys.jmspojo.JMSMessageReceiver;

public class JMSMessageListenerServiceAdapterTest {
	
	
	private ActiveMQConnectionFactory cf;
	private BrokerService broker;
	private QueueConnection qc;
	private static final JMSJacksonMapper OBJECT_MAPPER = new JMSJacksonMapper(new ObjectMapper());
	private QueueSession queueSession;
	private TemporaryQueue reqlayQ;

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
		reqlayQ = queueSession.createTemporaryQueue();
		qc.start();
	}
	
	@After
	public void cleanup() throws Exception {
		qc.close();
		broker.stop();
		broker.waitUntilStopped();
	}

	@Test
	public void testCreateAdapterWithReply() throws JMSException {
		TextMessage textMessage = queueSession.createTextMessage(OBJECT_MAPPER.serialize(new PingMessage("ping")));
		textMessage.setJMSReplyTo(reqlayQ);
		
		
		SampleMessageServiceWithReply service = new SampleMessageServiceWithReply();
		JMSMessageListenerServiceAdapter<SampleMessageServiceWithReply> adapter = JMSMessageListenerServiceAdapter.createAdapter(service, cf, OBJECT_MAPPER);
		adapter.onMessage(textMessage);
		
		TextMessage message = (TextMessage) queueSession.createReceiver(reqlayQ).receive(1000);
		assertNotNull(message);
		assertNull(message.getStringProperty("ERROR"));
		assertEquals("{\"ping\":\"ping\"}", message.getText());
	}
	
	@Test
	public void testCreateAdapterWithException() throws JMSException {
		TextMessage textMessage = queueSession.createTextMessage(OBJECT_MAPPER.serialize(new PingMessage("ping")));
		textMessage.setJMSReplyTo(reqlayQ);
		
		
		SampleMessageServiceWithException service = new SampleMessageServiceWithException();
		JMSMessageListenerServiceAdapter<SampleMessageServiceWithException> adapter = JMSMessageListenerServiceAdapter.createAdapter(service, cf, OBJECT_MAPPER);
		adapter.onMessage(textMessage);
		
		TextMessage message = (TextMessage) queueSession.createReceiver(reqlayQ).receive(1000);
		assertNotNull(message);
		assertEquals("java.lang.RuntimeException: expected problem", message.getStringProperty("ERROR"));
	}
	
	@Test
	public void testCreateAdapterVoid() throws JMSException {
		TextMessage textMessage = queueSession.createTextMessage(OBJECT_MAPPER.serialize(new PingMessage("ping")));
		textMessage.setJMSReplyTo(reqlayQ);
		
		
		SampleMessageServiceVoid service = new SampleMessageServiceVoid();
		JMSMessageListenerServiceAdapter<SampleMessageServiceVoid> adapter = JMSMessageListenerServiceAdapter.createAdapter(service, cf, OBJECT_MAPPER);
		adapter.onMessage(textMessage);
		
		TextMessage message = (TextMessage) queueSession.createReceiver(reqlayQ).receive(1000);
		assertNotNull(message);
		assertNull(message.getStringProperty("ERROR"));
		assertNull(message.getText());
	}
	
	@Test
	public void testCreateAdapterWithHeaders() throws JMSException {
		TextMessage textMessage = queueSession.createTextMessage(OBJECT_MAPPER.serialize(new PingMessage("ping")));
		textMessage.setJMSReplyTo(reqlayQ);
		textMessage.setBooleanProperty("testHeader", true);
		
		
		SampleMessageServiceWithHeaders service = new SampleMessageServiceWithHeaders();
		JMSMessageListenerServiceAdapter<SampleMessageServiceWithHeaders> adapter = JMSMessageListenerServiceAdapter.createAdapter(service, cf, OBJECT_MAPPER);
		adapter.onMessage(textMessage);
		
		TextMessage message = (TextMessage) queueSession.createReceiver(reqlayQ).receive(1000);
		assertNotNull(message);
		assertNull(message.getStringProperty("ERROR"));
		assertNull(message.getText());
	}
	
	
	public static class SampleMessageServiceWithReply {
		
		@JMSMessageReceiver
		public PingMessage ping(PingMessage message) {
			return message;
		}

	}
	
	public static class SampleMessageServiceVoid {
		
		@JMSMessageReceiver
		public void ping(PingMessage message) {
		}

	}
	
	public static class SampleMessageServiceWithException {
		
		@JMSMessageReceiver
		public void observe(PingMessage message) {
			throw new RuntimeException("expected problem");
		}

	}
	
	public static class SampleMessageServiceWithHeaders {
		
		@JMSMessageReceiver
		public void ping(@JMSMessageHeaders HashMap<String, Object> headers, PingMessage message) {
			assertTrue((Boolean)headers.get("testHeader"));
		}

	}

}
