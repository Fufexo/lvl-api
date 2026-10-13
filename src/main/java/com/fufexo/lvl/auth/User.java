package com.fufexo.lvl.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fufexo.lvl.shared.AssignedIdEntity;

@Entity
@Table(name = "users")
class User extends AssignedIdEntity {

	private String gamertag;

	private String email;

	private String passwordHash;

	private Instant createdAt;

	protected User() {
	}

	User(UUID id, String gamertag, String email, String passwordHash, Instant createdAt) {
		super(id);
		this.gamertag = gamertag;
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	UUID id() {
		return getId();
	}

	String gamertag() {
		return gamertag;
	}

	String email() {
		return email;
	}

	String passwordHash() {
		return passwordHash;
	}

}
