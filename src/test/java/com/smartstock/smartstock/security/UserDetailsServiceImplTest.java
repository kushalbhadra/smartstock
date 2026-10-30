package com.smartstock.smartstock.security;

import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsUserByTrimmedCaseInsensitiveEmail() {
        User user = User.builder()
                .email("sneha@smartstock.com")
                .password("encoded-password")
                .role(User.Role.WAREHOUSE_MGR)
                .build();
        when(userRepository.findByEmailIgnoreCase("sneha@smartstock.com"))
                .thenReturn(Optional.of(user));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "userRepository", userRepository);

        UserDetails userDetails = service.loadUserByUsername(
                "  Sneha@SmartStock.com  ");

        verify(userRepository).findByEmailIgnoreCase(
                "sneha@smartstock.com");
        assertEquals("sneha@smartstock.com", userDetails.getUsername());
        assertEquals("encoded-password", userDetails.getPassword());
        assertEquals("ROLE_WAREHOUSE_MGR",
                userDetails.getAuthorities().iterator().next().getAuthority());
    }
}
