package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.validation.ContactDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

@Data
public class UserRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Email
    @Pattern(regexp = ContactDetails.EMAIL_PATTERN,
            message = ContactDetails.EMAIL_MESSAGE)
    @Size(max = 150)
    private String email;

    private String password;
    private User.Role role;
    private Long warehouseId;

    @Pattern(regexp = ContactDetails.PHONE_PATTERN,
            message = ContactDetails.PHONE_MESSAGE)
    @Size(max = 13, message = ContactDetails.PHONE_MESSAGE)
    private String phone;

    public void setEmail(String email) {
        this.email = email == null
                ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
