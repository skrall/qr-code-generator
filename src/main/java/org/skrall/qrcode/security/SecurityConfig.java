package org.skrall.qrcode.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authentication for the whole application, selected by {@code app.security.mode}. The design
 * is mailoverlord's, so the two deploy the same way.
 *
 * <p>{@code none} reproduces the pre-security behaviour, which is what the general test suite
 * runs under. {@code basic} requires HTTP Basic, answers 401/403 as RFC 9457 problem details, and
 * does so without a session: Basic carries the credential on every request, so there is no
 * server-side session to protect or invalidate.
 *
 * <p>{@code oidc} signs browsers in against an identity provider through the authorization code
 * flow. The SPA only ever holds a session cookie; the provider endpoints are read from the
 * registration's {@code issuer-uri}, so Okta and Entra ID are configuration rather than code.
 * The session is a cookie, so the chain keeps CSRF protection, in its single-page-app form: the
 * UI echoes the {@code XSRF-TOKEN} cookie back as {@code X-XSRF-TOKEN}.
 *
 * <p>{@code header} mode believes the identity a reverse proxy asserts, but only when the
 * connection itself came from a {@code trusted-proxies} CIDR. Any other request is answered 401
 * whatever it carries, because a header anyone can send is no proof of anything.
 *
 * <p>Every mode applies the same rules ({@link #authorize}). The short-link redirects are public:
 * a phone scanning a printed code cannot sign in. So are health, info and the OpenAPI document.
 * Everything else, the UI included, needs a signed-in user, and changing a short link needs the
 * OPERATOR role, enforced by method security on the link controller.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Method security is for the authenticated modes only: under {@code none} the link
     * endpoints stay exactly as they were, including for test requests that carry no identity.
     * A condition rather than a property-conditional annotation, because the default mode is
     * {@code basic} even when the property is left out entirely.
     */
    @Configuration
    @EnableMethodSecurity
    @Conditional(ModeIsNotNone.class)
    static class MethodSecurityConfiguration {
    }

    /**
     * Each condition reads the raw {@code app.security.mode} property. Anything unrecognised is
     * treated as {@code basic}, matching the default.
     */
    private abstract static class ModeCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("app.security.mode");
            return matchesMode(mode == null ? "" : mode.trim().toLowerCase());
        }

        protected abstract boolean matchesMode(String mode);
    }

    /** Mode names, or defaults to, {@code basic} or {@code none}. */
    static final class ModeIsClassic extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return !"oidc".equals(mode) && !"header".equals(mode);
        }
    }

    /** Mode is {@code oidc}. */
    static final class ModeIsOidc extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return "oidc".equals(mode);
        }
    }

    /** Mode is {@code header}: trust the identity headers of a configured proxy only. */
    static final class ModeIsHeader extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return "header".equals(mode);
        }
    }

    /** Mode is anything but {@code none}. */
    static final class ModeIsNotNone extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return !"none".equals(mode);
        }
    }

    /**
     * The rules every authenticated mode shares. Kept in one place so the four chains cannot
     * drift: a path made public in one mode and forgotten in another is the kind of difference
     * nobody notices until a printed code stops working.
     */
    private static void authorize(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules
                // A scan comes from a phone that has never signed in, and the code is already
                // printed: the redirect has to answer anyone.
                .requestMatchers("/r/**").permitAll()
                // The OpenAPI contract describes the API, not the data it guards.
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                // Probes read health and info without a credential. Anything else exposed on
                // the management plane would show internals, so it is an OPERATOR privilege.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("OPERATOR")
                .anyRequest().authenticated();
    }

    @Bean
    @Conditional(ModeIsClassic.class)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppSecurityProperties properties,
            AuthenticationEntryPoint problemDetailEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                // Basic carries the credential on every request: there is no ambient cookie to
                // abuse, and no session for a login form to drag in.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailEntryPoint)
                        .accessDeniedHandler(deniedHandler));

        if (properties.mode() == AppSecurityProperties.Mode.NONE) {
            http.authorizeHttpRequests(rules -> rules.anyRequest().permitAll());
        } else {
            http.httpBasic(Customizer.withDefaults())
                    .authorizeHttpRequests(SecurityConfig::authorize);
        }
        return http.build();
    }

    /**
     * The OIDC chain: the identity-provider login, a session of the SPA's own, and (because the
     * session is a cookie) CSRF protection against state-changing requests from elsewhere.
     *
     * <p>{@code csrf.spa()} rather than a bare cookie repository: the default request handler
     * masks the token against BREACH and so expects a different value from the one in the
     * cookie, which an SPA echoing the cookie can never send. {@code spa()} pairs the readable
     * cookie with a handler that accepts the raw value from the header, and makes sure the cookie
     * is written on the first response rather than only once a page has asked for the token.
     */
    @Bean
    @Conditional(ModeIsOidc.class)
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, OidcUserService oidcUserService,
            AuthenticationEntryPoint oidcEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.csrf(csrf -> csrf.spa())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(login -> login.userInfoEndpoint(
                        userInfo -> userInfo.oidcUserService(oidcUserService)))
                .logout(logout -> logout
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(oidcEntryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(SecurityConfig::authorize);
        return http.build();
    }

    /**
     * Answers a request that reached a guarded path without credentials as RFC 9457.
     *
     * <p>The {@code WWW-Authenticate} header is kept, because it is what makes a top-level
     * navigation pop the browser's Basic prompt. The {@code login-url} property is where the SPA
     * sends the browser when a {@code fetch} gets the 401, since a fetch never shows the prompt.
     */
    @Bean
    AuthenticationEntryPoint problemDetailEntryPoint(JsonMapper jsonMapper) {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"qr-code-generator\"");
            writeProblem(jsonMapper, response, HttpStatus.UNAUTHORIZED,
                    "Authentication is required. Sign in with the configured user credentials.",
                    Map.of("login-url", "/login"));
        };
    }

    /**
     * The OIDC answers to an unauthenticated request. An API call gets an RFC 9457 401 whose
     * {@code login-url} restarts the provider login, because a {@code fetch} that follows a
     * redirect to the provider's origin fails on CORS. A top-level navigation gets an ordinary
     * redirect to the provider (or, with several registrations, to the page that lists them).
     */
    @Bean
    @Conditional(ModeIsOidc.class)
    AuthenticationEntryPoint oidcEntryPoint(JsonMapper jsonMapper, OAuth2ClientProperties clients) {
        List<String> registrationIds = new ArrayList<>(clients.getRegistration().keySet());
        String loginUrl = registrationIds.size() == 1
                ? "/oauth2/authorization/" + registrationIds.get(0)
                : "/login";
        Map<String, Object> apiProperties = Map.of("login-url", loginUrl);
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(
                        (request, response, exception) -> writeProblem(jsonMapper, response,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required. Sign in through the configured "
                                        + "identity provider.",
                                apiProperties),
                        PathPatternRequestMatcher.pathPattern("/api/**"))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(loginUrl))
                .build();
    }

    /**
     * The trusted-header chain: it only ever matches a request whose connection came from a
     * {@link TrustedProxyRequestMatcher configured proxy}, and treats the identity headers on
     * those requests as authenticated. Stateless like Basic: the proxy authenticates, and the
     * app holds no session of its own.
     */
    @Bean
    @Order(1)
    @Conditional(ModeIsHeader.class)
    SecurityFilterChain trustedHeaderSecurityFilterChain(HttpSecurity http,
            TrustedProxyRequestMatcher trustedProxyMatcher,
            TrustedHeaderAuthenticationFilter trustedHeaderFilter,
            AuthenticationEntryPoint trustedHeaderEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.securityMatcher(trustedProxyMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(trustedHeaderFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(trustedHeaderEntryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(SecurityConfig::authorize);
        return http.build();
    }

    /**
     * The other half of the boundary: any request that a trusted proxy did not make. The
     * identity header is never read here, which is what keeps the mode from being forgeable by
     * anyone who can reach the port directly. The public paths still answer, so a scan that
     * reaches the app without passing the proxy is redirected as usual.
     */
    @Bean
    @Order(2)
    @Conditional(ModeIsHeader.class)
    SecurityFilterChain proxyBoundarySecurityFilterChain(HttpSecurity http,
            TrustedProxyRequestMatcher trustedProxyMatcher,
            AuthenticationEntryPoint trustedHeaderEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.securityMatcher(new NegatedRequestMatcher(trustedProxyMatcher))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(trustedHeaderEntryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(SecurityConfig::authorize);
        return http.build();
    }

    /**
     * The header-mode answer to an unauthenticated request. There is nowhere to send the
     * browser: the proxy owns sign-in. So the SPA gets a problem detail with no
     * {@code login-url} and shows it, and there is no {@code WWW-Authenticate}, because the app
     * accepts no credentials of its own.
     */
    @Bean
    @Conditional(ModeIsHeader.class)
    AuthenticationEntryPoint trustedHeaderEntryPoint(JsonMapper jsonMapper,
            AppSecurityProperties properties) {
        return (request, response, exception) -> writeProblem(jsonMapper, response,
                HttpStatus.UNAUTHORIZED,
                "Authentication is required. The reverse proxy must set the "
                        + properties.header() + " header, and the request must have reached the "
                        + "app from a trusted proxy.",
                Map.of());
    }

    /** The source allowlist shared by both header-mode chains. */
    @Bean
    @Conditional(ModeIsHeader.class)
    TrustedProxyRequestMatcher trustedProxyMatcher(AppSecurityProperties properties) {
        return new TrustedProxyRequestMatcher(properties.trustedProxies());
    }

    /** Reads the identity and groups headers into the mapped authorities. */
    @Bean
    @Conditional(ModeIsHeader.class)
    TrustedHeaderAuthenticationFilter trustedHeaderFilter(AppSecurityProperties properties) {
        return new TrustedHeaderAuthenticationFilter(properties.header(),
                properties.groupsHeader(), new GroupRolesMapper(properties.operatorGroups()));
    }

    /**
     * Answers a request that was refused after authentication as RFC 9457: a VIEWER changing a
     * short link, or (under OIDC) a change that arrived without its CSRF token. Both are 403, but
     * the remedy differs, so the detail says which it was.
     */
    @Bean
    AccessDeniedHandler problemDetailDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, exception) -> writeProblem(jsonMapper, response,
                HttpStatus.FORBIDDEN,
                exception instanceof CsrfException
                        ? "The request was missing its CSRF token. Reload the page and try again."
                        : "That needs the operator role.",
                Map.of());
    }

    private static void writeProblem(JsonMapper jsonMapper, HttpServletResponse response,
            HttpStatus status, String detail, Map<String, Object> properties) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            problem.setProperty(entry.getKey(), entry.getValue());
        }
        jsonMapper.writeValue(response.getWriter(), problem);
    }

    /** The OIDC user loader that stamps the group-derived roles on the identity. */
    @Bean
    @Conditional(ModeIsOidc.class)
    OidcUserService oidcUserService(AppSecurityProperties properties) {
        return new OidcRolesUserService(
                new OidcRolesMapper(properties.rolesClaim(), properties.operatorGroups()));
    }

    /**
     * Encodes the shared {@code spring.security.user.password}. Spring Security 7 removed the
     * default encoder, so without an explicit one every {@code User} built here would refuse to
     * load.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The Basic identities, all sharing {@code spring.security.user.password}.
     *
     * <p>The {@code spring.security.user.*} identity is OPERATOR, so the documented user can run
     * the whole tool. Named users opt into less or more by the list they appear in; an OPERATOR
     * also holds VIEWER.
     */
    @Bean
    UserDetailsService userDetailsService(SecurityProperties springUser,
            AppSecurityProperties properties, PasswordEncoder encoder) {
        String encodedPassword = encoder.encode(springUser.getUser().getPassword());
        Set<String> operators = new LinkedHashSet<>();
        for (String operator : properties.operatorUsers()) {
            operators.add(operator.trim().toLowerCase());
        }
        Set<String> names = new LinkedHashSet<>(operators);
        for (String viewer : properties.viewerUsers()) {
            names.add(viewer.trim().toLowerCase());
        }

        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager(
                User.withUsername(springUser.getUser().getName())
                        .password(encodedPassword)
                        .roles("OPERATOR", "VIEWER")
                        .build());
        for (String name : names) {
            if (name.isEmpty() || manager.userExists(name)) {
                continue;
            }
            User.UserBuilder user = User.withUsername(name).password(encodedPassword);
            if (operators.contains(name)) {
                user.roles("OPERATOR", "VIEWER");
            } else {
                user.roles("VIEWER");
            }
            manager.createUser(user.build());
        }
        return manager;
    }

    /**
     * Where a 401 sends the browser under Basic: a {@code fetch} never shows the Basic prompt,
     * so a top-level navigation to /login is what makes the browser ask. The path serves the SPA,
     * and its next API calls carry the credentials the browser now caches. Under OIDC with
     * several providers, the generated provider-chooser page claims the same path first.
     */
    @Bean
    WebMvcConfigurer loginView() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/login").setViewName("forward:/index.html");
            }
        };
    }
}
