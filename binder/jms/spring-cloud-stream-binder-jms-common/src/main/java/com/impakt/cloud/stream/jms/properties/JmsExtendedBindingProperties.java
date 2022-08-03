/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.impakt.cloud.stream.jms.properties;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.cloud.stream.binder.ExtendedBindingProperties;

/**
 * Configuration for extended binding metadata.
 *
 * @author Oleg Zhurakousky
 */
@ConfigurationProperties( "spring.cloud.stream.jms" )
public class JmsExtendedBindingProperties implements
                                          ExtendedBindingProperties<JmsConsumerProperties, JmsProducerProperties> {

    private static final String DEFAULTS_PREFIX = "spring.cloud.stream.jms.default";

    private RetryProperties retry = new RetryProperties();

    private Map<String, JmsBindingProperties> bindings = new HashMap<>();

    public Map<String, JmsBindingProperties> getBindings() {
        return bindings;
    }

    public RetryProperties getRetry() {
        return retry;
    }

    @Override
    public String getDefaultsPrefix() {
        return DEFAULTS_PREFIX;
    }

    @Override
    public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
        return BinderSpecificPropertiesProvider.class;
    }

    @Override
    public JmsConsumerProperties getExtendedConsumerProperties( String channelName ) {
        if ( bindings.containsKey( channelName ) && bindings.get( channelName ).getConsumer() != null ) {
            return bindings.get( channelName ).getConsumer();
        } else {
            return new JmsConsumerProperties();
        }
    }

    @Override
    public JmsProducerProperties getExtendedProducerProperties( String channelName ) {
        if ( bindings.containsKey( channelName ) && bindings.get( channelName ).getProducer() != null ) {
            return bindings.get( channelName ).getProducer();
        } else {
            return new JmsProducerProperties();
        }
    }

}
