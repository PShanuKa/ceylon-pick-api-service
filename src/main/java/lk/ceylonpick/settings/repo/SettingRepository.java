package lk.ceylonpick.settings.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import lk.ceylonpick.settings.domain.Setting;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
