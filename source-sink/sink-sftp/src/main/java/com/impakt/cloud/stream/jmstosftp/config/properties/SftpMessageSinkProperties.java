package com.impakt.cloud.stream.jmstosftp.config.properties;

import java.time.Duration;
import java.util.Properties;

import lombok.Data;

@Data
public class SftpMessageSinkProperties {

    private String host;

    private int port = 22;

    private String user;

    private String password;

    private String knownHosts;

    private String privateKey;

    private String privateKeyPassphrase;

    private Properties sessionConfig;

    private Integer timeout;

    private String clientVersion;

    private String hostKeyAlias;

    private Integer serverAliveInterval;

    private Integer serverAliveCountMax;

    private Boolean enableDaemonThread;

    private SftpUserInfo userInfo;

    private boolean allowUnknownKeys;

    private Duration channelConnectTimeout;

    private String remoteLocation;

}
