package ng.itlearning.pay.notifications;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

/**
 * Blocking retries, then a dead-letter topic (topic 11.4). Spring Boot applies this
 * error handler to every @KafkaListener in the service.
 */
@Configuration(proxyBeanMethods = false)
class KafkaErrorHandling {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(record.topic() + "-notifications-dlt", -1));   // -1: let Kafka pick the partition
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));          // 1 try + 2 retries, 1 s apart
        handler.addNotRetryableExceptions(JacksonException.class);                            // a poison message goes straight to the DLT
        return handler;
    }

    @Bean
    NewTopic transferEvents() {                       // declared by every service that uses it, with the same partitions
        return TopicBuilder.name("transfer-events").partitions(3).build();
    }

    @Bean
    NewTopic deadLetters() {
        return TopicBuilder.name("transfer-events-notifications-dlt").partitions(1).build();
    }
}
