package ru.rfsnab.userservice.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rfsnab.userservice.exceptions.UserDeletionNotAllowedException;
import ru.rfsnab.userservice.models.UserEntity;
import ru.rfsnab.userservice.services.UserService;
import ru.rfsnab.userservice.services.client.OrderServiceClient;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminUserController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UserService userService;
    private final OrderServiceClient orderServiceClient;

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String callerUserId,
            @RequestHeader("X-User-Role") String callerRole) {

        if (callerUserId != null && callerUserId.equals(String.valueOf(id))) {
            throw new UserDeletionNotAllowedException("Нельзя удалить собственный аккаунт");
        }

        UserEntity user = userService.findUserById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с id " + id + " не найден"));

        boolean targetIsAdmin = user.getRoles().stream()
                .anyMatch(r -> ROLE_ADMIN.equals(r.getName()));
        if (targetIsAdmin && userService.countAdmins() <= 1) {
            throw new UserDeletionNotAllowedException("Нельзя удалить последнего администратора");
        }

        long activeOrders = orderServiceClient.countActiveOrdersByUserId(id, callerUserId, callerRole);
        if (activeOrders > 0) {
            throw new UserDeletionNotAllowedException(
                    "Нельзя удалить пользователя с активными заказами (" + activeOrders + ")");
        }

        userService.deleteUser(id);
        log.info("Пользователь {} удалён администратором {}", id, callerUserId);
        return ResponseEntity.noContent().build();
    }
}
