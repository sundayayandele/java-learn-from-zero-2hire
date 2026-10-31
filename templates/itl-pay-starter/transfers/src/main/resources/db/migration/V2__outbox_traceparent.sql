-- The trace context of the request that wrote the event, so the trace continues through Kafka (topic 13.1).
ALTER TABLE outbox ADD COLUMN traceparent TEXT;
