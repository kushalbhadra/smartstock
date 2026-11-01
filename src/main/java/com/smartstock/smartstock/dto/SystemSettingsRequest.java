package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.validation.ContactDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

@Data
public class SystemSettingsRequest {

    @NotBlank
    private String companyName;

    @Email
    @Pattern(regexp = ContactDetails.EMAIL_PATTERN,
            message = ContactDetails.EMAIL_MESSAGE)
    @Size(max = 150)
    private String contactEmail;

    @Pattern(regexp = ContactDetails.PHONE_PATTERN,
            message = ContactDetails.PHONE_MESSAGE)
    @Size(max = 13, message = ContactDetails.PHONE_MESSAGE)
    private String contactPhone;

    @NotNull
    @Min(0)
    private Integer defaultLowStockThreshold;

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail == null || contactEmail.isBlank()
                ? null : contactEmail.trim().toLowerCase(Locale.ROOT);
    }
}
