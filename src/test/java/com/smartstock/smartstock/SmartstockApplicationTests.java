package com.smartstock.smartstock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import com.smartstock.smartstock.entity.AuditEvent;
import com.smartstock.smartstock.entity.Notification;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.repository.AuditEventRepository;
import com.smartstock.smartstock.repository.NotificationRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SmartstockApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private WarehouseRepository warehouseRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private PurchaseOrderRepository purchaseOrderRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private StockMovementRepository stockMovementRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void dashboardSummaryReturnsAggregatesAndChartSeries() throws Exception {
		mockMvc.perform(get("/api/dashboard/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.totalProducts").isNumber())
				.andExpect(jsonPath("$.warehouseDistribution").isArray())
				.andExpect(jsonPath("$.movementTrend").isArray())
				.andExpect(jsonPath("$.purchaseOrderStatuses").isArray())
				.andExpect(jsonPath("$.recentMovements").isArray());
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/notifications/unread-count"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void authenticatedUserCanReadAndAcknowledgeOwnNotifications()
			throws Exception {
		Long adminId = userRepository.findByEmail(
				"test-admin@example.invalid").orElseThrow().getId();
		long unreadCount = notificationRepository
				.countByRecipient_IdAndReadAtIsNull(adminId);

		mockMvc.perform(get("/api/notifications/unread-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(unreadCount));

		mockMvc.perform(get("/api/notifications/recent"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications")
						.param("type", "LOW_STOCK")
						.param("active", "true")
						.param("unread", "true")
						.param("page", "0")
						.param("size", "10"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/notifications/read-all"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updated").value(unreadCount));

		mockMvc.perform(patch("/api/notifications/999999/read"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "viewer@smartstock.com", roles = "VIEWER")
	void viewerCanReadProductsButCannotExportOrMutateThem()
			throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/products/export"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Pen","sku":"PEN-1",
								"category":"Stationery","unit":"piece",
								"price":1}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "viewer@smartstock.com", roles = "VIEWER")
	void viewerCannotReadAdminSettings() throws Exception {
		mockMvc.perform(get("/api/settings"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void movementHistoryLoadsWithoutOptionalFilters() throws Exception {
		Product product = saveTestProduct();
		Warehouse warehouse = saveTestWarehouse();
		mockMvc.perform(post("/api/stocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"warehouseId":%d,"productId":%d,
								"quantity":2,"minQuantity":1,"maxQuantity":10}
								""".formatted(warehouse.getId(), product.getId())))
				.andExpect(status().isCreated());

		String today = LocalDate.now().toString();
		mockMvc.perform(get("/api/stocks/movements/history")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.totalElements").isNumber());

		mockMvc.perform(get("/api/stocks/movements/history")
						.param("search", " ")
						.param("type", "INBOUND")
						.param("warehouseId", warehouse.getId().toString())
						.param("from", today)
						.param("to", today)
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].productId")
						.value(product.getId()));

		mockMvc.perform(get("/api/stocks/movements/history")
						.param("warehouseId", "999999999")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());

		mockMvc.perform(get("/api/stocks/movements/history")
						.param("search", product.getName())
						.param("type", "INBOUND")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].productId")
						.value(product.getId()));

		mockMvc.perform(get("/api/stocks/movements/history")
						.param("from", today)
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].productId")
						.value(product.getId()));

		mockMvc.perform(get("/api/stocks/movements/history")
						.param("to", today)
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].productId")
						.value(product.getId()));

		mockMvc.perform(get("/api/stocks/movements/history")
						.param("from", today)
						.param("to", LocalDate.now().minusDays(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"The start date must be on or before the end date."));

		mockMvc.perform(get("/api/stocks/movements/export"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/stocks/movements/export")
						.param("search", product.getName())
						.param("type", "INBOUND")
						.param("warehouseId", warehouse.getId().toString())
						.param("from", today)
						.param("to", today))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void stockAllocationReturnsFlatStockResponse() throws Exception {
		Product product = saveTestProduct();
		Warehouse warehouse = saveTestWarehouse();

		mockMvc.perform(post("/api/stocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"warehouseId":%d,"productId":%d,
								"quantity":2,"minQuantity":1,"maxQuantity":10}
								""".formatted(warehouse.getId(), product.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.warehouseId")
						.value(warehouse.getId()))
				.andExpect(jsonPath("$.productId").value(product.getId()))
				.andExpect(jsonPath("$.warehouseName")
						.value(warehouse.getName()))
				.andExpect(jsonPath("$.productName").value(product.getName()))
				.andExpect(jsonPath("$.warehouse").doesNotExist())
				.andExpect(jsonPath("$.product").doesNotExist());
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void duplicateStockAllocationReturnsInboundMovementGuidance()
			throws Exception {
		Product product = saveTestProduct();
		Warehouse warehouse = saveTestWarehouse();
		String request = """
				{"warehouseId":%d,"productId":%d,
				"quantity":2,"minQuantity":1,"maxQuantity":10}
				""".formatted(warehouse.getId(), product.getId());

		mockMvc.perform(post("/api/stocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/stocks")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"This product already has a stock allocation in this "
								+ "warehouse. Use an inbound stock movement to "
								+ "increase the quantity."));
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void purchaseOrderApprovalAndAuditSucceedTogether() throws Exception {
		PurchaseOrder order = savePendingPurchaseOrder();

		mockMvc.perform(put("/api/purchase-orders/{id}/status", order.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"APPROVED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		assertEquals(PurchaseOrder.Status.APPROVED,
				purchaseOrderRepository.findById(order.getId())
						.orElseThrow().getStatus());
		assertTrue(auditEventRepository.findAll().stream()
				.anyMatch(event -> "UPDATED_STATUS".equals(event.getAction())
						&& order.getId().equals(event.getEntityId())));
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void purchaseOrderReceiptsUpdateStockMovementsAndNotifications()
			throws Exception {
		PurchaseOrder order = savePendingPurchaseOrder();
		Stock stock = stockRepository.save(Stock.builder()
				.product(order.getProduct())
				.warehouse(order.getWarehouse())
				.quantity(0)
				.minQuantity(0)
				.maxQuantity(20)
				.build());

		mockMvc.perform(get("/api/purchase-orders"))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/purchase-orders/{id}/status", order.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"APPROVED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		assertTrue(notificationRepository.findAll().stream()
				.anyMatch(notification -> order.getId().equals(
						notification.getTargetId())
						&& notification.getType()
						== Notification.Type.PURCHASE_ORDER_APPROVED
						&& notification.getTargetType()
						== Notification.TargetType.PURCHASE_ORDER));

		mockMvc.perform(post("/api/purchase-orders/{id}/receipts", order.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(receiptRequest(4, order, "RCPT-"
								+ UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status")
						.value("PARTIALLY_RECEIVED"))
				.andExpect(jsonPath("$.receivedQuantity").value(4))
				.andExpect(jsonPath("$.remainingQuantity").value(6));

		assertEquals(4, stockRepository.findById(stock.getId())
				.orElseThrow().getQuantity());
		var partialMovements = stockMovementRepository
				.findByStockIdOrderByCreatedAtDesc(stock.getId());
		assertEquals(1, partialMovements.size());
		assertEquals(4, partialMovements.getFirst().getQuantity());
		assertEquals(StockMovement.MovementType.INBOUND,
				partialMovements.getFirst().getMovementType());

		mockMvc.perform(post("/api/purchase-orders/{id}/receipts", order.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(receiptRequest(6, order, "RCPT-"
								+ UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RECEIVED"))
				.andExpect(jsonPath("$.receivedQuantity").value(10))
				.andExpect(jsonPath("$.remainingQuantity").value(0));

		assertEquals(10, stockRepository.findById(stock.getId())
				.orElseThrow().getQuantity());
		var allMovements = stockMovementRepository
				.findByStockIdOrderByCreatedAtDesc(stock.getId());
		assertEquals(2, allMovements.size());
		assertEquals(6, allMovements.getFirst().getQuantity());
		assertEquals(4, allMovements.getLast().getQuantity());
		assertTrue(notificationRepository.findAll().stream()
				.anyMatch(notification -> order.getId().equals(
						notification.getTargetId())
						&& notification.getType()
						== Notification.Type.PURCHASE_ORDER_PARTIALLY_RECEIVED
						&& notification.getTargetType()
						== Notification.TargetType.PURCHASE_ORDER));
		assertTrue(notificationRepository.findAll().stream()
				.anyMatch(notification -> order.getId().equals(
						notification.getTargetId())
						&& notification.getType()
						== Notification.Type.PURCHASE_ORDER_RECEIVED
						&& notification.getTargetType()
						== Notification.TargetType.PURCHASE_ORDER));

		mockMvc.perform(post("/api/purchase-orders/{id}/receipts", order.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(receiptRequest(1, order, "RCPT-"
								+ UUID.randomUUID())))
				.andExpect(status().isBadRequest());
		assertEquals(10, stockRepository.findById(stock.getId())
				.orElseThrow().getQuantity());
	}

	@Test
	void auditFailureRollsBackPurchaseOrderStatus() throws Exception {
		PurchaseOrder order = savePendingPurchaseOrder();
		String invalidAuditName = "a".repeat(121);

		mockMvc.perform(put("/api/purchase-orders/{id}/status", order.getId())
						.with(user(invalidAuditName).roles("ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"APPROVED\"}"))
				.andExpect(status().is5xxServerError());

		assertEquals(PurchaseOrder.Status.PENDING,
				purchaseOrderRepository.findById(order.getId())
						.orElseThrow().getStatus());
	}

	private Product saveTestProduct() {
		String suffix = UUID.randomUUID().toString();
		return productRepository.save(Product.builder()
				.name("Integration product " + suffix)
				.sku("IT-" + suffix.substring(0, 12))
				.category("Test")
				.unit("piece")
				.price(BigDecimal.TEN)
				.storageCapacityFactor(BigDecimal.ONE)
				.build());
	}

	private Warehouse saveTestWarehouse() {
		String suffix = UUID.randomUUID().toString();
		return warehouseRepository.save(Warehouse.builder()
				.name("Integration warehouse " + suffix)
				.location("Test")
				.capacity(100)
				.build());
	}

	private PurchaseOrder savePendingPurchaseOrder() {
		Product product = saveTestProduct();
		Warehouse warehouse = saveTestWarehouse();
		Supplier supplier = supplierRepository.save(Supplier.builder()
				.name("Integration supplier " + UUID.randomUUID())
				.contactEmail("integration@example.invalid")
				.phone("123456789")
				.rating(BigDecimal.valueOf(4))
				.active(true)
				.build());
		User creator = userRepository.findByEmail(
				"test-admin@example.invalid").orElseThrow();
		return purchaseOrderRepository.save(PurchaseOrder.builder()
				.poNumber("IT-" + UUID.randomUUID().toString().substring(0, 12))
				.supplier(supplier)
				.product(product)
				.warehouse(warehouse)
				.quantity(10)
				.receivedQuantity(0)
				.purchaseUnitPrice(BigDecimal.TEN)
				.status(PurchaseOrder.Status.PENDING)
				.createdBy(creator)
				.build());
	}

	private String receiptRequest(
			int quantity, PurchaseOrder order, String referenceNumber) {
		return """
				{"quantity":%d,"receivedDate":"%s","warehouseId":%d,
				"referenceNumber":"%s","notes":"Integration receipt"}
				""".formatted(quantity, LocalDate.now(),
				order.getWarehouse().getId(), referenceNumber);
	}
}
