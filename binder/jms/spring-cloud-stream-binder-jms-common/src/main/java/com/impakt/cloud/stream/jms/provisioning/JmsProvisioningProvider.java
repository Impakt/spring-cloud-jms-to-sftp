package com.impakt.cloud.stream.jms.provisioning;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;

import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.cloud.stream.provisioning.ProvisioningException;
import org.springframework.cloud.stream.provisioning.ProvisioningProvider;

import com.impakt.cloud.stream.jms.properties.JmsConsumerProperties;
import com.impakt.cloud.stream.jms.properties.JmsProducerProperties;
import com.impakt.cloud.stream.jms.properties.Type;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JmsProvisioningProvider implements
                                     ProvisioningProvider<ExtendedConsumerProperties<JmsConsumerProperties>,
                                                                 ExtendedProducerProperties<JmsProducerProperties>> {

    private ConnectionFactory connectionFactory;

    public JmsProvisioningProvider( ConnectionFactory connectionFactory ) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public ProducerDestination provisionProducerDestination( String name,
                                                             ExtendedProducerProperties<JmsProducerProperties> properties )
            throws ProvisioningException {
        try ( Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession( true, Session.AUTO_ACKNOWLEDGE ) ) {
            logger.debug( "Creating producer destination {} @{}", name,
                    properties.getExtension().getDestinationType() );
            if ( properties.getExtension().getDestinationType() == Type.QUEUE )
                session.createQueue( name.replace( '-', '_' ) );
            else
                session.createTopic( name.replace( '-', '_' ) );
        } catch ( JMSException e ) {
            throw new ProvisioningException( String.format( "Error creating producer destination '%s'.", name ), e );
        }
        return new ProducerDestination() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getNameForPartition( int partition ) {
                return name;
            }
        };
    }

    @Override
    public ConsumerDestination provisionConsumerDestination( String name, String group,
                                                             ExtendedConsumerProperties<JmsConsumerProperties> properties )
            throws ProvisioningException {
        logger.debug( "Creating consumer destination {} @{}", name, properties.getExtension().getType() );
        try ( Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession( true, Session.AUTO_ACKNOWLEDGE ) ) {
            if ( properties.getExtension().getType() == Type.QUEUE )
                session.createQueue( name.replace( '-', '_' ) );
            else
                session.createTopic( name.replace( '-', '_' ) );
        } catch ( JMSException e ) {
            throw new ProvisioningException( String.format( "Error creating consumer destination '%s'.", name ), e );
        }
        return () -> name;
    }
}
