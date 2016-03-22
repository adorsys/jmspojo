# Use JMS like Pojos

## Maven Dependency

    <dependency>
		<groupId>de.adorsys.jmspojo</groupId>
		<artifactId>jmspojo</artifactId>
	    <version>0.1</version>
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

## Initializing the Service Interface

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
    
# Receive messages with MessageDrivenBean or MessageListener adapter

## Define the POJO receiver class

    public static class SampleMessageServiceWithReply {
		@JMSMessageReceiver
		public PingMessage ping(PingMessage message) {
			return message;
		}
	}
	
## Implementing a Message Driven Bean

	@MessageDriven(activationConfig= {
    	@ActivationConfigProperty(propertyName="destinationType", propertyValue="javax.jms.Queue"),
    	@ActivationConfigProperty(propertyName="destination", propertyValue="sampleQ")
	})
	public class SampleMDB
			extends JMSAbstractMessageListener<SampleMessageServiceWithReply> {
		
		@Inject
		SampleMessageServiceWithReply service;
		
		@Resource(lookup="java:/JmsXA")
		ConnectionFactory cf;

		@Override
		protected SampleMessageServiceWithReply getService() {
			return service;
		}

		@Override
		protected ConnectionFactory getConnectionFactory() {
			return cf;
		}
	}

## Initialize the adapter

    SampleMessageServiceWithReply service = new SampleMessageServiceWithReply();
    JMSMessageListenerServiceAdapter<SampleMessageServiceWithReply> adapter =
        JMSMessageListenerServiceAdapter.createAdapter(service, cf, OBJECT_MAPPER);
        
## Delegate onMessage to the POJO adapter 
    
    new MessageListener() {
			
		@Override
		public void onMessage(Message message) {
            adapter.onMessage(textMessage);
        }
    }
    

