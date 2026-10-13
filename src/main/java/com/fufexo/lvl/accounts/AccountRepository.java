package com.fufexo.lvl.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fufexo.lvl.shared.CurrencyCode;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {

	List<Account> findByOwnerIdOrderByCurrency(UUID ownerId);

	/** Scoped by owner: another user's account id simply does not exist for you. */
	Optional<Account> findByIdAndOwnerId(UUID id, UUID ownerId);

	Optional<Account> findByOwnerIdAndCurrency(UUID ownerId, CurrencyCode currency);

}
