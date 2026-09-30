package com.neueda.leap.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Message envelope for all Kafka events.
 * 
 * Ensures compatibility across downstream systems by wrapping
 * event payloads with consistent metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEnvelope<T> {
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("correlationId")
    private String correlationId;
    
    @JsonProperty("schemaVersion")
    private String schemaVersion;
    
    @JsonProperty("messageType")
    private String messageType;
    
    @JsonProperty("payload")
    private T payload;
}
