package ru.rfsnab.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.rfsnab.userservice.models.UserAddress;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    /** Все адреса пользователя: default первым, затем по дате создания */
    List<UserAddress> findAllByUserIdOrderByDefaultAddressDescCreatedAtAsc(Long userId);

    /** Получить адрес с проверкой принадлежности пользователю */
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    /** Адрес по умолчанию */
    Optional<UserAddress> findByUserIdAndDefaultAddressTrue(Long userId);

    /** Проверка дублирования метки (case-insensitive) */
    boolean existsByUserIdAndLabelIgnoreCase(Long userId, String label);

    /** Проверка дублирования метки — для обновления (исключаем текущий адрес) */
    boolean existsByUserIdAndLabelIgnoreCaseAndIdNot(Long userId, String label, Long id);

    /** Сброс текущего default-адреса перед установкой нового */
    @Modifying
    @Query("UPDATE UserAddress a SET a.defaultAddress = false WHERE a.userId = :userId AND a.defaultAddress = true")
    void resetDefaultForUser(@Param("userId") Long userId);
}
