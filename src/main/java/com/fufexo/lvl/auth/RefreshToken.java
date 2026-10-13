package com.fufexo.lvl.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.shared.AssignedIdEntity;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken extends AssignedIdEntity {

	private UUID userId;

	private UUID familyId;

	private String tokenHash;

	private Instant expiresAt;

	@Nullable
	private Instant usedAt;

	@Nullable
	private Instant revokedAt;

	private Instant createdAt;

	protected RefreshToken() {
	}

	RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant createdAt) {
		super(id);
		this.userId = userId;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	UUID userId() {
		return userId;
	}

	UUID familyId() {
		return familyId;
	}

	/** Already rotated or revoked: presenting it again means it was replayed or stolen. */
	boolean isSpent() {
		return usedAt != null || revokedAt != null;
	}

	boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	void markUsed(Instant now) {
		usedAt = now;
	}

}
