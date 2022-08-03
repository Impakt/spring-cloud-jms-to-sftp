package com.impakt.cloud.stream.jms.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetryTemplate;

import com.impakt.cloud.stream.jms.properties.RetryProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JmsBinderUtils {

    public static <T, E extends Exception> T runRetryTemplate(
            RetryProperties childRetryProperties,
            RetryProperties parentRetryProperties,
            RetryCallback<T, E> callback ) throws E {
        if ( childRetryProperties.isEnabled() )
            return runRetryTemplate( childRetryProperties, callback );
        return runRetryTemplate( parentRetryProperties, callback );
    }

    public static <T, E extends Throwable> T runRetryTemplate(
            RetryProperties retryProperties,
            RetryCallback<T, E> callback ) throws E {
        if ( !retryProperties.isEnabled() )
            return callback.doWithRetry( new NeverRetryContext() );
        return RetryTemplate.builder()
                .maxAttempts( retryProperties.getRetryMaxAttempts() )
                .exponentialBackoff( retryProperties.getRetryInitialInterval(),
                        retryProperties.getRetryMultiplier(),
                        retryProperties.getRetryMaxInterval() )
                .retryOn( Throwable.class )
                .traversingCauses()
                .build().execute( context -> {
                    if ( context.getLastThrowable() != null ) {
                        logger.warn( "Retrying an operation. Retry {}", context.getRetryCount() );
                        if ( logger.isDebugEnabled() ) {
                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter( sw );
                            context.getLastThrowable().printStackTrace( pw );
                            logger.debug( sw.toString() );
                        }
                    }
                    return callback.doWithRetry( context );
                } );
    }

    public static String sanitiseDestinationName( String destinationName ) {
        String newName = destinationName.replace( '-', '_' );
        if ( !newName.equals( destinationName ) )
            logger.warn( "Jms destination sanitisation took place: " + destinationName );
        return newName;
    }

    private static class NeverRetryContext extends RetryContextSupport {

        private boolean finished = false;

        public NeverRetryContext() {
            super( null );
        }

    }
}
