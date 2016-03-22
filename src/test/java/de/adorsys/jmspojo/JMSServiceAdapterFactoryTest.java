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

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.adorsys.jmspojo.JMSFuture;
import de.adorsys.jmspojo.JMSJacksonMapper;
import de.adorsys.jmspojo.JMSJavaFutureAdapter;
import de.adorsys.jmspojo.JMSServiceAdapterFactory;

public class JMSServiceAdapterFactoryTest {

	private static final JMSJacksonMapper OBJECT_MAPPER = new JMSJacksonMapper(new ObjectMapper());
	private static final int JMS_TIMEOUT = 5000;
	private ActiveMQConnectionFactory cf;
	private QueueConnection qc;
	private Queue defaultQueue;
	private BrokerService broker;
	private JMSSampleService service;
	private Queue dedicatedQueue;

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
		QueueSession createQueueSession = qc.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
		defaultQueue = createQueueSession.createQueue("TestQueue");
		createQueueSession.createReceiver(defaultQueue).setMessageListener(new MessageListener() {
			
			@Override
			public void onMessage(Message message) {
				try {
					boolean sendTimeout = message.getBooleanProperty("timeout");
					if (!sendTimeout){
						JMSJavaFutureAdapter<PingMessage> jmsSender = new JMSJavaFutureAdapter<PingMessage>(OBJECT_MAPPER, cf, null, JMS_TIMEOUT);
						jmsSender.send(message.getJMSReplyTo(), null, ((TextMessage)message).getText());
					}
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}
		});
		
		dedicatedQueue = createQueueSession.createQueue("DedicatedQueue");
		createQueueSession.createReceiver(dedicatedQueue).setMessageListener(new MessageListener() {
			
			@Override
			public void onMessage(Message message) {
				try {
						JMSJavaFutureAdapter<PingMessage> jmsSender = new JMSJavaFutureAdapter<PingMessage>(OBJECT_MAPPER, cf, null, JMS_TIMEOUT);
						PingMessage data = new PingMessage();
						data.setPing("dedicted response");
						jmsSender.send(message.getJMSReplyTo(), null, data);
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}
		});
		qc.start();
		
		JMSServiceAdapterFactory jmsServiceStubFactory = new JMSServiceAdapterFactory(OBJECT_MAPPER, cf, defaultQueue, JMS_TIMEOUT);
		service = jmsServiceStubFactory.generateJMSServiceProxy(JMSSampleService.class);
		
	}
	
	@After
	public void cleanup() throws Exception {
		qc.close();
		broker.stop();
		broker.waitUntilStopped();
	}
	
	@Test
	public void testFireAndForget() {
		PingMessage message = new PingMessage();
		message.setPing("signal1");
		service.fireAndForget(message);
	}
	
	@Test
	public void testFireAndForgetDestination() {
		PingMessage message = new PingMessage();
		message.setPing("signal1");
		service.fireAndForget(message, dedicatedQueue);
	}
	
	@Test
	public void testPing() throws InterruptedException, ExecutionException {
		PingMessage message = new PingMessage();
		message.setPing("signal1");
		
		try (JMSFuture<PingMessage> future = service.ping(message)) {
			PingMessage sampleMessage = future.get();
			Assert.assertNotNull(sampleMessage);
			Assert.assertEquals(new PingMessage("signal1"), sampleMessage);
		} 
	}
	
	@Test
	public void testFireAndWait() throws InterruptedException, ExecutionException {
		PingMessage message = new PingMessage();
		message.setPing("signal1");
		
		try (JMSFuture<Void> future = service.fireAndWait(message)) {
			future.get();
		} 
	}
	
	@Test
	public void testPingMessageHeadersTimeout() throws InterruptedException, ExecutionException {
		PingMessage message = new PingMessage();
		message.setPing("signal1");
		
		HashMap<String, Object> headers = new HashMap<>();
		headers.put("timeout", true);
		
		try (JMSFuture<PingMessage> future = service.ping(headers, message)) {
			try {
				future.get(100, TimeUnit.MILLISECONDS);
				Assert.fail("timeout expected");
			} catch (TimeoutException e) {
			}
		} 
	}

	
	@Test
	public void testPingDestination() throws InterruptedException, ExecutionException {
		PingMessage message = new PingMessage();
		message.setPing("signal1");
		
		try (JMSFuture<PingMessage> future = service.ping(message, dedicatedQueue)) {
			PingMessage sampleMessage = future.get();
			Assert.assertNotNull(sampleMessage);
			Assert.assertEquals(new PingMessage("dedicted response"), sampleMessage);
		} 
	}

}
