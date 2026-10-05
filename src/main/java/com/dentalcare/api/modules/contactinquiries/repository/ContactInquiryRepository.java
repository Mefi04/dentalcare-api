package com.dentalcare.api.modules.contactinquiries.repository;

import com.dentalcare.api.modules.contactinquiries.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository public interface ContactInquiryRepository extends JpaRepository<ContactInquiry,UUID> {
    Page<ContactInquiry> findByStatus(ContactInquiryStatus status, Pageable pageable);
}
