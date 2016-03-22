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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

public class JMSMessageListenerServiceAdapter<T> {
	
	private final JMSMessageMethodCall jmMessageMethodCall;
	private final JMSObjectMapper objectMapper;
	private JMSJavaFutureAdapter<Void> jmsFutureCallAdapter;
	
	JMSMessageListenerServiceAdapter(JMSMessageMethodCall jmMessageMethodCall, JMSObjectMapper objectMapper,
			ConnectionFactory connectionFactory) {
		super();
		this.jmMessageMethodCall = jmMessageMethodCall;
		this.objectMapper = objectMapper;
		jmsFutureCallAdapter = new JMSJavaFutureAdapter<>(objectMapper, connectionFactory, Void.class, 0);
	}

	public void onMessage(Message m) {
		String text;
		Destination jmsReplyTo;
		try {
			text = resolveMessageText(m);
			jmsReplyTo = m.getJMSReplyTo();
		} catch (JMSException e) {
			throw new JMSServiceException("problemm accessing the message", e);
		}
		
		Object deserialized = null;
		if (jmMessageMethodCall.getBodyType() != null && text != null) {
			deserialized = objectMapper.deserialize(text, jmMessageMethodCall.getBodyType());
		}
		
		Map<String, Object> messageHeaders = null;
		if (jmMessageMethodCall.isConsumingMessageHeaders()) {
			messageHeaders = new JMSProperties(m).toMap();
		}
		
		try {
			Object returnObject = jmMessageMethodCall.call(deserialized, messageHeaders);
			if (jmsReplyTo == null) {
				return;
			}
			if (jmMessageMethodCall.isReturningVoid()) {
				jmsFutureCallAdapter.send(jmsReplyTo, Collections.<String, Object>emptyMap(), null);
			} else {
				jmsFutureCallAdapter.send(jmsReplyTo, Collections.<String, Object>emptyMap(), returnObject);
			}
		} catch (Throwable e) {
			e.printStackTrace();
			if (jmsReplyTo == null) {
				return;
			}
			HashMap<String, Object> jmsProperties = new HashMap<>();
			jmsProperties.put("ERROR", e.getClass().getName() + ": " + e.getMessage());
			jmsFutureCallAdapter.send(jmsReplyTo, jmsProperties, null);
		}
	}

	private String resolveMessageText(Message m) throws JMSException {
		if (!(m instanceof TextMessage)) {
			throw new JMSServiceException("recived jms message is not of type text " + m);
		}
		TextMessage textMessage = (TextMessage) m;
		String text = textMessage.getText();
		return text;
	}
	
	public static <T> JMSMessageListenerServiceAdapter<T> createAdapter(T service,  ConnectionFactory cf, JMSObjectMapper objectMapper) {
		JMSMessageMethodCall jmsMessageMethodCall = null;
		
		Method[] methods = service.getClass().getMethods();
		for (Method method : methods) {
			if (method.isAnnotationPresent(JMSMessageReceiver.class)) {
				if (jmsMessageMethodCall != null) {
					throw new JMSServiceException("more than one " + JMSMessageReceiver.class.getName() + " annotation found on class " + service.getClass().getName());
				}
				jmsMessageMethodCall = new JMSMessageMethodCall(service, method);
			}
		}
		
		if (jmsMessageMethodCall == null) {
			throw new JMSServiceException("no " + JMSMessageReceiver.class.getName() + " annotation found on class " + service.getClass().getName());
		}
		return new JMSMessageListenerServiceAdapter<T>(jmsMessageMethodCall, objectMapper, cf);
	}
	
	static class JMSMessageMethodCall {
		
		private final Method method;
		private final Object service;
		private int msgHeaderMapIndex = -1;
		private int msgBodyIndex = -1;
		
		public JMSMessageMethodCall(Object service, Method method) {
			super();
			this.service = service;
			this.method = method;
			Class<?>[] parameterTypes = method.getParameterTypes();
			for (int i = 0; i < parameterTypes.length; i++) {
				if (Map.class.isAssignableFrom(parameterTypes[i]) && isMessageHeadersParam(method, i)) {
					msgHeaderMapIndex = i;
				} else {
					msgBodyIndex = i;
				}
			}
		}
		
		public Class<?> getBodyType() {
			if (msgBodyIndex == -1) {
				return null;
			}
			return  method.getParameterTypes()[msgBodyIndex];
		}
		
		public boolean isConsumingMessageHeaders() {
			return msgHeaderMapIndex != -1;
		}
		
		public boolean isReturningVoid() {
			return method.getReturnType() == void.class;
		}

		public Object call(Object body, Map<String, Object> messageHeaders) throws Throwable {
			Object[] methodArguments = new Object[method.getParameterTypes().length];
			
			if (body != null && msgBodyIndex != -1) {
				methodArguments[msgBodyIndex] = body;
			}
			
			if (isConsumingMessageHeaders()) {
				methodArguments[msgHeaderMapIndex] = messageHeaders;
			}
			
			try {
				Object result = method.invoke(service, methodArguments);
				return result;
			} catch (InvocationTargetException e) {
				throw e.getTargetException();
			}
			
		}
		
		private boolean isMessageHeadersParam(Method method, int i) {
			List<Annotation> annotations = Arrays.asList(method.getParameterAnnotations()[i]);
			for (Annotation annotation : annotations) {
				if (annotation.annotationType() == JMSMessageHeaders.class) {
					return true;
				}
			}
			return false;
		}
	}

}
