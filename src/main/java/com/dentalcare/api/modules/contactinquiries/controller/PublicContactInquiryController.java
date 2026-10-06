package com.dentalcare.api.modules.contactinquiries.controller;
import com.dentalcare.api.modules.contactinquiries.dto.request.CreateContactInquiryRequest;
import com.dentalcare.api.modules.contactinquiries.dto.response.ContactInquiryAcknowledgement;
import com.dentalcare.api.modules.contactinquiries.service.ContactInquiryService;
import io.swagger.v3.oas.annotations.Operation; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/public/contact-inquiries") public class PublicContactInquiryController { private final ContactInquiryService service; public PublicContactInquiryController(ContactInquiryService service){this.service=service;} @PostMapping @Operation(summary="Submit a general inquiry",description="Does not create appointments and is not an emergency channel.") public ResponseEntity<ContactInquiryAcknowledgement> create(@Valid @RequestBody CreateContactInquiryRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));} }
