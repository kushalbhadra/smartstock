package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.AlertLog;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.AlertLogRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
public class WhatsAppAlertService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.whatsapp-from}")
    private String fromNumber;

    @Autowired private AlertLogRepository alertLogRepository;
    @Autowired private UserRepository userRepository;

    // Initialize Twilio once when app starts
    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    // ── LOW STOCK ALERT ───────────────────────────────────────────────

    public void sendLowStockAlert(Stock stock) {
        String productName = stock.getProduct().getName();
        String warehouseName = stock.getWarehouse().getName();
        int quantity = stock.getQuantity();

        String message = String.format(
                "⚠️ *SmartStock Alert*\n\n" +
                        "Product: *%s*\n" +
                        "Warehouse: *%s*\n" +
                        "Current Stock: *%d units*\n\n" +
                        "Stock is critically low. Please raise a purchase order.",
                productName, warehouseName, quantity
        );

        // Find manager of this warehouse
        List<User> managers = userRepository
                .findByWarehouseId(stock.getWarehouse().getId());

        for (User manager : managers) {
            sendAlert(
                    manager,
                    message,
                    AlertLog.AlertType.LOW_STOCK,
                    stock.getId()
            );
        }
    }

    // ── PO STATUS ALERT ───────────────────────────────────────────────

    public void sendPurchaseOrderAlert(Long userId,
                                       String poNumber,
                                       String status) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        String message = String.format(
                "📦 *SmartStock PO Update*\n\n" +
                        "Purchase Order: *%s*\n" +
                        "New Status: *%s*\n\n" +
                        "Login to SmartStock for details.",
                poNumber, status
        );

        sendAlert(
                user, message,
                AlertLog.AlertType.PO_STATUS, null
        );
    }

    // ── FORECAST READY ALERT ──────────────────────────────────────────

    public void sendForecastReadyAlert(int productCount) {
        // Send to all admins
        List<User> admins = userRepository
                .findByRole(User.Role.ADMIN);

        String message = String.format(
                "📊 *SmartStock Forecast Ready*\n\n" +
                        "Nightly AI forecast completed.\n" +
                        "Products analyzed: *%d*\n\n" +
                        "Login to view demand forecasts.",
                productCount
        );

        for (User admin : admins) {
            sendAlert(
                    admin, message,
                    AlertLog.AlertType.FORECAST_READY, null
            );
        }
    }

    // ── CORE SEND METHOD ──────────────────────────────────────────────

    private void sendAlert(User user,
                           String message,
                           AlertLog.AlertType type,
                           Long relatedEntityId) {

        // Skip if user has no phone
        if (user.getPhone() == null || user.getPhone().isEmpty()) {
            saveAlertLog(user, type, message,
                    AlertLog.AlertStatus.SKIPPED, relatedEntityId);
            return;
        }

        // Skip if alerts disabled for this user
        if (user.getWhatsappAlertsEnabled() != null &&
                !user.getWhatsappAlertsEnabled()) {
            saveAlertLog(user, type, message,
                    AlertLog.AlertStatus.SKIPPED, relatedEntityId);
            return;
        }

        // Skip during quiet hours
        if (isQuietHours(user)) {
            saveAlertLog(user, type, message,
                    AlertLog.AlertStatus.SKIPPED, relatedEntityId);
            return;
        }

        // Send via Twilio
        try {
            Message.creator(
                    new PhoneNumber("whatsapp:" + user.getPhone()),
                    new PhoneNumber(fromNumber),
                    message
            ).create();

            saveAlertLog(user, type, message,
                    AlertLog.AlertStatus.SENT, relatedEntityId);

            System.out.println("WhatsApp sent to: " + user.getName());

        } catch (Exception e) {
            System.err.println("WhatsApp failed: " + e.getMessage());
            saveAlertLog(user, type, message,
                    AlertLog.AlertStatus.FAILED, relatedEntityId);
        }
    }

    // Save every alert attempt to database
    private void saveAlertLog(User user,
                              AlertLog.AlertType type,
                              String message,
                              AlertLog.AlertStatus status,
                              Long relatedEntityId) {
        AlertLog log = AlertLog.builder()
                .user(user)
                .alertType(type)
                .message(message)
                .status(status)
                .relatedEntityId(relatedEntityId)
                .build();

        alertLogRepository.save(log);
    }

    // Check if current time is in user's quiet hours
    private boolean isQuietHours(User user) {
        if (user.getAlertQuietStart() == null ||
                user.getAlertQuietEnd() == null) {
            return false;
        }

        int currentHour = LocalTime.now().getHour();
        int start = user.getAlertQuietStart();
        int end = user.getAlertQuietEnd();

        // Handle overnight quiet hours (e.g. 22 to 7)
        if (start > end) {
            return currentHour >= start || currentHour < end;
        }
        return currentHour >= start && currentHour < end;
    }
}