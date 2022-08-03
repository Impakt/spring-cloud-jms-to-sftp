package com.impakt.cloud.stream.jmstosftp.handler;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

import com.impakt.cloud.stream.jmstosftp.config.properties.SftpMessageSinkBindings;
import com.impakt.cloud.stream.jmstosftp.config.properties.SftpMessageSinkProperties;
import com.jcraft.jsch.ChannelSftp.LsEntry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SftpMessageHandler implements DisposableBean {

    private final SftpMessageSinkBindings sftpMessageSinkBindings;

    private final Map<String, SessionFactory<LsEntry>> handlerMap = new ConcurrentHashMap<>();

    public SftpMessageHandler( SftpMessageSinkBindings sftpMessageSinkBindings ) {
        this.sftpMessageSinkBindings = sftpMessageSinkBindings;
    }

    @Override
    public void destroy() {
        for ( Object v : handlerMap.values() ) {
            if ( v instanceof DisposableBean ) {
                try {
                    ( (DisposableBean) v ).destroy();
                } catch ( Exception exception ) {
                    // no op
                }
            }
        }
        handlerMap.clear();
    }

    private String getDestinationName( Message<?> message ) {
        return (String) message.getHeaders().get( "destinationName" );
    }

    private SessionFactory<LsEntry> getSftpMessageFactory( Message<?> objectMessage, String bindingName ) {
        String destinationName = getDestinationName( objectMessage );
        String factoryName = String.format( "%s-%s", destinationName, bindingName );
        SessionFactory<LsEntry> sessionFactory = handlerMap.get( factoryName );
        if ( sessionFactory == null ) {
            SftpMessageSinkProperties properties = sftpMessageSinkBindings.getBindings().get( destinationName ).get(
                    bindingName );
            DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory( true );
            factory.setAllowUnknownKeys( properties.isAllowUnknownKeys() );
            factory.setHost( properties.getHost() );
            factory.setPort( properties.getPort() );
            factory.setPassword( properties.getPassword() );
            factory.setUser( properties.getUser() );
            if ( properties.getChannelConnectTimeout() != null )
                factory.setChannelConnectTimeout( properties.getChannelConnectTimeout() );
            factory.setClientVersion( properties.getClientVersion() );
            factory.setEnableDaemonThread( properties.getEnableDaemonThread() );
            factory.setHostKeyAlias( properties.getHostKeyAlias() );
            if ( properties.getKnownHosts() != null )
                factory.setKnownHostsResource( new FileSystemResource( properties.getKnownHosts() ) );
            if ( properties.getPrivateKey() != null )
                factory.setPrivateKey( new FileSystemResource( properties.getPrivateKey() ) );
            factory.setPrivateKeyPassphrase( properties.getPrivateKeyPassphrase() );

            factory.setServerAliveCountMax( properties.getServerAliveCountMax() );
            factory.setServerAliveInterval( properties.getServerAliveInterval() );
            factory.setSessionConfig( properties.getSessionConfig() );
            factory.setTimeout( properties.getTimeout() );
            factory.setUserInfo( properties.getUserInfo() );
            factory.setSessionConfig( properties.getSessionConfig() );

            //factory.setProxy( properties.getProxy() ) not supported;
            //factory.setSocketFactory( properties.getSocketFactory() ); not supported
            CachingSessionFactory<LsEntry> cachingSessionFactory = new CachingSessionFactory<>( factory );
            handlerMap.put( factoryName, cachingSessionFactory );

            sessionFactory = handlerMap.get( factoryName );
        }
        return sessionFactory;
    }

    public void handleMessage( Message<?> message ) throws MessagingException {
        Map<String, SftpMessageSinkProperties> propsMap =
                sftpMessageSinkBindings.getBindings().get( getDestinationName( message ) );
        for ( Map.Entry<String, SftpMessageSinkProperties> propsEntry : propsMap.entrySet() ) {
            SftpMessageSinkProperties props = propsEntry.getValue();
            SessionFactory<LsEntry> sessionFactory = getSftpMessageFactory( message, propsEntry.getKey() );

            try ( Session<LsEntry> session = sessionFactory.getSession() ) {
                ExpressionParser parser = new SpelExpressionParser();
                Map<String, Object> contextMap = new HashMap<>();
                contextMap.put( "handler", this );
                contextMap.put( "message", message );
                contextMap.put( "headers", message.getHeaders() );
                contextMap.put( "destinationName", message.getHeaders().get( "destinationName" ) );
                StandardEvaluationContext context = new StandardEvaluationContext();
                context.addPropertyAccessor( new MapAccessor() );
                Expression expression = parser.parseExpression( props.getRemoteLocation() );
                String remoteLocation = expression.getValue( context, contextMap, String.class );

                logger.debug( "Writing file to {} from SpEL {}", remoteLocation, props.getRemoteLocation() );
                Object payload = message.getPayload();
                if ( payload instanceof byte[] ) {
                    logger.trace( "Writing byte array {} to file", new String( (byte[]) payload ) );
                    session.write(
                            new ByteArrayInputStream( (byte[]) message.getPayload() ), remoteLocation );
                } else if ( payload instanceof String ) {
                    logger.trace( "Writing string {} to file", payload );
                    session.write(
                            new ByteArrayInputStream(
                                    message.getPayload().toString().getBytes( StandardCharsets.UTF_8 ) ),
                            remoteLocation );
                }
            } catch ( Exception e ) {
                logger.error( "Failed to SFTP file to binding "
                                      + getDestinationName( message )
                                      + "-" + propsEntry.getKey(), e );
                throw new MessagingException( message, e );
            }
        }
    }
}