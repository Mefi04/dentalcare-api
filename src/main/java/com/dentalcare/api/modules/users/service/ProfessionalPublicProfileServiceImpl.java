package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.users.dto.request.UpsertProfessionalPublicProfileRequest;
import com.dentalcare.api.modules.users.dto.response.ProfessionalPublicProfileResponse;
import com.dentalcare.api.modules.users.mapper.ProfessionalPublicProfileMapper;
import com.dentalcare.api.modules.users.model.ProfessionalPublicProfile;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.repository.ProfessionalPublicProfileRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ProfessionalPublicProfileServiceImpl implements ProfessionalPublicProfileService {
    private final ProfessionalPublicProfileRepository repository;
    private final UserRepository userRepository;
    private final ProfessionalPublicProfileMapper mapper;
    private final Clock clock;
    public ProfessionalPublicProfileServiceImpl(ProfessionalPublicProfileRepository repository, UserRepository userRepository, ProfessionalPublicProfileMapper mapper, Clock clock) {
        this.repository=repository; this.userRepository=userRepository; this.mapper=mapper; this.clock=clock;
    }
    @Override @Transactional(readOnly=true) public ProfessionalPublicProfileResponse findByUserId(UUID userId) {
        return mapper.toResponse(repository.findByUser_Id(userId).orElseThrow(()->new ResourceNotFoundException("Professional public profile not found")));
    }
    @Override @Transactional public ProfessionalPublicProfileResponse upsert(UUID userId, UpsertProfessionalPublicProfileRequest request, UUID actorId) {
        requireActor(actorId); User user=findDentist(userId); var now=clock.instant();
        var existing=repository.findByUser_Id(userId);
        ProfessionalPublicProfile profile=existing.orElseGet(()->new ProfessionalPublicProfile(UUID.randomUUID(),user,required(request.professionalRegistration()),required(request.specialty()),required(request.summary()),request.yearsExperience(),optional(request.languages()),optional(request.photoUrl()),request.publicVisible(),actorId,actorId,now,now));
        if(existing.isPresent()) { profile.setProfessionalRegistration(required(request.professionalRegistration())); profile.setSpecialty(required(request.specialty())); profile.setSummary(required(request.summary())); profile.setYearsExperience(request.yearsExperience()); profile.setLanguages(optional(request.languages())); profile.setPhotoUrl(optional(request.photoUrl())); profile.setPublicVisible(request.publicVisible()); profile.setUpdatedBy(actorId); profile.setUpdatedAt(now); }
        if (request.serviceCode() != null) profile.setServiceCode(request.serviceCode());
        return save(profile);
    }
    @Override @Transactional(readOnly=true) public List<ProfessionalPublicProfileResponse> findPubliclyVisible(){return repository.findAllPubliclyVisible().stream().map(mapper::toResponse).toList();}
    @Override @Transactional(readOnly=true) public ProfessionalPublicProfileResponse findPubliclyVisibleById(UUID profileId){return mapper.toResponse(repository.findPubliclyVisibleById(profileId).orElseThrow(()->new ResourceNotFoundException("Public professional not found")));}
    private User findDentist(UUID userId){if(userId==null)throw new BadRequestException("User id is required"); User user=userRepository.findWithRolesById(userId).orElseThrow(()->new ResourceNotFoundException("User not found")); if(user.getRoles().stream().noneMatch(role->"DENTIST".equals(role.getCode())&&role.isActive()))throw new ConflictException("User must have the active DENTIST role"); return user;}
    private void requireActor(UUID actorId){if(actorId==null||!userRepository.existsById(actorId))throw new BadRequestException("Authenticated actor is invalid");}
    private ProfessionalPublicProfileResponse save(ProfessionalPublicProfile profile){try{return mapper.toResponse(repository.saveAndFlush(profile));}catch(DataIntegrityViolationException ex){throw new ConflictException("Professional public profile already exists");}}
    private String required(String value){String normalized=optional(value);if(normalized==null)throw new BadRequestException("Profile value is required");return normalized;}
    private String optional(String value){if(value==null)return null;String normalized=value.trim();return normalized.isEmpty()?null:normalized;}
}
