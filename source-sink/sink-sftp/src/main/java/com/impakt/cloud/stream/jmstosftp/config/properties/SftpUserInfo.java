package com.impakt.cloud.stream.jmstosftp.config.properties;

import com.jcraft.jsch.UserInfo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class SftpUserInfo implements UserInfo {

    private String passphrase;

    private String password;

    private boolean promptPassphrase;

    private boolean promptPassword;

    private boolean promptYesNo;

    @Override
    public void showMessage( String message ) {
        logger.info( message );
    }

    @Override
    public boolean promptPassword( String message ) {
        logger.info( message );
        return promptPassword;
    }

    @Override
    public boolean promptPassphrase( String message ) {
        logger.info( message );
        return promptPassphrase;
    }

    @Override
    public boolean promptYesNo( String message ) {
        logger.info( message );
        return promptYesNo;
    }
}
