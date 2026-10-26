package com.helixflow.modules.resource.medication.service;

import com.helixflow.modules.resource.medication.dto.*;
import java.util.List;

public interface MedicationService {

    List<MedicationDto> searchMedications(String query);

    List<MedicationOrderDto> getActiveOrdersForVisit(Long visitId);

    List<MedicationOrderDto> getOrdersForVisit(Long visitId);

    SafetyCheckResultDto evaluateSafety(MedicationSafetyCheckRequest request);

    MedicationOrderDto prescribeMedication(MedicationPrescribeRequest request);

    MedicationOrderDto prescribeMedication(PrescriptionRequestDto request);

    MedicationOrderDto discontinueMedicationOrder(Long orderId, String reason);
}
