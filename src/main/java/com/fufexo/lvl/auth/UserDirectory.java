package com.fufexo.lvl.auth;

import java.util.Optional;
import java.util.UUID;

/** Read-only lookup of users for other modules. */
public interface UserDirectory {

	/** Case-insensitive. */
	Optional<UUID> findIdByGamertag(String gamertag);

	Optional<String> gamertagOf(UUID userId);

}
