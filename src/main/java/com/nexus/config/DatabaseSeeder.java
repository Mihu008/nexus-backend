package com.nexus.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Automatically seeds a default test user and profile in Supabase on startup.
 * Satisfies the foreign key constraint by first inserting into auth.users and then public.profiles.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    // A fixed, predictable UUID to make Postman variables work out-of-the-box
    public static final UUID DEFAULT_USER_ID = UUID.fromString("d7b29a24-4f27-4632-bd88-5188f6fa9809");

    @Override
    public void run(String... args) {
        log.info("Starting automated database seeding check...");
        try {
            // Step 1: Check if the default auth user exists in auth.users
            Integer authCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth.users WHERE id = ?::uuid",
                    Integer.class,
                    DEFAULT_USER_ID.toString()
            );

            if (authCount == null || authCount == 0) {
                log.info("Seeding dummy auth user in auth.users (ID: {})...", DEFAULT_USER_ID);
                jdbcTemplate.update(
                        "INSERT INTO auth.users (id, email, raw_app_meta_data, raw_user_meta_data, created_at, updated_at) " +
                        "VALUES (?::uuid, ?, '{\"provider\":\"email\",\"providers\":[\"email\"]}'::jsonb, '{}'::jsonb, NOW(), NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                        DEFAULT_USER_ID.toString(),
                        "test@nexus.com"
                );
                log.info("Auth user successfully seeded.");
            }

            // Step 2: Check if the profile exists in public.profiles
            Integer profileCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.profiles WHERE id = ?::uuid",
                    Integer.class,
                    DEFAULT_USER_ID.toString()
            );

            if (profileCount == null || profileCount == 0) {
                log.info("Seeding default profile in public.profiles (ID: {})...", DEFAULT_USER_ID);
                jdbcTemplate.update(
                        "INSERT INTO public.profiles (id, full_name, avatar_url, updated_at) " +
                        "VALUES (?::uuid, ?, ?, NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                        DEFAULT_USER_ID.toString(),
                        "Default Test User",
                        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde"
                );
                log.info("Profile successfully seeded.");
            }

            log.info("Database seeding verification complete. Ready for API testing!");

        } catch (Exception e) {
            log.error("Failed to execute database seeding: {}", e.getMessage(), e);
        }
    }
}
