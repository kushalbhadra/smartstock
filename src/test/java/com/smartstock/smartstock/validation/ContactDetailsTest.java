package com.smartstock.smartstock.validation;

import com.smartstock.smartstock.dto.LoginRequest;
import com.smartstock.smartstock.dto.SupplierRequest;
import com.smartstock.smartstock.dto.UserProfileRequest;
import com.smartstock.smartstock.dto.UserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactDetailsTest {

    @Test
    void normalizesPhoneNumbersToTenDigitIndianMobileNumbers() {
        assertEquals("9876543210",
                ContactDetails.normalizePhone("+919876543210"));
        assertEquals("9876543210",
                ContactDetails.normalizePhone("9876543210"));
        assertNull(ContactDetails.normalizePhone(""));
    }

    @Test
    void validatesOptionalPhoneFieldsAcrossRequestTypes() {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            UserRequest user = new UserRequest();
            user.setName("A User");
            user.setEmail("user@example.com");
            user.setPhone("+919876543210");
            assertTrue(validator.validate(user).isEmpty());

            UserProfileRequest profile = new UserProfileRequest();
            profile.setName("A User");
            profile.setPhone("9876543210");
            assertTrue(validator.validate(profile).isEmpty());

            SupplierRequest supplier = new SupplierRequest();
            supplier.setName("A Supplier");
            supplier.setPhone("9876543210");
            assertTrue(validator.validate(supplier).isEmpty());

            profile.setPhone("+91-9876543210");
            assertFalse(validator.validate(profile).isEmpty());
        }
    }

    @Test
    void trimsAndLowercasesEmailAndRejectsInvalidAddresses() {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            UserRequest user = new UserRequest();
            user.setName("A User");
            user.setEmail("  USER@Example.COM  ");
            assertEquals("user@example.com", user.getEmail());
            assertTrue(validator.validate(user).isEmpty());

            LoginRequest login = new LoginRequest();
            login.setEmail("user@localhost");
            login.setPassword("password");
            assertFalse(validator.validate(login).isEmpty());

            SupplierRequest supplier = new SupplierRequest();
            supplier.setName("A Supplier");
            supplier.setContactEmail("  SUPPLIER@EXAMPLE.COM  ");
            assertEquals("supplier@example.com",
                    supplier.getContactEmail());
            assertTrue(validator.validate(supplier).isEmpty());
        }
    }
}
