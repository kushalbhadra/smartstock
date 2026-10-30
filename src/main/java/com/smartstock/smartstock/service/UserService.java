package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.UserProfileRequest;
import com.smartstock.smartstock.dto.UserRequest;
import com.smartstock.smartstock.dto.UserResponse;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.entity.Warehouse;
import com.smartstock.smartstock.exception.BusinessException;
import com.smartstock.smartstock.exception.ResourceNotFoundException;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.repository.WarehouseRepository;
import com.smartstock.smartstock.repository.AlertLogRepository;
import com.smartstock.smartstock.repository.NotificationRepository;
import com.smartstock.smartstock.repository.PurchaseOrderRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.SupportTicketRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AlertLogRepository alertLogRepository;
    private final NotificationRepository notificationRepository;
    private final SupportTicketRepository supportTicketRepository;

    public UserService(
            UserRepository userRepository,
            WarehouseRepository warehouseRepository,
            PasswordEncoder passwordEncoder,
            PurchaseOrderRepository purchaseOrderRepository,
            StockMovementRepository stockMovementRepository,
            AlertLogRepository alertLogRepository,
            NotificationRepository notificationRepository,
            SupportTicketRepository supportTicketRepository) {
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.passwordEncoder = passwordEncoder;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.alertLogRepository = alertLogRepository;
        this.notificationRepository = notificationRepository;
        this.supportTicketRepository = supportTicketRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse create(UserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(
                request.getEmail().trim())) {
            throw new BusinessException("Email is already in use");
        }
        if (request.getPassword() == null
                || request.getPassword().length() < 8) {
            throw new BusinessException(
                    "Password must be at least 8 characters");
        }
        if (request.getRole() == null) {
            throw new BusinessException("A role is required");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .phone(request.getPhone())
                .warehouse(resolveWarehouse(
                        request.getRole(), request.getWarehouseId()))
                .build();
        return toResponse(userRepository.save(user));
    }

    public UserResponse update(Long id, UserRequest request) {
        User user = findById(id);
        if (!user.getEmail().equalsIgnoreCase(request.getEmail().trim())
                && userRepository.existsByEmailIgnoreCase(
                request.getEmail().trim())) {
            throw new BusinessException("Email is already in use");
        }
        if (request.getRole() == null) {
            throw new BusinessException("A role is required");
        }
        if (request.getPassword() != null
                && !request.getPassword().isBlank()
                && request.getPassword().length() < 8) {
            throw new BusinessException(
                    "Password must be at least 8 characters");
        }
        ensureAdminRemains(user, request.getRole());

        user.setName(request.getName().trim());
        user.setRole(request.getRole());
        user.setPhone(request.getPhone());
        if (request.getPassword() != null
                && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setWarehouse(resolveWarehouse(
                request.getRole(), request.getWarehouseId()));
        return toResponse(userRepository.save(user));
    }

    public void delete(Long id, String currentEmail) {
        User user = findById(id);
        if (user.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new BusinessException("You cannot delete your own account");
        }
        if (purchaseOrderRepository.existsByCreatedById(id)
                || stockMovementRepository.existsByPerformedById(id)
                || alertLogRepository.existsByUserId(id)
                || notificationRepository.existsByRecipient_Id(id)
                || supportTicketRepository.existsByCreatedById(id)) {
            throw new BusinessException(
                    "This user has purchase orders, stock movements, notifications, or alert history and cannot be deleted");
        }
        ensureAdminRemains(user, null);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentProfile(String email) {
        return toResponse(findByEmail(email));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public UserResponse updateCurrentProfile(
            String email, UserProfileRequest request) {
        User user = findByEmail(email);
        user.setName(request.getName().trim());
        user.setPhone(request.getPhone());
        return toResponse(userRepository.save(user));
    }

    private void ensureAdminRemains(User user, User.Role newRole) {
        if (user.getRole() == User.Role.ADMIN
                && newRole != User.Role.ADMIN
                && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw new BusinessException(
                    "At least one administrator account must remain");
        }
    }

    private Warehouse resolveWarehouse(User.Role role, Long warehouseId) {
        if (role != User.Role.WAREHOUSE_MGR) {
            return null;
        }
        if (warehouseId == null) {
            throw new BusinessException(
                    "Warehouse managers must be assigned a warehouse");
        }
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse", "id", warehouseId));
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "id", id));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", "email", email));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .warehouseId(user.getWarehouse() == null
                        ? null : user.getWarehouse().getId())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
