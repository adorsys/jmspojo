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

import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class JMSAbstractMessageListener<T> implements MessageListener {
	
	private static final JMSJacksonMapper OBJECT_MAPPER = new JMSJacksonMapper(new ObjectMapper());
	private JMSMessageListenerServiceAdapter<T> adapter;

	public JMSAbstractMessageListener() {
		adapter = JMSMessageListenerServiceAdapter.createAdapter(getService(), getConnectionFactory(), getObjectMapper());
	}

	protected abstract T getService();

	protected abstract ConnectionFactory getConnectionFactory();

	protected JMSObjectMapper getObjectMapper() {
		return OBJECT_MAPPER;
	}

	@Override
	public void onMessage(Message message) {
		adapter.onMessage(message);
	}

}
