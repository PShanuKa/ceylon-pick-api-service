package lk.ceylonpick.shared;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Architecture §4: the shared kernel owns Clock. Services take it as a
 * dependency rather than calling {@code Instant.now()}, so expiry, lockout and
 * OTP-window behaviour can be tested without sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
