# Use JMS like Pojos

## Maven Dependency

    <dependency>
	    <groupId>de.adorsys.jms-service-stub</groupId>
	    <artifactId>jms-service-stub</artifactId>
	    <version>0.1-SNAPSHOT</version>
    </dependency>
    
## Defining a JMS-Service Interface

    public interface JMSSampleService {
	
    	public void fireAndForget(PingMessage message);
    	
    	public JMSFuture<Void> fireAndWait(PingMessage message);
    	
    	public void fireAndForget(PingMessage message, Destination destination);
    	
    	public JMSFuture<PingMessage> ping(@JMSMessageHeaders Map<String, Object> messageHeaders, PingMessage message);
    	
    	public JMSFuture<PingMessage> ping(PingMessage message);
    	
    	public JMSFuture<PingMessage> ping(PingMessage message, Destination destination);

    }

## Initalizing the Service Interface

    JMSServiceAdapterFactory jmsServiceStubFactory = new JMSServiceAdapterFactory(OBJECT_MAPPER, cf, defaultQueue, JMS_TIMEOUT);
	JMSSampleService service = jmsServiceStubFactory.generateJMSServiceProxy(JMSSampleService.class);
	
## Calling a JMS Service

### Fire and forget
	
	service.fireAndForget(message);
	
### Send and recive replay message

    try (JMSFuture<PingMessage> future = service.ping(message)) {
		PingMessage sampleMessage = future.get(100, TimeUnit.MILLISECONDS);
	}

### Send  a message to dynamic destination

    try (JMSFuture<PingMessage> future = service.ping(message, dedicatedQueue)) {
        PingMessage sampleMessage = future.get();
    } 
    
## Send message with headers

	HashMap<String, Object> headers = new HashMap<>();
	headers.put("timeout", true);
	
	try (JMSFuture<PingMessage> future = service.ping(headers, message)) {
	    future.get(100, TimeUnit.MILLISECONDS);
	}

## Wait for multiple JMS-Futures

    try (JMSFuture<PingMessage> future1 = service.ping(message, dedicatedQueue);
        JMSFuture<PingMessage> future2 = service.ping(message, dedicatedQueue)) {
        
        JMSFuture.waitForAll(2000l, future1, future2);    
        PingMessage sampleMessage = future1.get();
        PingMessage sampleMessage = future2.get();
    }
    
# Recive messages with MessageDrivenBean or MessageListener adapter

## Define the POJO reciver class

    public static class SampleMessageServiceWithReply {
		@JMSMessageReceiver
		public PingMessage ping(PingMessage message) {
			return message;
		}
	}

## Initialize the adapter

    SampleMessageServiceWithReply service = new SampleMessageServiceWithReply();
    JMSMessageListenerServiceAdapter<SampleMessageServiceWithReply> adapter =
        JMSMessageListenerServiceAdapter.createAdapter(service, cf, OBJECT_MAPPER);
        
## Delegate onMessage to the POJO adaper 
    
    new MessageListener() {
			
		@Override
		public void onMessage(Message message) {
            adapter.onMessage(textMessage);
        }
    }
    

