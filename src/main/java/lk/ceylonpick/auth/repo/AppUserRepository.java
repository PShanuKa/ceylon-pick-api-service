package lk.ceylonpick.auth.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.api.Role;
import lk.ceylonpick.auth.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    /** Callers must lower-case first; the column holds only lower-cased values. */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Phone is unique per buyer only ({@code ux_app_user_phone_buyer}); staff may
     * share a phone with their own buyer identity, so the role must be given.
     */
    Optional<AppUser> findByPhoneAndRole(String phone, Role role);
}
