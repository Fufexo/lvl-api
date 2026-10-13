package com.fufexo.lvl.fx;

/** Current USD → PEN exchange rate. Never throws: falls back to the last known or a default rate. */
public interface ExchangeRates {

	FxQuote usdToPen();

}
