package com.dentalcare.api.shared.backup;

import com.dentalcare.api.modules.clinicalrecords.mapper.ClinicalDocumentMapper;
import com.dentalcare.api.modules.clinicalrecords.repository.ClinicalDocumentRepository;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentFileValidator;
import com.dentalcare.api.modules.clinicalrecords.service.ClinicalDocumentServiceImpl;
import com.dentalcare.api.modules.clinicalrecords.storage.ClinicalDocumentStorage;
import com.dentalcare.api.modules.patients.repository.PatientRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Characterizes the existing risk; deliberately does NOT fix or endorse UNKNOWN deletion. */
class ClinicalDocumentUnknownOutcomeCharacterizationTests {
    @ParameterizedTest
    @ValueSource(ints = {TransactionSynchronization.STATUS_COMMITTED,
            TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_UNKNOWN})
    void documentsCurrentCleanupDecisionWithoutAccessingStorage(int outcome) {
        ClinicalDocumentStorage storage = mock(ClinicalDocumentStorage.class);
        var service = new ClinicalDocumentServiceImpl(mock(ClinicalDocumentRepository.class),
                mock(PatientRepository.class), mock(UserRepository.class), mock(ClinicalDocumentMapper.class),
                storage, mock(ClinicalDocumentFileValidator.class), Clock.systemUTC());
        String syntheticKey = "patients/synthetic/documents/synthetic.pdf";
        TransactionSynchronizationManager.initSynchronization();
        try {
            Boolean registered = ReflectionTestUtils.invokeMethod(service, "registerRollbackCleanup", syntheticKey);
            assertThat(registered).isTrue();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(callback -> callback.afterCompletion(outcome));
            if (outcome == TransactionSynchronization.STATUS_COMMITTED) {
                verify(storage, never()).delete(syntheticKey);
            } else {
                // STATUS_UNKNOWN currently deletes too: this is the uncorrected finding.
                verify(storage).delete(syntheticKey);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
