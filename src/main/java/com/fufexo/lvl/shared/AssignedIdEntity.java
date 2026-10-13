package com.fufexo.lvl.shared;

import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * Entities whose UUID is assigned in code (see {@link Ids}). Without this, Spring Data sees a
 * non-null id, assumes the row exists and runs merge, an extra SELECT before every insert.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean isNew = true;

	protected AssignedIdEntity() {
	}

	protected AssignedIdEntity(UUID id) {
		this.id = id;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		isNew = false;
	}

}
