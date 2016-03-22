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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;

public class JMSServiceAdapterFactory {
	
	private final JMSObjectMapper objectMapper;
	private final ConnectionFactory connectionFactory;
	private final long defaultTimeout;
	private final Destination defaultDestination;
	
	public JMSServiceAdapterFactory(JMSObjectMapper objectMapper, ConnectionFactory connectionFactory, Destination defaultDestination, long defaultTimeout) {
		super();
		this.objectMapper = objectMapper;
		this.connectionFactory = connectionFactory;
		this.defaultDestination = defaultDestination;
		this.defaultTimeout = defaultTimeout;
	}

	@SuppressWarnings("unchecked")
	public <T> T generateJMSServiceProxy(Class<T> serviceInterfaceType) {
		if (!serviceInterfaceType.isInterface()) {
			throw new JMSServiceException("class " + serviceInterfaceType.getName() + " is no interface");
		}
		ArrayList<Class<?>> interfaces = new ArrayList<Class<?>>();
		interfaces.add(serviceInterfaceType);
		interfaces.addAll(Arrays.asList(serviceInterfaceType.getInterfaces()));
		
		return (T) Proxy.newProxyInstance(serviceInterfaceType.getClassLoader(), interfaces.toArray(new Class[interfaces.size()]), new JMSInvocationHandler(interfaces));
	}
	
	class JMSInvocationHandler implements InvocationHandler {
		
		final Map<Method, JMSMethodInvokerAdapter<Object>> method2Adapter = new HashMap<>();
		
		public JMSInvocationHandler(Collection<Class<?>> interfaces) {
			for (Class<?> interfaze : interfaces) {
				Method[] methods = interfaze.getMethods();
				for (Method method : methods) {
					method2Adapter.put(method, inspectMethod(method));
				}
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			JMSMethodInvokerAdapter<Object> jmsMethodInvokerAdapter = method2Adapter.get(method);
			if (jmsMethodInvokerAdapter == null) {
				throw new JMSServiceException("JMSMethodInvokerAdapter for method " + method + " is unknown - internal error");
			}
			return jmsMethodInvokerAdapter.invoke(args);
		}

		private <T> JMSMethodInvokerAdapter<T> inspectMethod(Method method) {
			if (!(Future.class.isAssignableFrom(method.getReturnType()) || void.class.equals(method.getReturnType()))) {
				throw new JMSServiceException("return type of method " + 
						method + " is not void or subclass of java.util.concurrent.Future ");
			}
			
			Class<T> retrunType = inspectFutureReturnType(method);
			
			ParameterRead<Destination> destination = new ParameterReadNull<>();
			ParameterRead<Object> messageBody = new ParameterReadNull<>();
			ParameterRead<Map<String, Object>> messageHeaders = new ParameterReadNull<>();
			Class<?>[] parameterTypes = method.getParameterTypes();
			
			for (int i = 0; i < parameterTypes.length; i++) {
				Class<?> parameterType = parameterTypes[i];
				final int paramIndex = i;
				if (parameterType.isAssignableFrom(Destination.class)) {
					
					destination = new ParameterRead<Destination>() {

						@Override
						public Destination read(Object[] args) {
							return (Destination) args[paramIndex];
						}
						
					};
				} else if (Map.class.isAssignableFrom(parameterType) &&  isMessageHeadersParam(method, i)) {
					messageHeaders = new ParameterRead<Map<String, Object>>() {

						@SuppressWarnings("unchecked")
						@Override
						public Map<String, Object> read(Object[] args) {
							return (Map<String, Object>) args[paramIndex];
						}
						
					};
				} else {
					messageBody = new ParameterRead<Object>() {

						@Override
						public Object read(Object[] args) {
							return args[paramIndex];
						}
						
					};
				}
				
			}
			long timeout = defaultTimeout;
			return new JMSMethodInvokerAdapter<T>(destination, messageBody, messageHeaders, retrunType, timeout);
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

		private <T> Class<T> inspectFutureReturnType(Method method) {
			Type returnType = method.getGenericReturnType();
			if (returnType == void.class) {
				return null;
			}
			ParameterizedType genericReturnType = (ParameterizedType) returnType;
			@SuppressWarnings("unchecked")
			Class<T> retrunType = (Class<T>) genericReturnType.getActualTypeArguments()[0];
			return retrunType;
		}
		
	}
	
	class JMSMethodInvokerAdapter<T> {
		private final ParameterRead<Destination> destination;
		private final ParameterRead<Object> messageBody;
		private final ParameterRead<Map<String, Object>> messageHeaders;
		private JMSJavaFutureAdapter<T> jmsFutureCallAdapter;

		public JMSMethodInvokerAdapter(ParameterRead<Destination> destination, ParameterRead<Object> messageBody,
				ParameterRead<Map<String, Object>> messageHeaders, Class<T> responseType, long timeout) {
			super();
			this.destination = destination;
			this.messageBody = messageBody;
			this.messageHeaders = messageHeaders;
			jmsFutureCallAdapter = new JMSJavaFutureAdapter<>(objectMapper, connectionFactory, responseType, timeout);
		}
		
		public JMSFuture<T> invoke(Object[] args) {
			Destination dst = destination.read(args);
			if (dst == null) {
				dst = defaultDestination;
			}
			Map<String, Object> headers = messageHeaders.read(args);
			Object body = messageBody.read(args);
			return jmsFutureCallAdapter.send(dst, headers, body);
		}
		
	}
	
	static interface ParameterRead<T> {
		
		T read(Object[] args);
		
	}
	
	static class ParameterReadNull<T> implements ParameterRead<T> {

		@Override
		public T read(Object[] args) {
			return null;
		}
		
	}

}
