package com.dentalcare.api.modules.clinicalrecords.mapper;

import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.ClinicalProfessionalResponse;
import com.dentalcare.api.modules.clinicalrecords.dto.response.PatientClinicalDocumentResponse;
import com.dentalcare.api.modules.clinicalrecords.model.ClinicalDocument;
import com.dentalcare.api.modules.users.model.User;
import org.springframework.stereotype.Component;

@Component
public class ClinicalDocumentMapper {

    public ClinicalProfessionalResponse toProfessionalResponse(User user) {
        if (user == null) {
            return null;
        }
        return new ClinicalProfessionalResponse(user.getId(), user.getFullName());
    }

    public ClinicalDocumentResponse toResponse(ClinicalDocument document) {
        if (document == null) {
            return null;
        }
        return new ClinicalDocumentResponse(
                document.getId(),
                document.getPatient().getId(),
                toProfessionalResponse(document.getAuthor()),
                document.getTitle(),
                document.getType(),
                document.getDescription(),
                document.getDocumentDate(),
                document.getFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.hasFile(),
                document.isPatientVisible(),
                document.getSharedAt(),
                toProfessionalResponse(document.getSharedBy()),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    public PatientClinicalDocumentResponse toPatientResponse(ClinicalDocument document) {
        if (document == null) {
            return null;
        }
        return new PatientClinicalDocumentResponse(
                document.getId(),
                toProfessionalResponse(document.getAuthor()),
                document.getTitle(),
                document.getType(),
                document.getDescription(),
                document.getDocumentDate(),
                document.getFileName(),
                document.getFileSize(),
                document.getContentType(),
                document.hasFile(),
                document.getSharedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
