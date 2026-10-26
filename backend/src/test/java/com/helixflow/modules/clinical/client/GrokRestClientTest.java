package com.helixflow.modules.clinical.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helixflow.modules.clinical.dto.StructuredSoapResponse;
import com.helixflow.modules.clinical.parser.FallbackSoapExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GrokRestClientTest {

    @Mock
    private RestClient restClient;

    private FallbackSoapExtractor fallbackExtractor;
    private GrokRestClient grokRestClient;

    @BeforeEach
    void setUp() {
        fallbackExtractor = new FallbackSoapExtractor();
        grokRestClient = new GrokRestClient(restClient, fallbackExtractor, new ObjectMapper());
    }

    @Test
    void shouldReturnSyntheticSoapWhenMockModeIsEnabled() {
        ReflectionTestUtils.setField(grokRestClient, "mockMode", true);

        String rawNote = "Patient 58yo male c/o severe central chest pressure radiating to left arm. HR 98, BP 145/90.";
        StructuredSoapResponse response = grokRestClient.generateStructuredSoap(rawNote);

        assertNotNull(response);
        assertNotNull(response.getSubjective());
        assertNotNull(response.getObjective());
        assertNotNull(response.getAssessment());
        assertNotNull(response.getPlan());
        assertNotNull(response.getSafetyDisclaimer());
        assertFalse(response.isFallback());
        assertEquals("grok-2-synthetic-mock", response.getModelVersion());
        assertNotNull(response.getNeedsClinicianReview());
    }

    @Test
    void shouldExtractHeuristicOnFallback() {
        String rawNote = "Patient presents with shortness of breath and persistent fever for 3 days.";
        StructuredSoapResponse response = fallbackExtractor.extractHeuristic(rawNote);

        assertNotNull(response);
        assertTrue(response.isFallback());
        assertEquals("rule-based-heuristic-fallback", response.getModelVersion());
        assertNotNull(response.getSubjective().getChiefComplaint());
    }
}
