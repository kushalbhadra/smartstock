package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.validation.ContactDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Email
    @Pattern(regexp = ContactDetails.EMAIL_PATTERN,
            message = ContactDetails.EMAIL_MESSAGE)
    @Size(max = 150)
    private String email;

    @Pattern(regexp = ContactDetails.PHONE_PATTERN,
            message = ContactDetails.PHONE_MESSAGE)
    @Size(max = 13, message = ContactDetails.PHONE_MESSAGE)
    private String phone;

    @NotBlank
    @Size(min = 8, max = 72,
            message = "Password must be 8-72 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$",
            message = "Password must contain a letter and a number")
    private String password;
}
