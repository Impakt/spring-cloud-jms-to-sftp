package com.impakt.cloud.stream.jmstosftp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.google.common.io.Files;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.impakt.cloud.stream.jmstosftp.TestMqToSftp.TestConfig;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest( classes = { TestConfig.class, JmsToSftpApplication.class } )
@Testcontainers( disabledWithoutDocker = true )
public class TestMqToSftp {

    private static final Logger log = LoggerFactory.getLogger( TestMqToSftp.class );

    private static final AtomicInteger sendCounter = new AtomicInteger( -1 );

    private static final List<String> fileContents = List.of( "one", "two", "three", "four", "five" );

    private static String sftpPath;

    @Container
    static GenericContainer<?> mqContainer = new GenericContainer<>( DockerImageName.parse( "ibmcom/mq" ) )
            .withEnv( "LICENSE", "accept" )
            .withEnv( "MQ_QMGR_NAME", "QM1" )
            .withCopyFileToContainer( MountableFile.forClasspathResource( "mqsc/10-config.mqsc" ), "/etc/mqm/" )
            .withExposedPorts( 1414 )
            .withLogConsumer( new Slf4jLogConsumer( log ) );
    static GenericContainer<?> sftpContainer;

    static File tempDir;

    static List<String> users = List.of( "foo", "bar", "dak" );

    @DynamicPropertySource
    static void registerMqProperties( DynamicPropertyRegistry dynamicPropertyRegistry ) {
        dynamicPropertyRegistry.add( "test-mq-port", () -> mqContainer.getMappedPort( 1414 ) );
        dynamicPropertyRegistry.add( "test-sftp-port", () -> sftpContainer.getMappedPort( 22 ) );
        dynamicPropertyRegistry.add( "test-sftp-path", () -> sftpPath );
    }

    @AfterAll
    static void afterAll() {
        sftpContainer.stop();
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        tempDir = Files.createTempDir();
        sftpPath = ResourceUtils.getFile( "classpath:sftp" ).getAbsolutePath();
        sftpContainer = new GenericContainer<>( DockerImageName.parse( "atmoz/sftp" ) )
                .withExposedPorts( 22 )
                .withLogConsumer( new Slf4jLogConsumer( log ) )
                .withFileSystemBind( ResourceUtils.getFile( "classpath:sftp/users.conf" ).getAbsolutePath(),
                        "/etc/sftp/users.conf",
                        BindMode.READ_ONLY );

        users.forEach( u -> {
            File userUploadDir = new File( tempDir, String.format( "%s/upload", u ) );
            if ( !userUploadDir.mkdirs() )
                throw new RuntimeException( "Could not create tmp dir for tests" );
            sftpContainer.withFileSystemBind( userUploadDir.getAbsolutePath(), String.format( "/home/%s/upload", u ) );

            File sshPublicKey = new File( sftpPath, String.format( "%s-id-rsa.pub", u ) );
            if ( sshPublicKey.exists() ) {
                sftpContainer.withFileSystemBind( sshPublicKey.getAbsolutePath(),
                        String.format( "/home/%s/.ssh/keys/%s-id-rsa.pub", u, u ) );
            }
        } );
        mqContainer.start();
        sftpContainer.start();
    }

    @Test
    @Timeout( 15 )
    void test5FilesReceived() throws IOException {
        boolean worked = false;
        while ( !worked ) {
            worked = true;
            for ( String user : users ) {
                File userUploadDir = new File( tempDir, String.format( "%s/upload", user ) );
                assertTrue( userUploadDir.exists() );
                for ( String expectedFileContents : fileContents ) {
                    File tempFile = new File( userUploadDir, String.format( "FOO.QUEUE-%s.txt",
                            expectedFileContents.toUpperCase( Locale.ROOT ) ) );
                    if ( !tempFile.exists() )
                        worked = false;
                    else {
                        String actualFileContent = FileUtils.readFileToString( tempFile, "UTF-8" );
                        if ( !expectedFileContents.toUpperCase().equals( actualFileContent ) )
                            worked = false;
                    }
                }
            }
        }
    }

    @Slf4j
    @SpringBootApplication
    static class TestConfig {

        @Bean
        Function<Message<?>, Message<?>> upperCaseFunction() {
            return message -> {
                System.out.println( "to upper case called" );
                return new GenericMessage<>( message.getPayload().toString().toUpperCase(), message.getHeaders() );
            };
        }

        @Bean
        public Supplier<String> supplier() {
            return () -> {
                if ( sendCounter.incrementAndGet() < 5 )
                    return fileContents.get( sendCounter.get() );
                return null;
            };
        }
    }
}
