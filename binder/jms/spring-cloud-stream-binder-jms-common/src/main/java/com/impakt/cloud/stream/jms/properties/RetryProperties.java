package com.impakt.cloud.stream.jms.properties;

import org.springframework.retry.backoff.ExponentialBackOffPolicy;

import lombok.Data;

@Data
public class RetryProperties {

    private boolean enabled = true;

    private int retryMaxAttempts = 10;

    private long retryInitialInterval = new ExponentialBackOffPolicy().getInitialInterval();

    private long retryMaxInterval = new ExponentialBackOffPolicy().getMaxInterval();

    private double retryMultiplier = new ExponentialBackOffPolicy().getMultiplier();

}
