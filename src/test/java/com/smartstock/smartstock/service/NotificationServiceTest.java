package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.Notification;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.repository.NotificationRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    private final Map<String, Notification> notifications = new HashMap<>();
    private NotificationService notificationService;
    private Stock stock;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, stockRepository,
                productRepository, userRepository);

        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .name("Main warehouse")
                .capacity(100)
                .build();
        Product product = Product.builder()
                .id(3L)
                .name("Widget")
                .build();
        stock = Stock.builder()
                .id(4L)
                .warehouse(warehouse)
                .product(product)
                .quantity(5)
                .minQuantity(10)
                .maxQuantity(100)
                .build();
        User manager = User.builder()
                .id(5L)
                .name("Warehouse Manager")
                .role(User.Role.WAREHOUSE_MGR)
                .build();

        lenient().when(userRepository.findByRole(User.Role.ADMIN))
                .thenReturn(List.of());
        lenient().when(userRepository.findByWarehouseId(2L))
                .thenReturn(List.of(manager));
        lenient().when(stockRepository.sumStorageCapacityByWarehouseId(anyLong()))
                .thenAnswer(invocation -> BigDecimal.valueOf(
                        stock.getQuantity()));
        lenient().when(notificationRepository.findByRecipient_IdAndConditionKey(
                anyLong(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        notifications.get(key(invocation.getArgument(0),
                                invocation.getArgument(1)))));
        lenient().when(notificationRepository.findByConditionKey(anyString()))
                .thenAnswer(invocation -> notifications.values().stream()
                        .filter(notification -> notification.getConditionKey()
                                .equals(invocation.getArgument(0)))
                        .toList());
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification notification = invocation.getArgument(0);
                    notifications.put(
                            key(notification.getRecipient().getId(),
                                    notification.getConditionKey()),
                            notification);
                    return notification;
                });
    }

    @Test
    void deduplicatesAndResolvesStockAlertsThenReactivatesTheSameRecord() {
        notificationService.onStockChanged(stock);
        Notification lowStock = onlyNotification();
        assertEquals(Notification.Type.LOW_STOCK, lowStock.getType());
        assertTrue(lowStock.getActive());

        lowStock.setReadAt(java.time.LocalDateTime.now());
        notificationService.onStockChanged(stock);
        assertEquals(1, notifications.size());
        assertNotNull(lowStock.getReadAt());

        stock.setQuantity(10);
        notificationService.onStockChanged(stock);
        assertFalse(lowStock.getActive());
        assertNotNull(lowStock.getResolvedAt());

        stock.setQuantity(5);
        notificationService.onStockChanged(stock);
        assertEquals(1, notifications.size());
        assertTrue(lowStock.getActive());
        assertNull(lowStock.getReadAt());
        assertNull(lowStock.getResolvedAt());
    }

    @Test
    void createsDistinctOutOfStockAndOverstockConditions() {
        stock.setQuantity(0);
        notificationService.onStockChanged(stock);
        assertEquals(1, notifications.size());
        assertEquals(Notification.Type.OUT_OF_STOCK,
                onlyNotification().getType());

        stock.setQuantity(101);
        notificationService.onStockChanged(stock);
        assertEquals(3, notifications.size());
        assertTrue(notifications.values().stream()
                .filter(notification -> notification.getType()
                        == Notification.Type.OUT_OF_STOCK)
                .noneMatch(Notification::getActive));
        assertTrue(notifications.values().stream()
                .anyMatch(notification -> notification.getType()
                        == Notification.Type.OVERSTOCK
                        && notification.getActive()));
    }

    @Test
    void notifiesAtNinetyPercentAndSwitchesToExceededCapacity() {
        stock.setQuantity(90);
        notificationService.onStockChanged(stock);
        Notification nearCapacity = notifications.values().stream()
                .filter(notification -> notification.getType()
                        == Notification.Type.WAREHOUSE_NEAR_CAPACITY)
                .findFirst()
                .orElseThrow();
        assertTrue(nearCapacity.getActive());

        stock.setQuantity(101);
        notificationService.onStockChanged(stock);
        Notification exceeded = notifications.values().stream()
                .filter(notification -> notification.getType()
                        == Notification.Type.WAREHOUSE_CAPACITY_EXCEEDED)
                .findFirst()
                .orElseThrow();
        assertFalse(nearCapacity.getActive());
        assertTrue(exceeded.getActive());
    }

    @Test
    void raisesDistinctAlertWhenWarehouseIsExactlyFull() {
        stock.setQuantity(100);
        notificationService.onStockChanged(stock);

        Notification full = notifications.values().stream()
                .filter(notification -> notification.getType()
                        == Notification.Type.WAREHOUSE_CAPACITY_FULL)
                .findFirst()
                .orElseThrow();
        assertTrue(full.getActive());
        assertEquals(Notification.Severity.CRITICAL, full.getSeverity());
    }

    @Test
    void deduplicatesAndResolvesForecastAnomalies() {
        when(stockRepository.findByProductId(3L)).thenReturn(List.of(stock));
        when(productRepository.findById(3L))
                .thenReturn(Optional.of(stock.getProduct()));
        DemandForecast forecast = DemandForecast.builder()
                .product(stock.getProduct())
                .anomalyDetected(true)
                .build();

        notificationService.onForecastSaved(forecast);
        notificationService.onForecastSaved(forecast);

        assertEquals(1, notifications.size());
        Notification anomaly = notifications.values().iterator().next();
        assertEquals(Notification.Type.FORECAST_ANOMALY, anomaly.getType());
        assertTrue(anomaly.getActive());

        forecast.setAnomalyDetected(false);
        notificationService.onForecastSaved(forecast);
        assertFalse(anomaly.getActive());
    }

    @Test
    void createsScopedPurchaseOrderStatusNotification() {
        PurchaseOrder order = PurchaseOrder.builder()
                .id(20L)
                .poNumber("PO-20")
                .warehouse(stock.getWarehouse())
                .quantity(12)
                .receivedQuantity(4)
                .status(PurchaseOrder.Status.PARTIALLY_RECEIVED)
                .build();

        notificationService.onPurchaseOrderStatus(order);

        Notification notification = notifications.values().iterator().next();
        assertEquals(Notification.Type.PURCHASE_ORDER_PARTIALLY_RECEIVED,
                notification.getType());
        assertEquals(Notification.Severity.INFO, notification.getSeverity());
        assertEquals("Purchase order partially received",
                notification.getTitle());
        assertEquals(Notification.TargetType.PURCHASE_ORDER,
                notification.getTargetType());
        assertEquals(20L, notification.getTargetId());
    }

    @Test
    void viewersDoNotReceiveOperationalNotifications() {
        User viewer = User.builder()
                .id(12L)
                .email("viewer@example.com")
                .role(User.Role.VIEWER)
                .build();
        when(userRepository.findByEmail(viewer.getEmail()))
                .thenReturn(Optional.of(viewer));

        assertEquals(0, notificationService.getUnreadCount(
                viewer.getEmail()));
        assertTrue(notificationService.getRecent(
                viewer.getEmail(), 8).isEmpty());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markingReadIsScopedToTheAuthenticatedRecipient() {
        User recipient = User.builder()
                .id(13L)
                .email("manager@example.com")
                .role(User.Role.WAREHOUSE_MGR)
                .build();
        Notification notification = Notification.builder()
                .id(17L)
                .recipient(recipient)
                .conditionKey("stock:4:LOW_STOCK")
                .type(Notification.Type.LOW_STOCK)
                .message("Widget is below its minimum stock level.")
                .targetType(Notification.TargetType.STOCK)
                .targetId(4L)
                .active(true)
                .build();
        when(userRepository.findByEmail(recipient.getEmail()))
                .thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipient_Id(
                notification.getId(), recipient.getId()))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification))
                .thenReturn(notification);

        notificationService.markAsRead(
                recipient.getEmail(), notification.getId());

        assertTrue(notification.getReadAt() != null);
        verify(notificationRepository).findByIdAndRecipient_Id(
                notification.getId(), recipient.getId());
    }

    private Notification onlyNotification() {
        List<Notification> values = new ArrayList<>(notifications.values());
        return values.stream()
                .filter(notification -> notification.getType()
                        == Notification.Type.LOW_STOCK
                        || notification.getType()
                        == Notification.Type.OUT_OF_STOCK
                        || notification.getType()
                        == Notification.Type.OVERSTOCK)
                .findFirst()
                .orElseThrow();
    }

    private String key(Long recipientId, String conditionKey) {
        return recipientId + ":" + conditionKey;
    }

    private static void assertNotNull(Object value) {
        assertTrue(value != null);
    }
}
