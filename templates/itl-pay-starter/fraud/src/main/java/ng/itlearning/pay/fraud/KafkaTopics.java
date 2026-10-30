package ng.itlearning.pay.fraud;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service reads and writes, with the same partition count everywhere,
 * so a consumer never subscribes to a topic the broker invented with the wrong number of partitions.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopics {

    @Bean
    NewTopic transferEvents() {
        return TopicBuilder.name("transfer-events").partitions(3).build();
    }

    @Bean
    NewTopic fraudDecisions() {
        return TopicBuilder.name("fraud-decisions").partitions(3).build();
    }
}
