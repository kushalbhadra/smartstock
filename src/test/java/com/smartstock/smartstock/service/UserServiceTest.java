package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.ChangePasswordRequest;
import com.smartstock.smartstock.dto.RegistrationRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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
    void updateNormalizesIndianPhoneNumber() {
        User user = User.builder()
                .id(5L)
                .name("Sneha")
                .email("sneha@smartstock.com")
                .password("encoded-password")
                .role(User.Role.VIEWER)
                .build();
        UserRequest request = new UserRequest();
        request.setName("Sneha");
        request.setEmail(user.getEmail());
        request.setRole(User.Role.VIEWER);
        request.setPhone("+919876543210");

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.update(5L, request);

        assertEquals("9876543210", user.getPhone());
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

    @Test
    void publicRegistrationCreatesViewerAndEncodesPassword() {
        RegistrationRequest request = new RegistrationRequest();
        request.setName(" New Viewer ");
        request.setEmail("New.Viewer@example.invalid");
        request.setPassword("StrongPass1");
        when(userRepository.existsByEmailIgnoreCase(
                "new.viewer@example.invalid")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass1"))
                .thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.register(request);

        assertEquals(User.Role.VIEWER, response.getRole());
        assertEquals("new.viewer@example.invalid", response.getEmail());
        verify(passwordEncoder).encode("StrongPass1");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void publicRegistrationRejectsDuplicateEmail() {
        RegistrationRequest request = new RegistrationRequest();
        request.setName("Viewer");
        request.setEmail("existing@example.invalid");
        request.setPassword("StrongPass1");
        when(userRepository.existsByEmailIgnoreCase(
                "existing@example.invalid")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> userService.register(request));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changePasswordVerifiesAndEncodesTheNewPassword() {
        User user = User.builder()
                .id(5L)
                .name("Viewer")
                .email("viewer@example.invalid")
                .password("$2a$old")
                .role(User.Role.VIEWER)
                .build();
        ChangePasswordRequest request = changePasswordRequest();
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPass1", "$2a$old"))
                .thenReturn(true);
        when(passwordEncoder.matches("NewPassword2", "$2a$old"))
                .thenReturn(false);
        when(passwordEncoder.encode("NewPassword2"))
                .thenReturn("$2a$new");
        when(userRepository.save(user)).thenReturn(user);

        userService.changePassword(user.getEmail(), request);

        assertEquals("$2a$new", user.getPassword());
        verify(passwordEncoder).encode("NewPassword2");
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        User user = User.builder()
                .email("viewer@example.invalid")
                .password("$2a$old")
                .role(User.Role.VIEWER)
                .build();
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CurrentPass1", "$2a$old"))
                .thenReturn(false);

        assertThrows(BusinessException.class,
                () -> userService.changePassword(
                        user.getEmail(), changePasswordRequest()));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsConfirmationMismatch() {
        ChangePasswordRequest request = changePasswordRequest();
        request.setConfirmPassword("DifferentPass3");

        assertThrows(BusinessException.class,
                () -> userService.changePassword(
                        "viewer@example.invalid", request));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    private ChangePasswordRequest changePasswordRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("CurrentPass1");
        request.setNewPassword("NewPassword2");
        request.setConfirmPassword("NewPassword2");
        return request;
    }
}
