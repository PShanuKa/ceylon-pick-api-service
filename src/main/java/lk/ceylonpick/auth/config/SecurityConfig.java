package lk.ceylonpick.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import lk.ceylonpick.auth.web.ApiError;
import tools.jackson.databind.ObjectMapper;

/**
 * NFR-05: HTTPS only, JWT in httpOnly SameSite cookies, CSRF protection,
 * OWASP Top 10 checklist.
 *
 * <p>CSRF is on because authentication is by cookie: the browser attaches the
 * session to any cross-site request, so the token is what proves the request
 * came from our own front end. The PayHere IPN is exempt — it is a
 * server-to-server callback authenticated by its own signature (IF-02).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Public browsing: UI Spec §1 lists these areas as needing no login. */
    private static final String[] PUBLIC_GET = {
            "/", "/c/**", "/p/**", "/@*/**", "/track/**",
            "/api/v1/public/**",
            "/actuator/health", "/actuator/health/**", "/actuator/info"
    };

    /** Sign-in and account-recovery endpoints, which by definition run unauthenticated. */
    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/login",
            "/api/v1/auth/login/otp",
            "/api/v1/auth/otp/request",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/otp/resend",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/register",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegating, so hashes carry a {bcrypt} prefix and the algorithm can be
        // upgraded later without invalidating existing passwords.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtCookieAuthenticationFilter jwtFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        // Readable by script on purpose: the front end copies it into
                        // the X-XSRF-TOKEN header. The session cookies stay httpOnly.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/webhooks/**"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Not signed in: 401 with the same JSON envelope the controllers use. */
    private AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, authException) ->
                write(mapper, response, 401, ApiError.of("UNAUTHENTICATED", "Sign in to continue"));
    }

    /** Signed in but not entitled: 403 (FR-AUTH-03). */
    private AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, deniedException) ->
                write(mapper, response, 403, ApiError.of("FORBIDDEN", "You do not have access to this"));
    }

    private static void write(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response,
                              int status, ApiError body) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body);
    }
}
