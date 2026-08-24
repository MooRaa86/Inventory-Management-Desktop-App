package com.company.inventory.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    @Query("SELECT DISTINCT p.code FROM User u JOIN u.roles r JOIN r.permissions p WHERE lower(u.username) = lower(:username)")
    List<String> findCodesByUsername(@Param("username") String username);
}
