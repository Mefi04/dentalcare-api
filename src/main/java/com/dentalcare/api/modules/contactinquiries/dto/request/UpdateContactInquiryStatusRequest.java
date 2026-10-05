package com.dentalcare.api.modules.contactinquiries.dto.request;

import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateContactInquiryStatusRequest(@NotNull ContactInquiryStatus status) { }
