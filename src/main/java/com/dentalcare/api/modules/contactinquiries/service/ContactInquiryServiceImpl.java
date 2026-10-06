package com.dentalcare.api.modules.contactinquiries.service;
import com.dentalcare.api.exception.*;
import com.dentalcare.api.modules.contactinquiries.dto.request.*;
import com.dentalcare.api.modules.contactinquiries.dto.response.*;
import com.dentalcare.api.modules.contactinquiries.mapper.ContactInquiryMapper;
import com.dentalcare.api.modules.contactinquiries.model.*;
import com.dentalcare.api.modules.contactinquiries.repository.ContactInquiryRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock; import java.util.Locale; import java.util.UUID;

@Service public class ContactInquiryServiceImpl implements ContactInquiryService {
    private static final int MAX_PAGE_SIZE=100; private static final Sort DEFAULT_SORT=Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"));
    private final ContactInquiryRepository repository; private final ContactInquiryMapper mapper; private final UserRepository users; private final Clock clock;
    public ContactInquiryServiceImpl(ContactInquiryRepository repository,ContactInquiryMapper mapper,UserRepository users,Clock clock){this.repository=repository;this.mapper=mapper;this.users=users;this.clock=clock;}
    @Override @Transactional public ContactInquiryAcknowledgement create(CreateContactInquiryRequest request){
        if(request==null)throw new BadRequestException("Contact inquiry is required");
        if(request.reason()==null)throw new BadRequestException("Reason is required"); var now=clock.instant();
        var inquiry=new ContactInquiry(UUID.randomUUID(),required(request.name(),"Name is required"),required(request.email(),"Email is required").toLowerCase(Locale.ROOT),optional(request.phone()),request.reason(),required(request.message(),"Message is required"),Boolean.TRUE.equals(request.privacyAccepted()),now);
        if(!inquiry.isPrivacyAccepted())throw new BadRequestException("Privacy policy acceptance is required"); repository.save(inquiry);
        return new ContactInquiryAcknowledgement(true,"Your general inquiry was received. This does not create an appointment and is not an emergency channel.");
    }
    @Override @Transactional(readOnly=true) public Page<ContactInquiryResponse> findAll(ContactInquiryStatus status,int page,int size){validatePaging(page,size);var pageable=PageRequest.of(page,Math.min(size,MAX_PAGE_SIZE),DEFAULT_SORT);return (status==null?repository.findAll(pageable):repository.findByStatus(status,pageable)).map(mapper::toResponse);}
    @Override @Transactional(readOnly=true) public ContactInquiryResponse findById(UUID id){return mapper.toResponse(find(id));}
    @Override @Transactional public ContactInquiryResponse updateStatus(UUID id,UpdateContactInquiryStatusRequest request,UUID actorId){if(request==null||request.status()==null)throw new BadRequestException("Contact inquiry status is required");if(actorId==null||!users.existsById(actorId))throw new BadRequestException("Authenticated actor is invalid");var inquiry=find(id);inquiry.updateStatus(request.status(),actorId,clock.instant());return mapper.toResponse(repository.save(inquiry));}
    private ContactInquiry find(UUID id){if(id==null)throw new BadRequestException("Contact inquiry id is required");return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Contact inquiry not found"));}
    private void validatePaging(int page,int size){if(page<0)throw new BadRequestException("Page must not be negative");if(size<1)throw new BadRequestException("Size must be at least 1");}
    private String required(String value,String message){String normalized=optional(value);if(normalized==null)throw new BadRequestException(message);return normalized;}
    private String optional(String value){if(value==null)return null;String normalized=value.trim();return normalized.isEmpty()?null:normalized;}
}
