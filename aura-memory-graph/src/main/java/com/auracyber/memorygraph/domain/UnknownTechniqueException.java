package com.auracyber.memorygraph.domain;

public class UnknownTechniqueException extends RuntimeException {

	public UnknownTechniqueException(String techniqueId) {
		super("TTP " + techniqueId + " is not recorded in the Immune Memory Graph");
	}
}
