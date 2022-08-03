package com.impakt.cloud.stream.ibm.config;

import static com.impakt.cloud.stream.jms.utils.JmsBinderUtils.runRetryTemplate;

import java.util.Collections;

import javax.jms.Connection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.mq.spring.boot.MQConfigurationProperties;
import com.ibm.mq.spring.boot.MQConnectionFactoryFactory;
import com.impakt.cloud.stream.jms.binder.JmsMessageChannelBinder;
import com.impakt.cloud.stream.jms.properties.JmsExtendedBindingProperties;
import com.impakt.cloud.stream.jms.provisioning.JmsProvisioningProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties( { MQConfigurationProperties.class, JmsExtendedBindingProperties.class } )
public class IbmMqConfiguration {

    @Bean
    MQConnectionFactory connectionFactory( MQConfigurationProperties properties,
                                           JmsExtendedBindingProperties jmsExtendedBindingProperties )
            throws Exception {
        logger.debug( String.format( "Creating connection factory for : %s", properties.getApplicationName() ) );
        return runRetryTemplate( jmsExtendedBindingProperties.getRetry(),
                context -> {
                    MQConnectionFactory mqConnectionFactoryInner = new MQConnectionFactoryFactory( properties,
                            Collections.emptyList() ).createConnectionFactory(
                            MQConnectionFactory.class );
                    Connection connection;
                    connection = mqConnectionFactoryInner.createConnection();
                    connection.close();
                    return mqConnectionFactoryInner;
                } );
    }

    @Bean
    JmsProvisioningProvider ibmMqExchangeProvisioner( MQConnectionFactory mqConnectionFactory ) {
        return new JmsProvisioningProvider( mqConnectionFactory );
    }

    @Bean
    JmsMessageChannelBinder ibmMqMessageChannelBinder( MQConnectionFactory connectionFactory,
                                                       JmsExtendedBindingProperties jmsExtendedBindingProperties,
                                                       JmsProvisioningProvider ibmMqExchangeProvisioner ) {
        return new JmsMessageChannelBinder( connectionFactory, jmsExtendedBindingProperties, ibmMqExchangeProvisioner );
    }
}
