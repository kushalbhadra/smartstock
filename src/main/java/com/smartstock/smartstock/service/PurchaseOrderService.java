package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.PurchaseOrderRequest;
import com.smartstock.smartstock.dto.PurchaseOrderResponse;
import com.smartstock.smartstock.dto.PurchaseOrderReceiptRequest;
import com.smartstock.smartstock.dto.StockMovementRequest;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.PurchaseOrderReceiptRepository;
import com.smartstock.smartstock.repository.SupplierRepository;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import com.smartstock.smartstock.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderReceiptRepository receiptRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final NotificationService notificationService;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderReceiptRepository receiptRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository,
            StockService stockService,
            NotificationService notificationService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.receiptRepository = receiptRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getAll(String email) {
        User user = findUser(email);
        List<PurchaseOrder> orders;
        if (user.getRole() == User.Role.WAREHOUSE_MGR) {
            if (user.getWarehouse() == null) {
                return List.of();
            }
            orders = purchaseOrderRepository.findByWarehouseId(
                    user.getWarehouse().getId()
            );
        } else {
            orders = purchaseOrderRepository.findAll();
        }
        return orders.stream().map(PurchaseOrderResponse::from).toList();
    }

    public PurchaseOrderResponse create(
            PurchaseOrderRequest request,
            String email
    ) {
        User user = findUser(email);
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", request.getWarehouseId()));

        if (user.getRole() == User.Role.WAREHOUSE_MGR
                && (user.getWarehouse() == null
                || !user.getWarehouse().getId().equals(warehouse.getId()))) {
            throw new AccessDeniedException(
                    "Warehouse managers can create orders only for their warehouse");
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Supplier", "id", request.getSupplierId()));
        if (Boolean.FALSE.equals(supplier.getActive())) {
            throw new BusinessException(
                    "Purchase orders cannot be created for an inactive supplier.");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product", "id", request.getProductId()));

        PurchaseOrder order = PurchaseOrder.builder()
                .supplier(supplier)
                .product(product)
                .warehouse(warehouse)
                .quantity(request.getQuantity())
                .purchaseUnitPrice(request.getPurchaseUnitPrice())
                .receivedQuantity(0)
                .status(PurchaseOrder.Status.PENDING)
                .expectedDate(request.getExpectedDate())
                .createdBy(user)
                .build();

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        saved.setPoNumber("PO-" + saved.getId());
        saved = purchaseOrderRepository.save(saved);
        return PurchaseOrderResponse.from(saved);
    }

    public PurchaseOrderResponse updateStatus(
            Long id,
            PurchaseOrder.Status status
    ) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase order", "id", id));
        if (status == order.getStatus()) {
            return PurchaseOrderResponse.from(order);
        }
        boolean allowed = switch (order.getStatus()) {
            case PENDING -> status == PurchaseOrder.Status.APPROVED
                    || status == PurchaseOrder.Status.CANCELLED;
            case APPROVED -> status == PurchaseOrder.Status.CANCELLED;
            case PARTIALLY_RECEIVED, RECEIVED, DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new BusinessException(
                    "Purchase order status transition from "
                            + order.getStatus() + " to " + status
                            + " is not permitted.");
        }
        order.setStatus(status);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        notificationService.onPurchaseOrderStatus(saved);
        return PurchaseOrderResponse.from(saved);
    }

    public PurchaseOrderResponse receive(
            Long id,
            PurchaseOrderReceiptRequest request,
            String email) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Purchase order", "id", id));
        User actor = findUser(email);
        if (actor.getRole() == User.Role.WAREHOUSE_MGR
                && (actor.getWarehouse() == null
                || !actor.getWarehouse().getId().equals(
                        order.getWarehouse().getId()))) {
            throw new AccessDeniedException(
                    "Warehouse managers can receive only for their assigned warehouse.");
        }
        if (!order.getWarehouse().getId().equals(request.getWarehouseId())) {
            throw new BusinessException(
                    "Receipt warehouse must match the purchase order destination.");
        }
        if (order.getStatus() != PurchaseOrder.Status.APPROVED
                && order.getStatus()
                != PurchaseOrder.Status.PARTIALLY_RECEIVED) {
            throw new BusinessException(
                    "Only approved purchase orders can receive goods.");
        }
        String referenceNumber = request.getReferenceNumber().trim();
        if (receiptRepository
                .existsByPurchaseOrderIdAndReferenceNumberIgnoreCase(
                        id, referenceNumber)) {
            throw new BusinessException(
                    "This receipt reference has already been processed.");
        }
        int remaining = order.getQuantity() - order.getReceivedQuantity();
        if (request.getQuantity() > remaining) {
            throw new BusinessException(
                    "Received quantity exceeds the remaining order quantity. "
                            + "Remaining: " + remaining
                            + "; requested: " + request.getQuantity() + ".");
        }

        StockMovementRequest movementRequest = new StockMovementRequest();
        movementRequest.setWarehouseId(order.getWarehouse().getId());
        movementRequest.setProductId(order.getProduct().getId());
        movementRequest.setMovementType(StockMovement.MovementType.INBOUND);
        movementRequest.setQuantity(request.getQuantity());
        movementRequest.setReferenceNumber(referenceNumber);
        movementRequest.setReason("Purchase order " + order.getPoNumber()
                + " receipt");
        stockService.recordMovement(movementRequest, actor.getId());

        receiptRepository.save(com.smartstock.smartstock.entity
                .PurchaseOrderReceipt.builder()
                .purchaseOrder(order)
                .quantity(request.getQuantity())
                .receivedDate(request.getReceivedDate())
                .referenceNumber(referenceNumber)
                .notes(request.getNotes())
                .receivedBy(actor)
                .build());
        order.setReceivedQuantity(
                order.getReceivedQuantity() + request.getQuantity());
        order.setStatus(order.getReceivedQuantity() == order.getQuantity()
                ? PurchaseOrder.Status.RECEIVED
                : PurchaseOrder.Status.PARTIALLY_RECEIVED);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        notificationService.onPurchaseOrderStatus(saved);
        return PurchaseOrderResponse.from(saved);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "email", email));
    }
}
