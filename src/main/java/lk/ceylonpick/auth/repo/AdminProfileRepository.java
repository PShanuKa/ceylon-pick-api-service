package lk.ceylonpick.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.auth.api.AdminRole;
import lk.ceylonpick.auth.domain.AdminProfile;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, String> {

    /** Used to refuse demoting or disabling the last OWNER. */
    long countByAdminRole(AdminRole adminRole);
}
