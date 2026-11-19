package com.smartstock.smartstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandForecastServiceTest {
    @Mock
    private DemandForecastRepository forecastRepository;
    @Mock
    private UserRepository userRepository;

    private DemandForecastService service;

    @BeforeEach
    void setUp() {
        service = new DemandForecastService(
                forecastRepository, userRepository, new ObjectMapper());
    }

    @Test
    void warehouseManagerForecastsAreScopedToDatabaseAssignment() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(42L);
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(warehouse)
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(forecastRepository.findLatestForecastsByWarehouseId(42L))
                .thenReturn(List.of());

        assertTrue(service.getCurrentForecasts(manager.getEmail()).isEmpty());

        verify(forecastRepository)
                .findLatestForecastsByWarehouseId(42L);
        verify(forecastRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void historyEndpointAlsoUsesTheDatabaseAssignedWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(42L);
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .warehouse(warehouse)
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));
        when(forecastRepository.findByWarehouseIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of());

        assertTrue(service.getForecasts(manager.getEmail()).isEmpty());

        verify(forecastRepository)
                .findByWarehouseIdOrderByCreatedAtDesc(42L);
        verify(forecastRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void unassignedManagerDoesNotReceiveGlobalForecasts() {
        User manager = User.builder()
                .email("manager@example.invalid")
                .role(User.Role.WAREHOUSE_MGR)
                .build();
        when(userRepository.findByEmail(manager.getEmail()))
                .thenReturn(Optional.of(manager));

        assertTrue(service.getForecasts(manager.getEmail()).isEmpty());

        verify(forecastRepository, never()).findAllByOrderByCreatedAtDesc();
    }
}
