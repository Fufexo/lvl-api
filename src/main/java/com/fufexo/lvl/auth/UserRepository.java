package com.fufexo.lvl.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// lower() matches the functional unique indexes, so these lookups use them.
interface UserRepository extends JpaRepository<User, UUID> {

	@Query("select u from User u where lower(u.gamertag) = lower(:gamertag)")
	Optional<User> findByGamertag(String gamertag);

	@Query("select u from User u where lower(u.email) = lower(:email)")
	Optional<User> findByEmail(String email);

}
