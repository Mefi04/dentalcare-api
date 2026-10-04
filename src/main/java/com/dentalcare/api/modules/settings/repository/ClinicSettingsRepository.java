package com.dentalcare.api.modules.settings.repository;

import com.dentalcare.api.modules.settings.model.ClinicSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, Short> {}
