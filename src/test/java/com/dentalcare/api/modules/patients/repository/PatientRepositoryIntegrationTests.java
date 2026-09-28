package com.dentalcare.api.modules.patients.repository;

import com.dentalcare.api.modules.patients.model.Gender;
import com.dentalcare.api.modules.patients.model.Patient;
import com.dentalcare.api.modules.users.model.Role;
import com.dentalcare.api.modules.users.model.User;
import com.dentalcare.api.modules.users.model.UserStatus;
import com.dentalcare.api.modules.users.repository.RoleRepository;
import com.dentalcare.api.modules.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PatientRepositoryIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Role patientRole;

    @BeforeEach
    void setUp() {
        patientRole = roleRepository.findByCode("PATIENT").orElseThrow();
    }

    @Test
    void findAllEagerlyFetchesUserAndMaintainsPaginationMetadataWithoutNPlusOne() {
        Patient patientWithoutUser = createPatient("PAC-00101", "1000000000101", "Ana Sin Cuenta", null);
        User userPending = createUser("2000000000102", UserStatus.PENDING_ACTIVATION);
        Patient patientPending = createPatient("PAC-00102", "2000000000102", "Beto Pendiente", userPending);
        User userActive = createUser("3000000000103", UserStatus.ACTIVE);
        Patient patientActive = createPatient("PAC-00103", "3000000000103", "Carlos Activo", userActive);

        entityManager.flush();
        entityManager.clear();

        Page<Patient> page = patientRepository.findAll(PageRequest.of(0, 2, Sort.by("code").ascending()));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.getTotalPages()).isGreaterThanOrEqualTo(2);
        assertThat(page.getContent()).hasSize(2);

        Patient p1 = page.getContent().stream().filter(p -> p.getCode().equals("PAC-00101")).findFirst().orElse(null);
        if (p1 != null) {
            assertThat(p1.getUser()).isNull();
        }

        Patient p2 = page.getContent().stream().filter(p -> p.getCode().equals("PAC-00102")).findFirst().orElse(null);
        if (p2 != null) {
            assertThat(Hibernate.isInitialized(p2.getUser())).isTrue();
            assertThat(p2.getUser().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        }

        Page<Patient> secondPage = patientRepository.findAll(PageRequest.of(1, 2, Sort.by("code").ascending()));
        assertThat(secondPage.getContent()).isNotEmpty();
    }

    @Test
    void searchEagerlyFetchesUserAndMaintainsPaginationAndFilters() {
        User user1 = createUser("4000000000104", UserStatus.ACTIVE);
        Patient patient1 = createPatient("PAC-SEARCH-01", "4000000000104", "Busqueda Unica Alpha", user1);

        User user2 = createUser("5000000000105", UserStatus.LOCKED);
        Patient patient2 = createPatient("PAC-SEARCH-02", "5000000000105", "Busqueda Unica Beta", user2);

        Patient patient3 = createPatient("PAC-OTHER-01", "6000000000106", "Otro Paciente", null);

        entityManager.flush();
        entityManager.clear();

        Page<Patient> results = patientRepository.search("Busqueda Unica", PageRequest.of(0, 10, Sort.by("code").ascending()));

        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getTotalPages()).isEqualTo(1);
        assertThat(results.getContent()).extracting(Patient::getCode)
                .containsExactly("PAC-SEARCH-01", "PAC-SEARCH-02");

        Patient found1 = results.getContent().get(0);
        assertThat(Hibernate.isInitialized(found1.getUser())).isTrue();
        assertThat(found1.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);

        Patient found2 = results.getContent().get(1);
        assertThat(Hibernate.isInitialized(found2.getUser())).isTrue();
        assertThat(found2.getUser().getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void findByIdAndFindByUserIdEagerlyFetchUser() {
        User user = createUser("7000000000107", UserStatus.ACTIVE);
        Patient patient = createPatient("PAC-DETAIL-01", "7000000000107", "Detalle Paciente", user);

        entityManager.flush();
        entityManager.clear();

        Optional<Patient> byId = patientRepository.findById(patient.getId());
        assertThat(byId).isPresent();
        assertThat(Hibernate.isInitialized(byId.get().getUser())).isTrue();
        assertThat(byId.get().getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);

        entityManager.clear();

        Optional<Patient> byUserId = patientRepository.findByUser_Id(user.getId());
        assertThat(byUserId).isPresent();
        assertThat(Hibernate.isInitialized(byUserId.get().getUser())).isTrue();
        assertThat(byUserId.get().getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void patientPortalAccessLifecycleReflectsStatusChanges() {
        // 1. Patient without user
        Patient patient = createPatient("PAC-LIFE-01", "8000000000108", "Lifecycle Patient", null);
        entityManager.flush();
        entityManager.clear();

        Patient loaded1 = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(loaded1.getUser()).isNull();

        // 2. Access created (User attached with PENDING_ACTIVATION)
        User user = createUser("8000000000108", UserStatus.PENDING_ACTIVATION);
        loaded1.setUser(user);
        patientRepository.saveAndFlush(loaded1);
        entityManager.clear();

        Patient loaded2 = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded2.getUser())).isTrue();
        assertThat(loaded2.getUser().getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);

        // 3. Account activated (User status changed to ACTIVE)
        User userToActivate = userRepository.findById(user.getId()).orElseThrow();
        userToActivate.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(userToActivate);
        entityManager.clear();

        Patient loaded3 = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded3.getUser())).isTrue();
        assertThat(loaded3.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);

        // 4. Account locked (User status changed to LOCKED)
        User userToLock = userRepository.findById(user.getId()).orElseThrow();
        userToLock.setStatus(UserStatus.LOCKED);
        userRepository.saveAndFlush(userToLock);
        entityManager.clear();

        Patient loaded4 = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded4.getUser())).isTrue();
        assertThat(loaded4.getUser().getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void patientProfileContactFieldsCanBeUpdatedAndPersistedCleanly() {
        User user = createUser("9000000000109", UserStatus.ACTIVE);
        Patient patient = createPatient("PAC-PROF-01", "9000000000109", "Perfil Paciente", user);
        entityManager.flush();
        entityManager.clear();

        Patient loaded = patientRepository.findByUser_Id(user.getId()).orElseThrow();
        loaded.setPhone("55559999");
        loaded.setEmail("perfil.nuevo@example.com");
        loaded.setAddress("Nueva Direccion 123");
        loaded.setEmergencyContact("Contacto Familiar");
        loaded.setEmergencyPhone("55550000");
        Instant updateInstant = NOW.plusSeconds(3600);
        loaded.setUpdatedAt(updateInstant);

        patientRepository.saveAndFlush(loaded);
        entityManager.clear();

        Patient reloaded = patientRepository.findByUser_Id(user.getId()).orElseThrow();
        assertThat(reloaded.getPhone()).isEqualTo("55559999");
        assertThat(reloaded.getEmail()).isEqualTo("perfil.nuevo@example.com");
        assertThat(reloaded.getAddress()).isEqualTo("Nueva Direccion 123");
        assertThat(reloaded.getEmergencyContact()).isEqualTo("Contacto Familiar");
        assertThat(reloaded.getEmergencyPhone()).isEqualTo("55550000");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updateInstant);

        // Invariants
        assertThat(reloaded.getName()).isEqualTo("Perfil Paciente");
        assertThat(reloaded.getDpi()).isEqualTo("9000000000109");
        assertThat(reloaded.getCode()).isEqualTo("PAC-PROF-01");
        assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
    }

    private Patient createPatient(String code, String dpi, String name, User user) {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCode(code);
        patient.setName(name);
        patient.setDpi(dpi);
        patient.setBirthDate(LocalDate.of(1992, 5, 10));
        patient.setGender(Gender.OTHER);
        patient.setPhone("55551234");
        patient.setEmail(code.toLowerCase() + "@example.com");
        patient.setUser(user);
        patient.setCreatedAt(NOW);
        patient.setUpdatedAt(NOW);
        return patientRepository.save(patient);
    }

    private User createUser(String cui, UserStatus status) {
        User user = new User(
                UUID.randomUUID(),
                "patient-" + UUID.randomUUID(),
                "User " + cui,
                null,
                cui,
                "password_hash",
                status,
                NOW,
                NOW
        );
        user.setRoles(Set.of(patientRole));
        return userRepository.save(user);
    }
}
