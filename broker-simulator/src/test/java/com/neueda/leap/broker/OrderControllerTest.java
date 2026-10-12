package com.neueda.leap.broker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Broker broker;

    @Test
    @DisplayName("accepts a valid order with 202 and queues it")
    void acceptsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"orderId":"4f63fed9-6d52-424b-844b-504313595bb9","accountId":11,"symbol":"ACME",
                 "side":"BUY","quantity":10,"limitPrice":30.00}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value("4f63fed9-6d52-424b-844b-504313595bb9"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(broker).accept(any(BrokerOrder.class));
    }

    @Test
    @DisplayName("rejects an invalid order with 400 and does not queue it")
    void rejectsInvalidOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"orderId":"4f63fed9-6d52-424b-844b-504313595bb9","accountId":11,"symbol":"ACME",
                 "side":"BUY","quantity":0,"limitPrice":30.00}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"))
                .andExpect(jsonPath("$.message").value("quantity: must be greater than 0"));

        verifyNoInteractions(broker);
    }
}
