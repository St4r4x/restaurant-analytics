package com.aflokkat.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationArguments args;

    @InjectMocks
    private DataSeeder dataSeeder;

    @Test
    void run_createsCustomerAndController_whenAbsent() throws Exception {
        // Neither account exists yet
        when(userRepository.findByUsername("customer_test")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("controller_test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Test1234!")).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        dataSeeder.run(args);

        // save() must be called twice
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository, times(2)).save(captor.capture());

        // First saved entity: ROLE_CUSTOMER
        UserEntity customer = captor.getAllValues().get(0);
        assertEquals("customer_test", customer.getUsername());
        assertEquals("ROLE_CUSTOMER", customer.getRole());

        // Second saved entity: ROLE_CONTROLLER
        UserEntity controller = captor.getAllValues().get(1);
        assertEquals("controller_test", controller.getUsername());
        assertEquals("ROLE_CONTROLLER", controller.getRole());
    }

    @Test
    void run_skipsExisting_whenAlreadySeeded() throws Exception {
        // Both accounts already exist
        when(userRepository.findByUsername("customer_test"))
                .thenReturn(Optional.of(new UserEntity("customer_test", "customer@test.com", "hash", "ROLE_CUSTOMER")));
        when(userRepository.findByUsername("controller_test"))
                .thenReturn(Optional.of(new UserEntity("controller_test", "controller@test.com", "hash", "ROLE_CONTROLLER")));

        dataSeeder.run(args);

        // save() must never be called
        verify(userRepository, never()).save(any());
    }
}
