package com.dentalcare.api.modules.contactinquiries.service;
import com.dentalcare.api.modules.contactinquiries.dto.request.*;
import com.dentalcare.api.modules.contactinquiries.dto.response.*;
import com.dentalcare.api.modules.contactinquiries.model.ContactInquiryStatus;
import org.springframework.data.domain.Page;
import java.util.UUID;
public interface ContactInquiryService { ContactInquiryAcknowledgement create(CreateContactInquiryRequest request); Page<ContactInquiryResponse> findAll(ContactInquiryStatus status,int page,int size); ContactInquiryResponse findById(UUID id); ContactInquiryResponse updateStatus(UUID id,UpdateContactInquiryStatusRequest request,UUID actorId); }
