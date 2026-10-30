package com.smartstock.smartstock.config;

import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsInitialAdminWithEncodedPassword() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(0L);
        when(passwordEncoder.encode("long-enough-password"))
                .thenReturn("encoded-password");
        when(userRepository.existsByEmailIgnoreCase("admin@example.com"))
                .thenReturn(false);

        DataInitializer initializer = initializer(
                "Workspace Admin",
                " Admin@Example.com ",
                "long-enough-password");
        initializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User user = userCaptor.getValue();
        assertEquals("Workspace Admin", user.getName());
        assertEquals("admin@example.com", user.getEmail());
        assertEquals("encoded-password", user.getPassword());
        assertEquals(User.Role.ADMIN, user.getRole());
    }

    @Test
    void doesNotResetExistingAdministrator() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(1L);

        initializer("", "", "").run();

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void requiresAllBootstrapValuesTogether() {
        DataInitializer initializer = initializer(
                "Workspace Admin", "", "");

        assertThrows(IllegalStateException.class, initializer::run);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void requiresStrongInitialPassword() {
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(0L);
        DataInitializer initializer = initializer(
                "Workspace Admin",
                "admin@example.com",
                "short-pass");

        assertThrows(IllegalStateException.class, initializer::run);
        verify(userRepository, never()).save(any(User.class));
    }

    private DataInitializer initializer(
            String name, String email, String password) {
        return new DataInitializer(
                userRepository, passwordEncoder, name, email, password);
    }
}
