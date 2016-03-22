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

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

public class JMSJavaFutureAdapter<T> {
	
	private final Class<T> responseType;
	private final JMSObjectMapper objectMapper;
	private final ConnectionFactory connectionFactory;
	private final long timeout;
	
	public JMSJavaFutureAdapter(JMSObjectMapper objectMapper,
			ConnectionFactory connectionFactory, Class<T> responseType, long timeout) {
		super();
		this.objectMapper = objectMapper;
		this.connectionFactory = connectionFactory;
		this.responseType = responseType;
		this.timeout = timeout;
	}
	
	boolean isReply() {
		return responseType != null;
	}

	JMSFuture<T> send(Destination destination, Map<String, Object> messageProperties, Object data) {
		Connection jmsConnection = null;
		Session jmsSession = null;
		try {
			jmsConnection = connectionFactory.createConnection();
			jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			TextMessage textMessage = jmsSession.createTextMessage();
			if (data != null) {
				String jsonMessage = objectMapper.serialize(data);
				textMessage.setText(jsonMessage);
			}
			setMessageProperties(messageProperties, textMessage);

			if (isReply()) {
				TemporaryQueue replyTo = jmsSession.createTemporaryQueue();
				textMessage.setJMSReplyTo(replyTo);
			}
			
			send(destination, jmsSession, textMessage);
			
			jmsConnection.start();
			
			JMSFuture<T> future = null;
			if (isReply()) {
				final Session s = jmsSession;
				final Connection c = jmsConnection;
				AutoCloseable jmsResources = new AutoCloseable() {
					
					@Override
					public void close() throws Exception {
						JMSResourceHousekeeper.close(s);
						JMSResourceHousekeeper.close(c);
					}
				};
				
				future = createReplyFuture(jmsSession, textMessage, jmsResources);
			}
			return future;
		} catch (JMSException e) {
			throw new JMSServiceException(e);
		} finally {
			if (!isReply()) {
				JMSResourceHousekeeper.close(jmsSession);
				JMSResourceHousekeeper.close(jmsConnection);
			}
		}
	}

	private void setMessageProperties(Map<String, Object> messageProperties, TextMessage textMessage) {
		if (messageProperties != null) {
			JMSProperties jmsProperties = new JMSProperties(textMessage);
			jmsProperties.setProperties(messageProperties);
		}
	}

	private JMSFuture<T> createReplyFuture(Session jmsSession, final Message callerMessage, final AutoCloseable jmsResources) {
		try {
			final JMSCloseable<MessageConsumer> jmsConsumer = JMSCloseable.wrap(jmsSession.createConsumer(callerMessage.getJMSReplyTo()));
			
			JMSFuture<T> future = new JMSFuture<T>() {
				
				boolean closed;
				boolean done;
				private T object;

				@Override
				public boolean cancel(boolean mayInterruptIfRunning) {
					return false;
				}

				@Override
				public boolean isCancelled() {
					return closed;
				}

				@Override
				public boolean isDone() {
					return done;
				}

				@Override
				public T get() throws ExecutionException {
					try {
						return get(timeout, TimeUnit.MILLISECONDS);
					} catch (TimeoutException e) {
						throw new ExecutionException(e);
					} finally {
						close();
					}
				}

				@Override
				public T get(long timeout, TimeUnit unit)
						throws ExecutionException, TimeoutException {
					if (object != null) {
						return object;
					}
					try {
						TextMessage message = (TextMessage) jmsConsumer.get().receive(unit.toMillis(timeout));
						if (message == null) {
							String reason = MessageFormat.format("timeout of reply mesage {0} timeout {1} {2}", callerMessage.getJMSMessageID(), timeout, unit);
							throw new TimeoutException(reason);
						}
						done = true;
						
						checkForError(message);
						
						object = toObject(message);
						return object;
					} catch (JMSException e) {
						throw new JMSServiceException(e);
					} finally {
						close();
					}
				}

				private void checkForError(TextMessage message) throws JMSException, ExecutionException {
					String error = message.getStringProperty("ERROR");
					if (error != null) {
						throw new ExecutionException(error, null);
					}
				}

				private T toObject(Message message) {
					if (message == null) {
						return null;
					}
					assert message instanceof TextMessage : "message is no instance of text message";
					try {
						if (responseType == Void.class) {
							// if void is defined no body will be deserialized, just an reply ACK
							return null;
						}
						String jsonText = ((TextMessage)message).getText();
						if (jsonText == null) {
							return null;
						}
						return objectMapper.deserialize(jsonText, responseType);
					} catch (JMSException e) {
						throw new JMSServiceException(e);
					}
				}

				@Override
				public void close() {
					if (closed) {
						return;
					}
					JMSResourceHousekeeper.close(jmsConsumer);
					JMSResourceHousekeeper.close(jmsResources);
					closed = true;
				}
			};
			JMSResourceHousekeeper.close(timeout, future);
			return future;
		} catch (JMSException e) {
			throw new JMSServiceException(e);
		}
	}

	private void send(Destination destination, Session jmsSession, TextMessage textMessage) throws JMSException {
		try (JMSCloseable<MessageProducer> sender = JMSCloseable.wrap(jmsSession.createProducer(destination))) {
			sender.get().send(destination, textMessage);
		}
	}

}
