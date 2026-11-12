package com.auracyber.memorygraph.domain;

import java.util.Objects;

/** An indicator of compromise, identified by its type and observed value. */
public record Ioc(IocType type, String value) {

	public Ioc {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(value, "value");
	}
}
