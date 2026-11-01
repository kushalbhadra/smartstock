package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.PurchaseOrderRequest;
import com.smartstock.smartstock.dto.PurchaseOrderResponse;
import com.smartstock.smartstock.dto.PurchaseOrderReceiptRequest;
import com.smartstock.smartstock.entity.PurchaseOrder;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Supplier;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
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
import org.springframework.beans.factory.annotation.Value;

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
    private final boolean managerApprovalEnabled;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderReceiptRepository receiptRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository,
            StockService stockService,
            NotificationService notificationService,
            @Value("${app.permissions.manager-approve-purchase-orders:false}")
            boolean managerApprovalEnabled
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.receiptRepository = receiptRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.notificationService = notificationService;
        this.managerApprovalEnabled = managerApprovalEnabled;
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
        return orders.stream()
                .map(order -> PurchaseOrderResponse.from(
                        order, canApprove(user, order)))
                .toList();
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
        return PurchaseOrderResponse.from(saved, false);
    }

    public PurchaseOrderResponse updateStatus(
            Long id,
            PurchaseOrder.Status status
    ) {
        return updateStatus(id, status, null, true);
    }

    public PurchaseOrderResponse updateStatus(
            Long id,
            PurchaseOrder.Status status,
            String email
    ) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException(
                    "An authenticated actor is required to change purchase "
                            + "order status.");
        }
        return updateStatus(id, status, email, false);
    }

    public PurchaseOrderResponse updateStatus(
            Long id,
            PurchaseOrder.Status status,
            String email,
            boolean administrator
    ) {
        if (!administrator && (email == null || email.isBlank())) {
            throw new AccessDeniedException(
                    "An authenticated actor is required to change purchase "
                            + "order status.");
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase order", "id", id));
        User actor = administrator || email == null ? null : findUser(email);
        if (!administrator && actor != null) {
            if (actor.getRole() == User.Role.WAREHOUSE_MGR) {
                if (!managerApprovalEnabled
                        || status != PurchaseOrder.Status.APPROVED) {
                    throw new AccessDeniedException(
                            "Warehouse managers are not permitted to change "
                                    + "purchase order status.");
                }
                ensureManagerCanApprove(actor, order);
            } else {
                throw new AccessDeniedException(
                        "Only administrators and permitted warehouse managers "
                                + "can change purchase order status.");
            }
        }
        if (status == order.getStatus()) {
            return PurchaseOrderResponse.from(
                    order, actor != null && canApprove(actor, order));
        }
        boolean allowed = switch (order.getStatus()) {
            case PENDING -> status == PurchaseOrder.Status.APPROVED
                    || status == PurchaseOrder.Status.CANCELLED;
            case APPROVED -> false;
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
        return PurchaseOrderResponse.from(
                saved, actor != null && canApprove(actor, saved));
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

        stockService.recordPurchaseOrderReceipt(
                order.getWarehouse().getId(),
                order.getProduct().getId(),
                request.getQuantity(),
                order.getQuantity(),
                referenceNumber,
                "Purchase order " + order.getPoNumber() + " receipt",
                actor.getId());

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
        return PurchaseOrderResponse.from(saved, false);
    }

    private boolean canApprove(User user, PurchaseOrder order) {
        if (order.getStatus() != PurchaseOrder.Status.PENDING) {
            return false;
        }
        if (user.getRole() == User.Role.ADMIN) {
            return true;
        }
        return user.getRole() == User.Role.WAREHOUSE_MGR
                && managerApprovalEnabled
                && user.getWarehouse() != null
                && user.getWarehouse().getId().equals(
                        order.getWarehouse().getId())
                && !user.getId().equals(order.getCreatedBy().getId());
    }

    private void ensureManagerCanApprove(User actor, PurchaseOrder order) {
        if (actor.getWarehouse() == null
                || !actor.getWarehouse().getId().equals(
                        order.getWarehouse().getId())) {
            throw new AccessDeniedException(
                    "Warehouse managers can approve only orders for their "
                            + "assigned warehouse.");
        }
        if (actor.getId().equals(order.getCreatedBy().getId())) {
            throw new AccessDeniedException(
                    "The purchase order creator cannot approve their own order.");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "email", email));
    }
}
