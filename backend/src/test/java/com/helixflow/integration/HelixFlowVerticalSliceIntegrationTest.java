package com.helixflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helixflow.common.dto.ApiResponse;
import com.helixflow.modules.auth.dto.LoginRequest;
import com.helixflow.modules.auth.dto.LoginResponse;
import com.helixflow.modules.patient.dto.PatientRegistrationDto;
import com.helixflow.modules.patient.dto.PatientResponseDto;
import com.helixflow.modules.patient.dto.TriageAssessmentDto;
import com.helixflow.modules.patient.dto.VisitCreateDto;
import com.helixflow.modules.patient.dto.VisitResponseDto;
import com.helixflow.modules.patient.entity.VisitType;
import com.helixflow.modules.resource.bed.dto.BedAllocationDto;
import com.helixflow.modules.resource.bed.dto.BedCapacitySummaryDto;
import com.helixflow.modules.resource.bed.dto.BedDto;
import com.helixflow.modules.resource.bed.entity.BedStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class HelixFlowVerticalSliceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End Vertical Slice: Login -> Register Patient -> Triage -> Bed Allocation -> Discharge")
    void testFullVerticalSliceWorkflow() throws Exception {
        // Step 1: Login as Receptionist
        LoginRequest receptLogin = new LoginRequest("recept_bob", "ReceptPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receptLogin)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<LoginResponse> receptAuth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, LoginResponse.class));
        String receptToken = receptAuth.getData().getToken();
        assertNotNull(receptToken);

        // Step 2: Register Synthetic Patient
        PatientRegistrationDto regDto = PatientRegistrationDto.builder()
                .firstName("Eleanor")
                .lastName("Vance")
                .dateOfBirth(LocalDate.of(1985, 4, 12))
                .gender("FEMALE")
                .phone("555-0199")
                .emergencyContact("Theodora Vance - Sister (555-0188)")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + receptToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regDto)))
                .andExpect(status().isCreated())
                .andReturn();

        ApiResponse<PatientResponseDto> patientResp = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, PatientResponseDto.class));
        Long patientId = patientResp.getData().getId();
        assertNotNull(patientId);
        assertTrue(patientResp.getData().getMrn().startsWith("SYN-"));

        // Step 3: Create Encounter Visit
        VisitCreateDto visitDto = VisitCreateDto.builder()
                .patientId(patientId)
                .visitType(VisitType.EMERGENCY)
                .chiefComplaint("Sudden severe retrosternal chest pain radiating to left shoulder, diaphoresis.")
                .build();

        MvcResult visitResult = mockMvc.perform(post("/api/v1/patients/" + patientId + "/visits")
                        .header("Authorization", "Bearer " + receptToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(visitDto)))
                .andExpect(status().isCreated())
                .andReturn();

        ApiResponse<VisitResponseDto> visitResp = objectMapper.readValue(
                visitResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, VisitResponseDto.class));
        Long visitId = visitResp.getData().getId();
        assertNotNull(visitId);

        // Step 4: Login as Nurse Joy
        LoginRequest nurseLogin = new LoginRequest("nurse_joy", "NursePass123!");
        MvcResult nurseLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nurseLogin)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<LoginResponse> nurseAuth = objectMapper.readValue(
                nurseLoginResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, LoginResponse.class));
        String nurseToken = nurseAuth.getData().getToken();

        // Step 5: Triage Patient (ESI Level 2 - Emergent)
        TriageAssessmentDto triageDto = TriageAssessmentDto.builder()
                .visitId(visitId)
                .esiLevel(2)
                .heartRate(104)
                .bloodPressureSys(155)
                .bloodPressureDia(95)
                .respiratoryRate(22)
                .temperatureCelsius(37.4)
                .o2Saturation(95)
                .consciousnessLevel("ALERT")
                .triageNotes("High acuity. Urgent physician evaluation required.")
                .build();

        mockMvc.perform(post("/api/v1/triage")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(triageDto)))
                .andExpect(status().isCreated());

        // Step 6: Query Triage Queue
        mockMvc.perform(get("/api/v1/triage/queue")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // Step 7: Allocate Bed (Bed 1: ED-01)
        BedAllocationDto allocDto = BedAllocationDto.builder()
                .bedId(1L)
                .visitId(visitId)
                .notes("Assigned to ED-01 for immediate telemetry monitoring.")
                .build();

        mockMvc.perform(post("/api/v1/beds/admit")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bedNumber").value("ED-01"));

        // Step 8: Verify Double-Allocation Conflict (Attempting to allocate the same bed throws 409 Conflict)
        mockMvc.perform(post("/api/v1/beds/admit")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allocDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BED_NOT_AVAILABLE"));

        // Step 9: Verify Bed Capacity Summary
        MvcResult summaryResult = mockMvc.perform(get("/api/v1/beds/summary")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<BedCapacitySummaryDto> summaryResp = objectMapper.readValue(
                summaryResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, BedCapacitySummaryDto.class));
        assertTrue(summaryResp.getData().getOccupiedBeds() >= 1);

        // Step 10: Discharge Patient -> Bed transitions to CLEANING
        mockMvc.perform(post("/api/v1/beds/1/discharge")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isOk());

        // Step 11: Mark Bed Cleaned -> Transitions back to AVAILABLE
        mockMvc.perform(post("/api/v1/beds/1/cleaned")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isOk());
    }
}
