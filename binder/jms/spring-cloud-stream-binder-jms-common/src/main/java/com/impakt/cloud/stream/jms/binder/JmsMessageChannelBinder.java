package com.impakt.cloud.stream.jms.binder;

import static com.impakt.cloud.stream.jms.utils.JmsBinderUtils.runRetryTemplate;
import static com.impakt.cloud.stream.jms.utils.JmsBinderUtils.sanitiseDestinationName;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.AbstractMessageChannelBinder;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.cloud.stream.provisioning.ProvisioningException;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

import com.impakt.cloud.stream.jms.properties.JmsConsumerProperties;
import com.impakt.cloud.stream.jms.properties.JmsExtendedBindingProperties;
import com.impakt.cloud.stream.jms.properties.JmsProducerProperties;
import com.impakt.cloud.stream.jms.properties.Type;
import com.impakt.cloud.stream.jms.provisioning.JmsProvisioningProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JmsMessageChannelBinder
        extends AbstractMessageChannelBinder<ExtendedConsumerProperties<JmsConsumerProperties>,
                                                    ExtendedProducerProperties<JmsProducerProperties>,
                                                    JmsProvisioningProvider>
        implements ExtendedPropertiesBinder<MessageChannel, JmsConsumerProperties, JmsProducerProperties> {

    private final ConnectionFactory connectionFactory;

    private final JmsExtendedBindingProperties jmsExtendedBindingProperties;

    public JmsMessageChannelBinder( ConnectionFactory connectionFactory,
                                    JmsExtendedBindingProperties jmsExtendedBindingProperties,
                                    JmsProvisioningProvider provisioningProvider ) {
        super( new String[0], provisioningProvider );
        this.connectionFactory = connectionFactory;
        this.jmsExtendedBindingProperties = Objects
                .requireNonNullElseGet( jmsExtendedBindingProperties, JmsExtendedBindingProperties::new );
    }

    @Override
    protected MessageHandler createProducerMessageHandler( ProducerDestination destination,
                                                           ExtendedProducerProperties<JmsProducerProperties> producerProperties,
                                                           MessageChannel errorChannel ) {
        JmsTemplate jmsTemplate = new JmsTemplate( connectionFactory );
        jmsTemplate.setDefaultDestinationName( sanitiseDestinationName( destination.getName() ) );
        return new AbstractMessageHandler() {
            final Logger listenerLogger = LoggerFactory.getLogger( destination.getName() );

            @Override
            protected void handleMessageInternal( org.springframework.messaging.Message<?> message ) {
                try {
                    runRetryTemplate( jmsExtendedBindingProperties.getRetry(),
                            context -> {
                                String text = new String( (byte[]) message.getPayload() );
                                listenerLogger.debug( "Sending : {} as a byte array", text );

                                jmsTemplate.convertAndSend( message.getPayload(), jmsMessage -> {
                                    message.getHeaders().forEach( ( k, v ) -> {
                                        String key = k.replace( '-', '_' );
                                        try {
                                            if ( v instanceof Integer )
                                                jmsMessage.setIntProperty( key, (Integer) v );
                                            else if ( v instanceof Long )
                                                jmsMessage.setLongProperty( key, (Long) v );
                                            else if ( v instanceof String )
                                                jmsMessage.setStringProperty( key, v.toString() );
                                            else if ( v instanceof UUID )
                                                jmsMessage.setStringProperty( key, v.toString() );
                                            else
                                                throw new RuntimeException( String.format(
                                                        "Don't know how to map header value %s of type %s", v,
                                                        v.getClass().getName() ) );
                                        } catch ( JMSException e ) {
                                            throw new RuntimeException( e );
                                        }
                                    } );
                                    return jmsMessage;
                                } );
                                return true;
                            } );
                } catch ( Exception e ) {
                    logger.error( e, "Error sending JMS message to " + destination.getName() );
                    throw new RuntimeException( e );
                }
            }
        };
    }

    @Override
    protected MessageProducer createConsumerEndpoint( ConsumerDestination destination, String group,
                                                      ExtendedConsumerProperties<JmsConsumerProperties> properties ) {
        try {
            Connection connection = connectionFactory.createConnection();
            Session session = connection.createSession( true, Session.AUTO_ACKNOWLEDGE );
            Destination jmsDest;
            if ( properties.getExtension().getType() == Type.QUEUE )
                jmsDest = session.createQueue( sanitiseDestinationName( destination.getName() ) );
            else
                jmsDest = session.createTopic( sanitiseDestinationName( destination.getName() ) );
            final MessageConsumer messageConsumer = session.createConsumer( jmsDest );
            JmsMessageConsumer jmsMessageConsumer = new JmsMessageConsumer( properties );
            messageConsumer.setMessageListener( jmsMessageConsumer );
            connection.start();
            return jmsMessageConsumer;
        } catch ( JMSException e ) {
            logger.error( String.format( "Error creating producer destination '%s'.", destination.getName() ),
                    e );
            throw new ProvisioningException(
                    String.format( "Error creating producer destination '%s'.", destination.getName() ), e );
        }
    }

    @Override
    public JmsConsumerProperties getExtendedConsumerProperties( String channelName ) {
        return jmsExtendedBindingProperties.getExtendedConsumerProperties( channelName );
    }

    @Override
    public JmsProducerProperties getExtendedProducerProperties( String channelName ) {
        return jmsExtendedBindingProperties.getExtendedProducerProperties( channelName );
    }

    @Override
    public String getDefaultsPrefix() {
        return null;
    }

    @Override
    public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
        return null;
    }

    class JmsMessageConsumer implements javax.jms.MessageListener, MessageProducer {

        MessageChannel messageChannel;

        ExtendedConsumerProperties<JmsConsumerProperties> properties;

        public JmsMessageConsumer( ExtendedConsumerProperties<JmsConsumerProperties> properties ) {
            this.properties = properties;
        }

        @Override
        public void onMessage( Message jmsMessage ) {
            try {
                String body;
                if ( jmsMessage instanceof TextMessage )
                    body = jmsMessage.getBody( String.class );
                else if ( jmsMessage instanceof BytesMessage )
                    body = new String( jmsMessage.getBody( byte[].class ) );
                else //TODO add mappings for other message types?
                    throw new Exception( "Don't know how to handle message : " + jmsMessage.getClass().getName() );
                logger.debug( "JMS Listener got : {} @{}.", body, jmsMessage.getJMSDestination().toString() );
                Map<String, Object> headers = new HashMap<>();
                if ( jmsMessage.getJMSDestination() instanceof Queue )
                    headers.put( "destinationName",
                            ( (Queue) jmsMessage.getJMSDestination() ).getQueueName() );
                else
                    headers.put( "destinationName",
                            ( (Topic) jmsMessage.getJMSDestination() ).getTopicName() );
                Enumeration<?> jmsMessageHeadersEnumeration = jmsMessage.getPropertyNames();
                while ( jmsMessageHeadersEnumeration.hasMoreElements() ) {
                    String name = (String) jmsMessageHeadersEnumeration.nextElement();
                    Object o = jmsMessage.getObjectProperty( name );
                    headers.put( name, o );
                }
                final GenericMessage<Object> message = new GenericMessage<>( body, headers );
                runRetryTemplate( jmsExtendedBindingProperties.getRetry(),
                        context -> messageChannel.send( message ) );
            } catch ( Exception e ) {
                logger.error( "Error processing JMS message,", e );
                throw new RuntimeException( e );
            }
        }

        @Override
        public MessageChannel getOutputChannel() {
            return messageChannel;
        }

        @Override
        public void setOutputChannel( MessageChannel outputChannel ) {
            this.messageChannel = outputChannel;
        }
    }
}
