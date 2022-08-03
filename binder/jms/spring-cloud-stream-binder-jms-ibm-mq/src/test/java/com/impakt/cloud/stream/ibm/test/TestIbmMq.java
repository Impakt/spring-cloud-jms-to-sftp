package com.impakt.cloud.stream.ibm.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.impakt.cloud.stream.ibm.test.TestIbmMq.TestConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest( classes = TestConfig.class )
@Testcontainers( disabledWithoutDocker = true )
public class TestIbmMq {

    private static final AtomicInteger sendCounter = new AtomicInteger();

    private static final AtomicInteger receiveCounter = new AtomicInteger();

    static String mqscPath = Thread.currentThread().getContextClassLoader().getResource( "mqsc/10-config.mqsc" )
            .toString().replace( "file:", "" );
    @Container
    static GenericContainer<?> mqContainer = new GenericContainer<>( DockerImageName.parse( "ibmcom/mq" ) )
            .withEnv( "LICENSE", "accept" )
            .withEnv( "MQ_QMGR_NAME", "QM1" )
            .withCopyFileToContainer( MountableFile.forHostPath( mqscPath ), "/etc/mqm/" )
            .withExposedPorts( 1414 )
            .withLogConsumer( new Slf4jLogConsumer( logger ) );
    @Autowired
    public Consumer<String> consumer;

    @DynamicPropertySource
    static void registerMqProperties( DynamicPropertyRegistry dynamicPropertyRegistry ) {
        dynamicPropertyRegistry.add( "test-mq-port", () -> mqContainer.getMappedPort( 1414 ) );
    }

    @BeforeAll
    static void beforeAll() {
        mqContainer.start();
    }

    @Test
    @Timeout( 30 )
    @SuppressWarnings( "BusyWait" )
    void test5MessagesReceived() throws InterruptedException {
        int lastCount = receiveCounter.get();
        while ( receiveCounter.get() <= 5 ) {
            if ( receiveCounter.get() > lastCount ) {
                lastCount = receiveCounter.get();
                logger.debug( "Got {} messages", lastCount );
                if ( lastCount == 5 )
                    break;
            }
            Thread.sleep( 10 );
        }
    }

    @Slf4j
    @SpringBootApplication
    static class TestConfig {

        private final AtomicBoolean semaphore = new AtomicBoolean( false );

        @Bean
        public Consumer<String> consumer() {
            return s -> receiveCounter.incrementAndGet();
        }

        @Bean
        public Supplier<String> supplier() {
            return () -> {
                if ( sendCounter.getAndSet( sendCounter.get() + 1 ) < 5 )
                    return semaphore.getAndSet( !semaphore.get() ) ? "foo" : "bar";
                return null;
            };
        }
    }
}
