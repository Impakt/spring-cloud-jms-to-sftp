package com.impakt.cloud.stream.jms.properties;

import org.springframework.cloud.stream.binder.ProducerProperties;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode( callSuper = true )
public class JmsProducerProperties extends ProducerProperties {

    private MessageBodyType messageBodyType = MessageBodyType.BYTE_ARRAY;

    private Type destinationType = Type.QUEUE;

}
