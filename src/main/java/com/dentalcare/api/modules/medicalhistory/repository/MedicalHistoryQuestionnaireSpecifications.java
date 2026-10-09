package com.dentalcare.api.modules.medicalhistory.repository;

import com.dentalcare.api.modules.medicalhistory.model.MedicalHistoryQuestionnaire;
import com.dentalcare.api.modules.medicalhistory.model.QuestionnaireSource;
import com.dentalcare.api.modules.medicalhistory.model.QuestionnaireStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class MedicalHistoryQuestionnaireSpecifications {
    private MedicalHistoryQuestionnaireSpecifications() {
    }

    public static Specification<MedicalHistoryQuestionnaire> matching(
            UUID patientId, QuestionnaireStatus status, QuestionnaireSource source, Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            var predicates = criteriaBuilder.conjunction();
            if (patientId != null) {
                predicates = criteriaBuilder.and(predicates,
                        criteriaBuilder.equal(root.get("patient").get("id"), patientId));
            }
            if (status != null) {
                predicates = criteriaBuilder.and(predicates, criteriaBuilder.equal(root.get("status"), status));
            }
            if (source != null) {
                predicates = criteriaBuilder.and(predicates, criteriaBuilder.equal(root.get("source"), source));
            }
            if (from != null) {
                predicates = criteriaBuilder.and(predicates, criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates = criteriaBuilder.and(predicates, criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return predicates;
        };
    }
}
