package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerEncodesPasswordAndSavesNewUser() {
        when(userRepository.existsByUsername("ana")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.register("ana", "ana@example.test", "secret");

        assertThat(user.getUsername()).isEqualTo("ana");
        assertThat(user.getEmail()).isEqualTo("ana@example.test");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-secret");
        assertThat(user.getIcalToken()).isNotBlank();
        verify(passwordEncoder).encode("secret");
        verify(userRepository).save(user);
    }

    @Test
    void registerRejectsDuplicateUsernameBeforeEncodingPassword() {
        when(userRepository.existsByUsername("ana")).thenReturn(true);

        assertThatThrownBy(() -> service.register("ana", "ana@example.test", "secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Username already taken");

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requireByUsernameReturnsExistingUser() {
        User user = new User("ana", "ana@example.test", "hash");
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(user));

        assertThat(service.requireByUsername("ana")).isSameAs(user);
    }

    @Test
    void requireByUsernameRejectsUnknownUser() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireByUsername("missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown user: missing");
    }
}
