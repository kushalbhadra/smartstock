package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.SystemSettingsRequest;
import com.smartstock.smartstock.dto.SystemSettingsResponse;
import com.smartstock.smartstock.entity.SystemSettings;
import com.smartstock.smartstock.repository.SystemSettingsRepository;
import com.smartstock.smartstock.validation.ContactDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SystemSettingsService {

    private static final long SETTINGS_ID = 1L;
    private final SystemSettingsRepository settingsRepository;

    public SystemSettingsService(
            SystemSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public SystemSettingsResponse get() {
        return toResponse(getOrCreate());
    }

    public SystemSettingsResponse update(SystemSettingsRequest request) {
        SystemSettings settings = getOrCreate();
        settings.setCompanyName(request.getCompanyName().trim());
        settings.setContactEmail(ContactDetails.normalizeEmail(
                request.getContactEmail()));
        settings.setContactPhone(ContactDetails.normalizePhone(
                request.getContactPhone()));
        settings.setDefaultLowStockThreshold(
                request.getDefaultLowStockThreshold());
        return toResponse(settingsRepository.save(settings));
    }

    public int getDefaultLowStockThreshold() {
        return getOrCreate().getDefaultLowStockThreshold();
    }

    private SystemSettings getOrCreate() {
        return settingsRepository.findById(SETTINGS_ID)
                .orElseGet(() -> settingsRepository.save(
                        SystemSettings.builder()
                                .id(SETTINGS_ID)
                                .build()));
    }

    private SystemSettingsResponse toResponse(SystemSettings settings) {
        return SystemSettingsResponse.builder()
                .companyName(settings.getCompanyName())
                .contactEmail(settings.getContactEmail())
                .contactPhone(settings.getContactPhone())
                .defaultLowStockThreshold(
                        settings.getDefaultLowStockThreshold())
                .build();
    }
}
