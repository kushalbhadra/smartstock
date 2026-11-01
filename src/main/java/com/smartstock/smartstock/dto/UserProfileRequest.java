package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.validation.ContactDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileRequest {

    @NotBlank
    private String name;

    @Pattern(regexp = ContactDetails.PHONE_PATTERN,
            message = ContactDetails.PHONE_MESSAGE)
    @Size(max = 13, message = ContactDetails.PHONE_MESSAGE)
    private String phone;
}
