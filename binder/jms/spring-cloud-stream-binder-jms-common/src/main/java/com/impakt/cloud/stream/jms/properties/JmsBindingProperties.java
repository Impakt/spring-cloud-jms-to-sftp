package com.impakt.cloud.stream.jms.properties;

import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;

import lombok.Data;

@Data
public class JmsBindingProperties implements BinderSpecificPropertiesProvider {

    private Type type = Type.QUEUE;

    private JmsConsumerProperties consumer = new JmsConsumerProperties();

    private JmsProducerProperties producer = new JmsProducerProperties();

    public Type getType() {
        return type;
    }

    public void setType( Type type ) {
        this.type = type;
        setConsumer( getConsumer() );
        setProducer( getProducer() );
    }

    public void setConsumer( JmsConsumerProperties consumer ) {
        this.consumer = consumer;
        if ( this.consumer != null )
            this.consumer.setType( getType() );
    }

    public void setProducer( JmsProducerProperties producer ) {
        this.producer = producer;
        if ( this.producer != null )
            this.producer.setDestinationType( getType() );
    }
}