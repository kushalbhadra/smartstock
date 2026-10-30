package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.UserRequest;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.repository.AlertLogRepository;
import com.smartstock.smartstock.repository.NotificationRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.SupportTicketRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private AlertLogRepository alertLogRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                warehouseRepository,
                passwordEncoder,
                purchaseOrderRepository,
                stockMovementRepository,
                alertLogRepository,
                notificationRepository,
                supportTicketRepository);
    }

    @Test
    void updateEncodesProvidedPassword() {
        User user = User.builder()
                .id(5L)
                .name("Sneha")
                .email("sneha@smartstock.com")
                .password("old-encoded-password")
                .role(User.Role.VIEWER)
                .build();
        UserRequest request = new UserRequest();
        request.setName("Sneha");
        request.setEmail(user.getEmail());
        request.setRole(User.Role.VIEWER);
        request.setPassword("new-password");

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password"))
                .thenReturn("new-encoded-password");
        when(userRepository.save(user)).thenReturn(user);

        userService.update(5L, request);

        assertEquals("new-encoded-password", user.getPassword());
        verify(passwordEncoder).encode("new-password");
        verify(userRepository).save(user);
    }

    @Test
    void updateRejectsShortPassword() {
        User user = User.builder()
                .id(5L)
                .name("Sneha")
                .email("sneha@smartstock.com")
                .password("old-encoded-password")
                .role(User.Role.VIEWER)
                .build();
        UserRequest request = new UserRequest();
        request.setName("Sneha");
        request.setEmail(user.getEmail());
        request.setRole(User.Role.VIEWER);
        request.setPassword("short");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class,
                () -> userService.update(5L, request));
        verifyNoInteractions(passwordEncoder);
    }
}
