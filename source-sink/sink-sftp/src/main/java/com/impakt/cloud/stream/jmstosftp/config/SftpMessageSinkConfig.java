package com.impakt.cloud.stream.jmstosftp.config;

import java.util.function.Consumer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.messaging.Message;

import com.impakt.cloud.stream.jmstosftp.handler.SftpMessageHandler;
import com.impakt.cloud.stream.jmstosftp.config.properties.SftpMessageSinkBindings;

@Configuration
@EnableConfigurationProperties( { SftpMessageSinkBindings.class } )
@IntegrationComponentScan
public class SftpMessageSinkConfig {

    @Bean
    SftpMessageHandler sftpMessageHandler( SftpMessageSinkBindings sftpMessageSinkBindings ) {
        return new SftpMessageHandler( sftpMessageSinkBindings );
    }

    @Bean
    Consumer<Message<?>> toSftpConsumer( SftpMessageHandler sftpMessageHandler ) {
        return sftpMessageHandler::handleMessage;
    }

/*    @Bean
    public Function<Message<?>, Message<?>> toSftpFunction( SftpMessageHandler sftpMessageHandler ) {
        return message -> {
            sftpMessageHandler.handleMessage( message );
            return message;
        };
    }*/
}
