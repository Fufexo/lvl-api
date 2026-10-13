package com.fufexo.lvl.accounts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Movements are paginated by keyset (created_at, id) instead of OFFSET: every page costs the
 * same, and new movements arriving while the user scrolls do not shift or duplicate rows.
 */
interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	@Query("""
			select new com.fufexo.lvl.accounts.Movement(e.id, e.transferId, t.kind, e.description, e.amountMinor,
				e.currency, e.balanceAfterMinor, e.createdAt)
			from LedgerEntry e join Transfer t on t.id = e.transferId
			where e.accountId = :accountId
			order by e.createdAt desc, e.id desc
			""")
	List<Movement> latest(UUID accountId, Limit limit);

	@Query("""
			select new com.fufexo.lvl.accounts.Movement(e.id, e.transferId, t.kind, e.description, e.amountMinor,
				e.currency, e.balanceAfterMinor, e.createdAt)
			from LedgerEntry e join Transfer t on t.id = e.transferId
			where e.accountId = :accountId
				and (e.createdAt < :createdAt or (e.createdAt = :createdAt and e.id < :id))
			order by e.createdAt desc, e.id desc
			""")
	List<Movement> before(UUID accountId, Instant createdAt, UUID id, Limit limit);

	@Query("select e.createdAt from LedgerEntry e where e.id = :id and e.accountId = :accountId")
	Optional<Instant> createdAtOf(UUID id, UUID accountId);

}
