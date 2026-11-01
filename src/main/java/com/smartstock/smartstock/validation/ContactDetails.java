package com.smartstock.smartstock.validation;

import com.smartstock.smartstock.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ContactDetails {

    public static final String PHONE_PATTERN =
            "^(?:(?:\\+91)?[6-9][0-9]{9})?$";
    public static final String PHONE_MESSAGE =
            "Phone must be a valid 10-digit Indian mobile number, "
                    + "optionally prefixed with +91.";
    public static final String EMAIL_PATTERN =
            "^(?:(?!.*\\.\\.)[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+"
                    + "(?<!\\.)@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}"
                    + "[A-Za-z0-9])?)+)?$";
    public static final String EMAIL_MESSAGE =
            "Enter a valid email address, such as example@gmail.com.";

    private static final Pattern PHONE_VALIDATOR =
            Pattern.compile(PHONE_PATTERN);

    private ContactDetails() {
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }
        if (!PHONE_VALIDATOR.matcher(phone).matches()) {
            throw new BusinessException(PHONE_MESSAGE);
        }
        return phone.startsWith("+91") ? phone.substring(3) : phone;
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeRequiredEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
