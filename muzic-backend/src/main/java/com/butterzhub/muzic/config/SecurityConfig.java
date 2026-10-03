package com.butterzhub.muzic.config;

import com.butterzhub.muzic.model.User;
import com.butterzhub.muzic.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.http.ProblemDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Configuration declares beans; EnableWebSecurity installs the servlet security chain.
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final UserRepository users;
    private final ClientRegistrationRepository registrations;
    private final ObjectMapper json;

    public SecurityConfig(UserRepository users, ClientRegistrationRepository registrations, ObjectMapper json) {
        this.users = users;
        this.registrations = registrations;
        this.json = json;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(builder -> builder.additionalParameters(parameters -> {
            parameters.put("access_type", "offline");
            parameters.put("prompt", "consent");
        }));
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("http://localhost:4200"));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return http
            .cors(config -> config.configurationSource(source))
            .authorizeHttpRequests(rules -> rules
                .requestMatchers("/api/recommend", "/", "/error", "/login/**", "/oauth2/**").permitAll()
                .requestMatchers("/api/playlists/**", "/api/youtube/**").authenticated()
                .anyRequest().denyAll())
            // Allow session-authenticated API writes from Postman and browser dev tools without a CSRF token.
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(errors -> errors
                .accessDeniedHandler((request, response, exception) ->
                    problem(request, response, 403, "Access denied"))
                .defaultAuthenticationEntryPointFor((request, response, exception) ->
                    problem(request, response, 401, "Google login required"),
                    request -> request.getServletPath().startsWith("/api/")))
            .oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .successHandler((request, response, authentication) -> {
                OAuth2AuthenticationToken auth = (OAuth2AuthenticationToken) authentication;
                String subject = auth.getPrincipal().getAttribute("sub");
                User user = users.findByGoogleId(subject).orElseGet(User::new);
                user.setGoogleId(subject);
                user.setEmail(auth.getPrincipal().getAttribute("email"));
                user.setName(auth.getPrincipal().getAttribute("name"));
                user.setPictureUrl(auth.getPrincipal().getAttribute("picture"));
                users.save(user);
                response.setContentType("application/json");
                response.getWriter().write("{\"authenticated\":true}");
            }))
            .build();
    }

    private void problem(HttpServletRequest request, HttpServletResponse response, int status, String detail)
        throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatusCode.valueOf(status), detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status);
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), problem);
    }
}
