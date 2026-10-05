package com.dentalcare.api.modules.contactinquiries.dto.request;

import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryReason;
import jakarta.validation.constraints.*;

public record CreateContactInquiryRequest(
        @NotBlank @Size(max=120) String name,
        @NotBlank @Email @Size(max=254) String email,
        @Size(max=30) String phone,
        @NotNull ContactInquiryReason reason,
        @NotBlank @Size(max=4000) String message,
        @AssertTrue(message="Privacy policy acceptance is required") Boolean privacyAccepted) { }
