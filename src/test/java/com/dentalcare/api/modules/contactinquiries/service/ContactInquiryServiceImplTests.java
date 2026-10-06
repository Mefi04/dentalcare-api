package com.dentalcare.api.modules.contactinquiries.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.modules.contactinquiries.dto.request.CreateContactInquiryRequest;
import com.dentalcare.api.modules.contactinquiries.mapper.ContactInquiryMapper;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiry;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryReason;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryStatus;
import com.dentalcare.api.modules.contactinquiries.repository.ContactInquiryRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContactInquiryServiceImplTests {

    @Test
    @DisplayName("creates new inquiry with backend identity, timestamp and status NEW")
    void createsNewInquiryWithBackendIdentityTimestampAndStatus() {
        var repo = mock(ContactInquiryRepository.class);
        var users = mock(UserRepository.class);
        var service = new ContactInquiryServiceImpl(repo, new ContactInquiryMapper(), users,
                Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request("<script>x</script>", ContactInquiryReason.SERVICES));

        assertThat(result.received()).isTrue();
        var cap = ArgumentCaptor.forClass(ContactInquiry.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ContactInquiryStatus.NEW);
        assertThat(cap.getValue().getId()).isNotNull();
        assertThat(cap.getValue().getCreatedAt()).isEqualTo(Instant.parse("2026-10-05T00:00:00Z"));
        assertThat(cap.getValue().getMessage()).isEqualTo("<script>x</script>");
        assertThat(cap.getValue().getReason()).isEqualTo(ContactInquiryReason.SERVICES);

        // Verify no user, appointment, or external entity is created
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @EnumSource(ContactInquiryReason.class)
    @DisplayName("accepts all 7 valid reasons, sets status NEW, and does not create auxiliary entities")
    void acceptsAllValidReasonsWithoutSideEffects(ContactInquiryReason reason) {
        var repo = mock(ContactInquiryRepository.class);
        var users = mock(UserRepository.class);
        var service = new ContactInquiryServiceImpl(repo, new ContactInquiryMapper(), users,
                Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request("Consulta para " + reason.name(), reason));

        assertThat(result.received()).isTrue();
        var cap = ArgumentCaptor.forClass(ContactInquiry.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ContactInquiryStatus.NEW);
        assertThat(cap.getValue().getReason()).isEqualTo(reason);

        // Crucial requirement: APPOINTMENT_HELP does not create appointments, ACCOUNT_ACTIVATION does not activate users, etc.
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("rejects null reason with BadRequestException")
    void rejectsNullReason() {
        var service = new ContactInquiryServiceImpl(mock(ContactInquiryRepository.class),
                new ContactInquiryMapper(), mock(UserRepository.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.create(new CreateContactInquiryRequest(
                "Ana", "ana@test.com", null, null, "Hola", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Reason is required");
    }

    @Test
    @DisplayName("rejects inquiry when privacy policy is not accepted")
    void rejectsPrivacyNotAccepted() {
        var service = new ContactInquiryServiceImpl(mock(ContactInquiryRepository.class),
                new ContactInquiryMapper(), mock(UserRepository.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.create(new CreateContactInquiryRequest(
                "Ana", "ana@test.com", null, ContactInquiryReason.GENERAL, "Hola", false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Privacy policy acceptance is required");
    }

    private CreateContactInquiryRequest request(String message, ContactInquiryReason reason) {
        return new CreateContactInquiryRequest(" Ana ", "ANA@TEST.COM", " 5555-1234 ",
                reason, message, true);
    }
}
