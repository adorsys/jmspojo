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

import java.util.Map;

import javax.jms.Destination;

import de.adorsys.jmspojo.JMSFuture;
import de.adorsys.jmspojo.JMSMessageHeaders;

public interface JMSSampleService {
	
	public void fireAndForget(PingMessage message);
	
	public JMSFuture<Void> fireAndWait(PingMessage message);
	
	public void fireAndForget(PingMessage message, Destination destination);
	
	public JMSFuture<PingMessage> ping(@JMSMessageHeaders Map<String, Object> messageHeaders, PingMessage message);
	
	public JMSFuture<PingMessage> ping(PingMessage message);
	
	public JMSFuture<PingMessage> ping(PingMessage message, Destination destination);

}
