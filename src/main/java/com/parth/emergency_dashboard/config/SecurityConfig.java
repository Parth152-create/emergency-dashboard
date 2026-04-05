
package com.parth.emergency_dashboard.config;

import com.parth.emergency_dashboard.model.User;
import com.parth.emergency_dashboard.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ✅ PASSWORD ENCODER
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ✅ USER DETAILS SERVICE
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(user.getRole().replace("ROLE_", ""))
                    .build();
        };
    }

    // ✅ SECURITY CONFIGURATION (FIXED)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // ✅ PUBLIC ROUTES
                .requestMatchers(
                    "/customer.html",
                    "/login",
                    "/login/**",
                    "/index.html",   // ✅ IMPORTANT FIX
                    "/error",        // ✅ IMPORTANT FIX
                    "/ws/**",
                    "/css/**", "/js/**", "/images/**",
                    "/favicon.ico"
                ).permitAll()

                // ✅ PUBLIC API (customer side)
                .requestMatchers(
                    "/api/emergencies",
                    "/api/emergencies/track/**"
                ).permitAll()

                // 🔒 EVERYTHING ELSE PROTECTED
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/index.html", true) // ✅ FIXED REDIRECT
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            );

        return http.build();
    }

    // ✅ SEED ADMIN USER
    @Bean
    public CommandLineRunner seedAdmin(PasswordEncoder encoder) {
        return args -> {
            if (userRepository.count() == 0) {
                User admin = new User(
                    "admin",
                    encoder.encode("admin123"),
                    "ROLE_ADMIN"
                );
                userRepository.save(admin);
                System.out.println("✅ Default admin created — username: admin, password: admin123");
            }
        };
    }
}
