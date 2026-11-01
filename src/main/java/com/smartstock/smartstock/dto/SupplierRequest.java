package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.validation.ContactDetails;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Locale;

@Data
public class SupplierRequest {

    @Size(max = 40)
    private String supplierCode;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Email
    @Pattern(regexp = ContactDetails.EMAIL_PATTERN,
            message = ContactDetails.EMAIL_MESSAGE)
    @Size(max = 150)
    private String contactEmail;

    @Pattern(regexp = ContactDetails.PHONE_PATTERN,
            message = ContactDetails.PHONE_MESSAGE)
    @Size(max = 13, message = ContactDetails.PHONE_MESSAGE)
    private String phone;

    @Size(max = 150)
    private String contactPerson;

    @Size(max = 500)
    private String address;

    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private BigDecimal rating;

    private Boolean active;

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail == null
                ? null : contactEmail.trim().toLowerCase(Locale.ROOT);
    }
}
