package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.validation.ContactDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Pattern(regexp = ContactDetails.EMAIL_PATTERN,
            message = ContactDetails.EMAIL_MESSAGE)
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public void setEmail(String email) {
        this.email = email == null
                ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}