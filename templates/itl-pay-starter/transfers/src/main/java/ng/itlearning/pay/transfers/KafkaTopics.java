package ng.itlearning.pay.transfers;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service uses, with the same partition count everywhere. In production
 * the platform team usually provisions topics and the broker's auto-creation is switched off.
 */
@Configuration(proxyBeanMethods = false)
class KafkaTopics {

    @Bean
    NewTopic transferEvents() {
        return TopicBuilder.name(Outbox.TOPIC).partitions(3).build();
    }

    @Bean
    NewTopic fraudDecisions() {
        return TopicBuilder.name("fraud-decisions").partitions(3).build();
    }
}
