package com.fufexo.lvl.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

	private final UserRepository users;

	UserDirectoryService(UserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<UUID> findIdByGamertag(String gamertag) {
		return users.findByGamertag(gamertag.strip()).map(User::id);
	}

	@Override
	public Optional<String> gamertagOf(UUID userId) {
		return users.findById(userId).map(User::gamertag);
	}

}
