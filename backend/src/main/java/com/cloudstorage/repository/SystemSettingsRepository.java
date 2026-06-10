package com.cloudstorage.repository;

import com.cloudstorage.model.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
}
