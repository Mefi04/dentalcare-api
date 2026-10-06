package com.dentalcare.api.modules.contactinquiries.mapper;
import com.dentalcare.api.modules.contactinquiries.dto.response.ContactInquiryResponse;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiry;
import org.springframework.stereotype.Component;
@Component public class ContactInquiryMapper { public ContactInquiryResponse toResponse(ContactInquiry value){return new ContactInquiryResponse(value.getId(),value.getName(),value.getEmail(),value.getPhone(),value.getReason(),value.getMessage(),value.getStatus(),value.getCreatedAt(),value.getUpdatedAt());} }
