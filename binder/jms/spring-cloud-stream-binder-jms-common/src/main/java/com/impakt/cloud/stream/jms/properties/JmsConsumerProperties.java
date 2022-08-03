package com.impakt.cloud.stream.jms.properties;

import org.springframework.cloud.stream.binder.ConsumerProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode( callSuper = true )
public class JmsConsumerProperties extends ConsumerProperties {

    private Type type = Type.QUEUE;

}
