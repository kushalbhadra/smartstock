package com.smartstock.smartstock.repository;

import com.smartstock.smartstock.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// JpaRepository<Entity, PrimaryKeyType>
// Gives you: findById, findAll, save, delete, count — all free
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Spring generates: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // Spring generates: SELECT * FROM users WHERE role = ?
    java.util.List<User> findByRole(User.Role role);

    // Spring generates: SELECT * FROM users WHERE warehouse_id = ?
    java.util.List<User> findByWarehouseId(Long warehouseId);

    // Check if email already exists (for registration)
    boolean existsByEmail(String email);
}