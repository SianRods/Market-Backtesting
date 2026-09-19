package com.rods.backtestingstrategies.domain;

/** Signals an invalid domain input before a misleading backtest can be produced. */
public final class DomainValidationException extends IllegalArgumentException {

    public DomainValidationException(String message) {
        super(message);
    }
}
