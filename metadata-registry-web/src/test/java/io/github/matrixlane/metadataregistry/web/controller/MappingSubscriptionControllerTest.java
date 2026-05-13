package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscription;
import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscriptionService;
import io.github.matrixlane.metadataregistry.itemmapping.notification.SubscriptionType;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.mapping.SubscriptionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingSubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class MappingSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MappingSubscriptionService subscriptionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @Test
    void subscribeToScheme_shouldReturnOk() throws Exception {
        UUID schemeId = UUID.randomUUID();
        SubscriptionRequest request = new SubscriptionRequest("user-1", SubscriptionType.SCHEME, schemeId);
        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.SCHEME, schemeId);

        when(subscriptionService.subscribeToScheme(eq("user-1"), eq(schemeId))).thenReturn(sub);

        mockMvc.perform(post("/api/v1/mappings/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-1"));
    }

    @Test
    void subscribeToItem_shouldReturnOk() throws Exception {
        UUID itemId = UUID.randomUUID();
        SubscriptionRequest request = new SubscriptionRequest("user-1", SubscriptionType.ITEM, itemId);
        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.ITEM, itemId);

        when(subscriptionService.subscribeToItem(eq("user-1"), eq(itemId))).thenReturn(sub);

        mockMvc.perform(post("/api/v1/mappings/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-1"));
    }

    @Test
    void getUserSubscriptions_shouldReturnOk() throws Exception {
        UUID schemeId = UUID.randomUUID();
        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.SCHEME, schemeId);

        when(subscriptionService.getUserSubscriptions(eq("user-1"))).thenReturn(List.of(sub));

        mockMvc.perform(get("/api/v1/mappings/subscriptions")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
