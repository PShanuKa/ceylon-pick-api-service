package lk.ceylonpick.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.api.AdminRole;
import lk.ceylonpick.auth.repo.AdminProfileRepository;

/**
 * Seeds the first OWNER.
 *
 * <p>Admin accounts can only be created by an OWNER, so an empty database has
 * no way in. This closes that loop once and then never fires again: it does
 * nothing unless {@code ceylonpick.auth.bootstrap.enabled} is on <em>and</em>
 * no admin profile exists at all.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProfileRepository adminProfiles;
    private final AdminUserService adminUsers;
    private final AuthProperties properties;

    public AdminBootstrap(AdminProfileRepository adminProfiles,
                          AdminUserService adminUsers,
                          AuthProperties properties) {
        this.adminProfiles = adminProfiles;
        this.adminUsers = adminUsers;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.Bootstrap config = properties.bootstrap();
        if (config == null || !config.enabled()) {
            return;
        }
        if (adminProfiles.count() > 0) {
            log.debug("Admin bootstrap skipped: an admin already exists");
            return;
        }
        if (config.email() == null || config.password() == null || config.phone() == null) {
            log.error("Admin bootstrap is enabled but email, password or phone is missing");
            return;
        }
        adminUsers.create(config.email(), config.password(), config.phone(), config.fullName(),
                AdminRole.OWNER, null, null);
        log.warn("Seeded the first OWNER admin: {}. Change this password immediately.", config.email());
    }
}
