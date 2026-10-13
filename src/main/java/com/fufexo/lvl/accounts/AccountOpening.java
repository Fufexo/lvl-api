package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.fufexo.lvl.auth.UserRegistered;
import com.fufexo.lvl.shared.Amounts;
import com.fufexo.lvl.shared.CurrencyCode;
import com.fufexo.lvl.shared.Ids;
import com.fufexo.lvl.shared.Instants;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a PEN and a USD account for every new user and credits the welcome bonus.
 *
 * <p>
 * Runs synchronously inside the sign-up transaction on purpose: a user without accounts would be a
 * broken state, so if this fails the sign-up is rolled back too.
 */
@Component
class AccountOpening {

	private final AccountRepository accounts;

	private final TransferRepository transfers;

	private final Ledger ledger;

	private final WelcomeBonusProperties bonus;

	private final Clock clock;

	AccountOpening(AccountRepository accounts, TransferRepository transfers, Ledger ledger,
			WelcomeBonusProperties bonus, Clock clock) {
		this.accounts = accounts;
		this.transfers = transfers;
		this.ledger = ledger;
		this.bonus = bonus;
		this.clock = clock;
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(UserRegistered event) {
		Instant now = Instants.now(clock);
		Account pen = accounts.saveAndFlush(Account.openFor(event.userId(), CurrencyCode.PEN, now));
		Account usd = accounts.saveAndFlush(Account.openFor(event.userId(), CurrencyCode.USD, now));
		credit(event, pen, bonus.pen(), now);
		credit(event, usd, bonus.usd(), now);
	}

	private void credit(UserRegistered event, Account account, BigDecimal amount, Instant now) {
		long minor = Amounts.toMinor(amount);
		if (minor == 0) {
			return;
		}
		Account treasury = accounts.findById(SystemAccounts.treasury(account.currency()))
			.orElseThrow(() -> new IllegalStateException("Missing treasury for " + account.currency()));
		Transfer transfer = transfers.save(new Transfer(Ids.next(), Transfer.Kind.WELCOME, event.userId(), null, null,
				treasury, minor, account, minor, null, null, now));
		ledger.post(transfer.id(),
				List.of(new Ledger.Leg(treasury.id(), -minor, "Bono de bienvenida para @" + event.gamertag()),
						new Ledger.Leg(account.id(), minor, "Bono de bienvenida")),
				now);
	}

}
