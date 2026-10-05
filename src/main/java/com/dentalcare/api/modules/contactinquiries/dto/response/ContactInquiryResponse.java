package com.dentalcare.api.modules.contactinquiries.dto.response;
import com.dentalcare.api.modules.contactinquiries.model.*;
import java.time.Instant; import java.util.UUID;
public record ContactInquiryResponse(UUID id,String name,String email,String phone,ContactInquiryReason reason,String message,ContactInquiryStatus status,Instant createdAt,Instant updatedAt) { }
