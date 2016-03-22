package de.adorsys.jmspojo;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.jms.ConnectionFactory;
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

import de.adorsys.jmspojo.JMSAbstractMessageListener;
import de.adorsys.jmspojo.JMSJacksonMapper;
import de.adorsys.jmspojo.JMSMessageListenerServiceAdapterTest.SampleMessageServiceVoid;

public class JMSAbstractMessageListenerTest {
	
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
	public void testCreateAdapterVoid() throws JMSException {
		TextMessage textMessage = queueSession.createTextMessage(OBJECT_MAPPER.serialize(new PingMessage("ping")));
		textMessage.setJMSReplyTo(reqlayQ);
		
		
		final SampleMessageServiceVoid service = new SampleMessageServiceVoid();
		JMSAbstractMessageListener<SampleMessageServiceVoid> jmsAbstractMessageListener = new JMSAbstractMessageListener<SampleMessageServiceVoid>() {
			
			@Override
			protected SampleMessageServiceVoid getService() {
				return service;
			}
			
			@Override
			protected ConnectionFactory getConnectionFactory() {
				return cf;
			}
		};
		
		jmsAbstractMessageListener.onMessage(textMessage);
		
		TextMessage message = (TextMessage) queueSession.createReceiver(reqlayQ).receive(1000);
		assertNotNull(message);
		assertNull(message.getStringProperty("ERROR"));
		assertNull(message.getText());
	}

}
