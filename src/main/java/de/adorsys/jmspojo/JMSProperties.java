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
/**
 * 
 */
package de.adorsys.jmspojo;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Message;

/**
 * @author sso
 *
 */
public class JMSProperties {
	
	private final Message message;

	public JMSProperties(Message message) {
		super();
		this.message = message;
	}
	
	public Map<String, Object> toMap() {
		try {
			Map<String, Object> properties = new HashMap<>();
			
			@SuppressWarnings("unchecked")
			Enumeration<String> propertyNames = (Enumeration<String>)message.getPropertyNames();
			while (propertyNames.hasMoreElements()) {
				String key = (String) propertyNames.nextElement();
				Object value = message.getObjectProperty(key);
				properties.put(key, value);
			}
			return properties;
		} catch (JMSException e) {
			throw new JMSServiceException(e);
		}
	}
	
	public void setProperties(Map<String, Object> properties) {
		try {
			Set<Entry<String, Object>> entries = properties.entrySet();
			for (Entry<String, Object> entry : entries) {
				message.setObjectProperty(entry.getKey(), entry.getValue());
			}
		} catch (JMSException e) {
			throw new JMSServiceException(e);
		}
	}


	public Message getMessage() {
		return message;
	}

}
