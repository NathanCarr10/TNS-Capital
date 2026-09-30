package com.neueda.leap.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Trade execution event with message envelope.
 * 
 * The envelope structure ensures compatibility across downstream systems
 * (Settlement, Risk Dashboard, Compliance Audit Log).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent {
    
    // ===== ENVELOPE FIELDS (5 fields) =====
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("correlationId")
    private String correlationId;
    
    @JsonProperty("schemaVersion")
    private String schemaVersion;
    
    @JsonProperty("messageType")
    private String messageType;
    
    @JsonProperty("payload")
    private TradeEventPayload payload;

    // ===== PAYLOAD INNER CLASS =====
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeEventPayload {
        @JsonProperty("orderId")
        private String orderId;
        
        @JsonProperty("accountId")
        private Long accountId;
        
        @JsonProperty("symbol")
        private String symbol;
        
        @JsonProperty("side")
        private String side;
        
        @JsonProperty("quantity")
        private Integer quantity;
        
        @JsonProperty("price")
        private String price;
        
        @JsonProperty("status")
        private String status;
    }
}
