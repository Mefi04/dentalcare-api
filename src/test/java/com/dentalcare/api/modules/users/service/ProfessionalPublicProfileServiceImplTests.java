package com.dentalcare.api.modules.users.service;

import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.modules.users.dto.request.UpsertProfessionalPublicProfileRequest;
import com.dentalcare.api.modules.users.mapper.ProfessionalPublicProfileMapper;
import com.dentalcare.api.modules.users.model.*;
import com.dentalcare.api.modules.users.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProfessionalPublicProfileServiceImplTests {
    @Test void rejectsAUserWithoutTheDentistRole() {
        var profiles=mock(ProfessionalPublicProfileRepository.class); var users=mock(UserRepository.class);
        var service=new ProfessionalPublicProfileServiceImpl(profiles,users,new ProfessionalPublicProfileMapper(),Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        UUID userId=UUID.randomUUID(), actor=UUID.randomUUID(); var user=new User(); user.setId(userId); user.setRoles(Set.of(new Role(UUID.randomUUID(),"SECRETARY","Secretary",null,true)));
        when(users.existsById(actor)).thenReturn(true); when(users.findWithRolesById(userId)).thenReturn(Optional.of(user));
        assertThatThrownBy(()->service.upsert(userId,request(),actor)).isInstanceOf(ConflictException.class).hasMessageContaining("DENTIST");
        verifyNoInteractions(profiles);
    }
    @Test void rejectsAnUnknownUser() {
        var profiles=mock(ProfessionalPublicProfileRepository.class); var users=mock(UserRepository.class);
        var service=new ProfessionalPublicProfileServiceImpl(profiles,users,new ProfessionalPublicProfileMapper(),Clock.systemUTC()); UUID actor=UUID.randomUUID();
        when(users.existsById(actor)).thenReturn(true); when(users.findWithRolesById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.upsert(UUID.randomUUID(),request(),actor)).isInstanceOf(com.dentalcare.api.exception.ResourceNotFoundException.class);
    }
    private UpsertProfessionalPublicProfileRequest request(){return new UpsertProfessionalPublicProfileRequest("COL-1","Ortodoncia","Bio",8,"Español","https://cdn.test/photo.jpg",true);}
}
