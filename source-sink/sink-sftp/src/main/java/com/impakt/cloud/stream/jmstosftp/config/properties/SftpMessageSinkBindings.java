package com.impakt.cloud.stream.jmstosftp.config.properties;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties( value = "spring.cloud.stream.sink.sftp", ignoreUnknownFields = false )
public class SftpMessageSinkBindings {

    private Map<String, Map<String, SftpMessageSinkProperties>> bindings = new HashMap<>();

}
