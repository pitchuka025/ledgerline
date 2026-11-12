package com.ledgerline.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
}
