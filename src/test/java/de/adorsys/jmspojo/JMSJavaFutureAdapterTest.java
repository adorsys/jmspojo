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

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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

public class JMSJavaFutureAdapterTest {
	
	private static final long TIMEOUT = 5000l;
	private ActiveMQConnectionFactory cf;
	private Queue testQueue;
	private BrokerService broker;
	private QueueConnection qc;
	private JMSJacksonMapper objectMapper = new JMSJacksonMapper(new ObjectMapper());

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
		testQueue = createQueueSession.createQueue("TestQueue");
		createQueueSession.createReceiver(testQueue).setMessageListener(new MessageListener() {
			
			@Override
			public void onMessage(Message message) {
				try {
					boolean sendTimeout = message.getBooleanProperty("timeout");
					boolean sendError = message.getBooleanProperty("error");
					if (sendError) {
						JMSJavaFutureAdapter<PingMessage> jmsSender = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, null, TIMEOUT);
						HashMap<String, Object> messageProperties = new HashMap<String, Object>();
						messageProperties.put("ERROR", "test error");
						jmsSender.send(message.getJMSReplyTo(), messageProperties, null);
					} else if (!sendTimeout){
						JMSJavaFutureAdapter<PingMessage> jmsSender = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, null, TIMEOUT);
						jmsSender.send(message.getJMSReplyTo(), null, ((TextMessage)message).getText());
					}
					
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}
		});
		qc.start();
		
	}
	
	@After
	public void cleanup() throws Exception {
		qc.close();
		broker.stop();
		broker.waitUntilStopped();
	}
	

	@Test
	public void testSendAndWaitForReplyWithTimeout() throws InterruptedException, ExecutionException, TimeoutException {
		JMSJavaFutureAdapter<PingMessage> jmsServiceMethodInvoker = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, PingMessage.class, 5000);
		
		PingMessage data = new PingMessage();
		data.setPing("sampletext");
		
		Future<PingMessage> future = jmsServiceMethodInvoker.send(testQueue, new HashMap<String, Object>(), data);
		PingMessage message = future.get(1000, TimeUnit.MILLISECONDS);
		Assert.assertNotNull(message);
		Assert.assertEquals(data, message);
	}
	
	@Test
	public void testSendAndWaitForReplyTimeout() throws InterruptedException, ExecutionException {
		JMSJavaFutureAdapter<PingMessage> jmsServiceMethodInvoker = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, PingMessage.class, 5000);
		HashMap<String, Object> messageProperties = new HashMap<String, Object>();
		messageProperties.put("timeout", true);
		JMSFuture<PingMessage> future = jmsServiceMethodInvoker.send(testQueue, messageProperties, new PingMessage());
		try {
			future.get(1000, TimeUnit.MILLISECONDS);
			fail("timeout expected");
		} catch (TimeoutException e) {
			;
		}
	}
	
	@Test
	public void testSendAndWaitForError() throws InterruptedException, TimeoutException {
		JMSJavaFutureAdapter<PingMessage> jmsServiceMethodInvoker = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, PingMessage.class, 5000);
		HashMap<String, Object> messageProperties = new HashMap<String, Object>();
		messageProperties.put("error", true);
		JMSFuture<PingMessage> future = jmsServiceMethodInvoker.send(testQueue, messageProperties, new PingMessage());
		try {
			future.get(1000, TimeUnit.MILLISECONDS);
			fail("error expected");
		} catch (ExecutionException e) {
			assertEquals("test error", e.getMessage());
		}
	}
	
	@Test
	public void testSendAndWaitForReply() throws InterruptedException, ExecutionException, TimeoutException {
		JMSJavaFutureAdapter<PingMessage> jmsServiceMethodInvoker = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, PingMessage.class, 5000);
		
		PingMessage data = new PingMessage();
		data.setPing("sampletext");
		
		JMSFuture<PingMessage> future = jmsServiceMethodInvoker.send(testQueue, new HashMap<String, Object>(), data);
		PingMessage message = future.get();
		Assert.assertNotNull(message);
		Assert.assertEquals(data, message);
	}
	
	@Test
	public void testSendFireAndForget() throws InterruptedException, ExecutionException, TimeoutException {
		JMSJavaFutureAdapter<PingMessage> jmsServiceMethodInvoker = new JMSJavaFutureAdapter<PingMessage>(objectMapper, cf, null, 5000);
		JMSFuture<PingMessage> future = jmsServiceMethodInvoker.send(testQueue, new HashMap<String, Object>(), new PingMessage());
		Assert.assertNull(future);
	}



}
