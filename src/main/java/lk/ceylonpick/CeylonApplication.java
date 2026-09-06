package lk.ceylonpick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Architecture §4: a modular monolith. Each module is a top-level package under
 * {@code lk.ceylonpick} exposing a small public API; other modules depend on
 * that API, never on entities or repositories.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CeylonApplication {

	public static void main(String[] args) {
		SpringApplication.run(CeylonApplication.class, args);
	}

}
