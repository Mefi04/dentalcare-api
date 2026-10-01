package com.dentalcare.api.modules.clinicalrecords.dto.response;

import com.dentalcare.api.modules.clinicalrecords.model.DentitionType;
import com.dentalcare.api.modules.clinicalrecords.model.ToothFinding;

import java.util.List;
import java.util.Map;

public record OdontogramResponse(
        DentitionType dentition,
        Map<String, ToothFinding> teeth,
        List<OdontogramFindingResponse> recentFindings) {
}
