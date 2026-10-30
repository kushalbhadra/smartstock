package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.NotificationResponse;
import com.smartstock.smartstock.entity.*;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.NotificationRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int NEAR_CAPACITY_PERCENT = 90;

    private final NotificationRepository notificationRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            StockRepository stockRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public void onStockChanged(Stock stock) {
        List<User> recipients = warehouseRecipients(stock.getWarehouse().getId());
        Notification.Type currentType = getStockCondition(stock);
        String conditionPrefix = "stock:" + stock.getId() + ":";
        String message = stockMessage(stock, currentType);

        for (Notification.Type type : stockTypes()) {
            String conditionKey = conditionPrefix + type.name();
            reconcileForRecipients(
                    conditionKey,
                    recipients,
                    type == currentType ? type : null,
                    type == currentType ? message : null,
                    Notification.TargetType.STOCK,
                    stock.getId());
        }

        onWarehouseInventoryChanged(stock.getWarehouse());
    }

    public void onWarehouseInventoryChanged(Warehouse warehouse) {
        java.math.BigDecimal stockTotal = stockRepository
                .sumStorageCapacityByWarehouseId(
                warehouse.getId());
        java.math.BigDecimal capacity = java.math.BigDecimal.valueOf(
                warehouse.getCapacity());
        Notification.Type currentType = stockTotal.compareTo(capacity) > 0
                ? Notification.Type.WAREHOUSE_CAPACITY_EXCEEDED
                : stockTotal.compareTo(capacity) == 0
                ? Notification.Type.WAREHOUSE_CAPACITY_FULL
                : capacity.signum() > 0
                && stockTotal.multiply(java.math.BigDecimal.valueOf(100))
                .compareTo(capacity.multiply(java.math.BigDecimal.valueOf(
                        NEAR_CAPACITY_PERCENT))) >= 0
                ? Notification.Type.WAREHOUSE_NEAR_CAPACITY
                : null;
        String conditionKey = "warehouse:" + warehouse.getId() + ":";
        List<User> recipients = warehouseRecipients(warehouse.getId());

        String message = currentType == null
                ? null
                : String.format(
                        "Warehouse %s is using %s of %s storage units.",
                        warehouse.getName(), stockTotal, capacity);

        reconcileForRecipients(
                conditionKey
                        + Notification.Type.WAREHOUSE_NEAR_CAPACITY.name(),
                recipients,
                currentType == Notification.Type.WAREHOUSE_NEAR_CAPACITY
                        ? currentType : null,
                message,
                Notification.TargetType.WAREHOUSE,
                warehouse.getId());
        reconcileForRecipients(
                conditionKey
                        + Notification.Type.WAREHOUSE_CAPACITY_EXCEEDED.name(),
                recipients,
                currentType
                        == Notification.Type.WAREHOUSE_CAPACITY_EXCEEDED
                        ? currentType : null,
                message,
                Notification.TargetType.WAREHOUSE,
                warehouse.getId());
        reconcileForRecipients(
                conditionKey
                        + Notification.Type.WAREHOUSE_CAPACITY_FULL.name(),
                recipients,
                currentType == Notification.Type.WAREHOUSE_CAPACITY_FULL
                        ? currentType : null,
                message,
                Notification.TargetType.WAREHOUSE,
                warehouse.getId());
    }

    public void onForecastSaved(DemandForecast forecast) {
        Long productId = forecast.getProduct().getId();
        String conditionKey = "forecast:" + productId + ":ANOMALY";
        List<User> recipients = forecastRecipients(productId);
        if (Boolean.TRUE.equals(forecast.getAnomalyDetected())) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", "id", productId));
            String message = String.format(
                    "AI demand forecast detected an anomaly for %s.",
                    product.getName());
            reconcileForRecipients(
                    conditionKey,
                    recipients,
                    Notification.Type.FORECAST_ANOMALY,
                    message,
                    Notification.TargetType.PRODUCT,
                    productId);
        } else {
            reconcileForRecipients(
                    conditionKey,
                    recipients,
                    null,
                    null,
                    Notification.TargetType.PRODUCT,
                    productId);
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        User recipient = findRecipient(email);
        if (recipient.getRole() == User.Role.VIEWER) {
            return 0;
        }
        return notificationRepository
                .countByRecipient_IdAndReadAtIsNull(recipient.getId());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecent(
            String email, int requestedLimit) {
        User recipient = findRecipient(email);
        if (recipient.getRole() == User.Role.VIEWER) {
            return List.of();
        }
        int limit = requestedLimit < 1
                ? DEFAULT_RECENT_LIMIT
                : Math.min(requestedLimit, MAX_PAGE_SIZE);
        return notificationRepository
                .findByRecipient_IdOrderByLastOccurredAtDesc(
                        recipient.getId(), PageRequest.of(0, limit))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(
            String email,
            Notification.Type type,
            Notification.Severity severity,
            Boolean active,
            Boolean unread,
            int page,
            int requestedSize) {
        User recipient = findRecipient(email);
        int safePage = Math.max(page, 0);
        int size = requestedSize < 1
                ? 20
                : Math.min(requestedSize, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                safePage,
                size,
                Sort.by(Sort.Direction.DESC, "lastOccurredAt"));
        if (recipient.getRole() == User.Role.VIEWER) {
            return Page.empty(pageable);
        }

        return notificationRepository.searchForRecipient(
                        recipient.getId(), type, severity, active, unread,
                        pageable)
                .map(NotificationResponse::from);
    }

    public void onPurchaseOrderStatus(PurchaseOrder order) {
        Notification.Type type = switch (order.getStatus()) {
            case APPROVED -> Notification.Type.PURCHASE_ORDER_APPROVED;
            case PARTIALLY_RECEIVED ->
                    Notification.Type.PURCHASE_ORDER_PARTIALLY_RECEIVED;
            case RECEIVED, DELIVERED ->
                    Notification.Type.PURCHASE_ORDER_RECEIVED;
            case PENDING, CANCELLED -> null;
        };
        if (type == null) {
            return;
        }

        String conditionKey = "purchase-order:" + order.getId() + ":"
                + type.name();
        String message = switch (type) {
            case PURCHASE_ORDER_APPROVED ->
                    "Purchase order " + order.getPoNumber() + " was approved.";
            case PURCHASE_ORDER_PARTIALLY_RECEIVED ->
                    "Purchase order " + order.getPoNumber() + " has received "
                            + order.getReceivedQuantity() + " of "
                            + order.getQuantity() + " units.";
            case PURCHASE_ORDER_RECEIVED ->
                    "Purchase order " + order.getPoNumber()
                            + " has been fully received.";
            default -> throw new IllegalStateException(
                    "Unexpected purchase order notification type " + type);
        };
        reconcileForRecipients(
                conditionKey,
                warehouseRecipients(order.getWarehouse().getId()),
                type,
                message,
                Notification.TargetType.PURCHASE_ORDER,
                order.getId());
    }

    public NotificationResponse markAsRead(String email, Long notificationId) {
        User recipient = findRecipient(email);
        ensureNotificationAccess(recipient);
        Notification notification = notificationRepository
                .findByIdAndRecipient_Id(notificationId, recipient.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification", "id", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
        return NotificationResponse.from(
                notificationRepository.save(notification));
    }

    public int markAllAsRead(String email) {
        User recipient = findRecipient(email);
        ensureNotificationAccess(recipient);
        return notificationRepository.markAllRead(
                recipient.getId(), LocalDateTime.now());
    }

    private void ensureNotificationAccess(User recipient) {
        if (recipient.getRole() == User.Role.VIEWER) {
            throw new AccessDeniedException(
                    "Viewers do not receive operational notifications");
        }
    }

    private void reconcileForRecipients(
            String conditionKey,
            List<User> recipients,
            Notification.Type activeType,
            String message,
            Notification.TargetType targetType,
            Long targetId) {
        Set<Long> recipientIds = recipients.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        for (User recipient : recipients) {
            reconcile(recipient, conditionKey, activeType, message,
                    targetType, targetId);
        }

        for (Notification existing
                : notificationRepository.findByConditionKey(conditionKey)) {
            if (!recipientIds.contains(existing.getRecipient().getId())
                    && Boolean.TRUE.equals(existing.getActive())) {
                resolve(existing);
            }
        }
    }

    private void reconcile(
            User recipient,
            String conditionKey,
            Notification.Type activeType,
            String message,
            Notification.TargetType targetType,
            Long targetId) {
        Notification notification = notificationRepository
                .findByRecipient_IdAndConditionKey(
                        recipient.getId(), conditionKey)
                .orElse(null);

        if (activeType == null) {
            if (notification != null
                    && Boolean.TRUE.equals(notification.getActive())) {
                resolve(notification);
            }
            return;
        }

        if (notification == null) {
            notification = Notification.builder()
                    .recipient(recipient)
                    .conditionKey(conditionKey)
                    .type(activeType)
                    .title(title(activeType))
                    .message(message)
                    .severity(severity(activeType))
                    .targetType(targetType)
                    .targetId(targetId)
                    .active(true)
                    .lastOccurredAt(LocalDateTime.now())
                    .build();
        } else if (!Boolean.TRUE.equals(notification.getActive())) {
            notification.setType(activeType);
            notification.setTitle(title(activeType));
            notification.setMessage(message);
            notification.setSeverity(severity(activeType));
            notification.setTargetType(targetType);
            notification.setTargetId(targetId);
            notification.setActive(true);
            notification.setReadAt(null);
            notification.setResolvedAt(null);
            notification.setLastOccurredAt(LocalDateTime.now());
        } else {
            notification.setTitle(title(activeType));
            notification.setMessage(message);
            notification.setSeverity(severity(activeType));
            notification.setLastOccurredAt(LocalDateTime.now());
        }

        notificationRepository.save(notification);
    }

    private void resolve(Notification notification) {
        notification.setActive(false);
        notification.setResolvedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private List<User> warehouseRecipients(Long warehouseId) {
        Map<Long, User> recipients = new LinkedHashMap<>();
        userRepository.findByRole(User.Role.ADMIN)
                .forEach(user -> recipients.put(user.getId(), user));
        userRepository.findByWarehouseId(warehouseId)
                .forEach(user -> recipients.put(user.getId(), user));
        return recipients.values().stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    private List<User> forecastRecipients(Long productId) {
        List<User> recipients = new java.util.ArrayList<>(
                userRepository.findByRole(User.Role.ADMIN));
        stockRepository.findByProductId(productId).stream()
                .map(stock -> stock.getWarehouse().getId())
                .distinct()
                .forEach(warehouseId -> recipients.addAll(
                        userRepository.findByWarehouseId(warehouseId)));
        return recipients.stream()
                .collect(Collectors.toMap(User::getId, user -> user,
                        (first, ignored) -> first))
                .values()
                .stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    private User findRecipient(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", email));
    }

    private Notification.Type getStockCondition(Stock stock) {
        if (stock.getQuantity() == 0) {
            return Notification.Type.OUT_OF_STOCK;
        }
        if (stock.getQuantity() < stock.getMinQuantity()) {
            return Notification.Type.LOW_STOCK;
        }
        if (stock.getQuantity() > stock.getMaxQuantity()) {
            return Notification.Type.OVERSTOCK;
        }
        return null;
    }

    private String stockMessage(Stock stock, Notification.Type type) {
        if (type == null) {
            return null;
        }
        return String.format(
                "%s is %s in %s (%d units; maximum %d).",
                stock.getProduct().getName(),
                type == Notification.Type.OUT_OF_STOCK
                        ? "out of stock"
                        : type == Notification.Type.LOW_STOCK
                        ? "below its minimum stock level"
                        : "over its maximum stock level",
                stock.getWarehouse().getName(),
                stock.getQuantity(),
                stock.getMaxQuantity());
    }

    private String title(Notification.Type type) {
        return switch (type) {
            case LOW_STOCK -> "Low stock";
            case OUT_OF_STOCK -> "Out of stock";
            case OVERSTOCK -> "Overstock";
            case WAREHOUSE_NEAR_CAPACITY -> "Warehouse nearing capacity";
            case WAREHOUSE_CAPACITY_FULL -> "Warehouse at capacity";
            case WAREHOUSE_CAPACITY_EXCEEDED -> "Warehouse capacity exceeded";
            case FORECAST_ANOMALY -> "AI forecast anomaly";
            case PURCHASE_ORDER_APPROVED -> "Purchase order approved";
            case PURCHASE_ORDER_PARTIALLY_RECEIVED ->
                    "Purchase order partially received";
            case PURCHASE_ORDER_RECEIVED -> "Purchase order received";
        };
    }

    private Notification.Severity severity(Notification.Type type) {
        return switch (type) {
            case OUT_OF_STOCK, OVERSTOCK, WAREHOUSE_CAPACITY_FULL,
                    WAREHOUSE_CAPACITY_EXCEEDED ->
                    Notification.Severity.CRITICAL;
            case LOW_STOCK, WAREHOUSE_NEAR_CAPACITY,
                    FORECAST_ANOMALY -> Notification.Severity.WARNING;
            case PURCHASE_ORDER_APPROVED,
                    PURCHASE_ORDER_PARTIALLY_RECEIVED,
                    PURCHASE_ORDER_RECEIVED -> Notification.Severity.INFO;
        };
    }

    private List<Notification.Type> stockTypes() {
        return List.of(
                Notification.Type.LOW_STOCK,
                Notification.Type.OUT_OF_STOCK,
                Notification.Type.OVERSTOCK);
    }
}
