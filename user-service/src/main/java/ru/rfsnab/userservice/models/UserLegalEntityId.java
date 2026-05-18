package ru.rfsnab.userservice.models;

import java.io.Serializable;
import java.util.Objects;

/**
 * Составной первичный ключ для {@link UserLegalEntity}: пара (userId, legalEntityId).
 */
public class UserLegalEntityId implements Serializable {
    private Long user;
    private Long legalEntity;

    public UserLegalEntityId() {}

    public UserLegalEntityId(Long user, Long legalEntity) {
        this.user = user;
        this.legalEntity = legalEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserLegalEntityId that)) return false;
        return Objects.equals(user, that.user) && Objects.equals(legalEntity, that.legalEntity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, legalEntity);
    }
}
