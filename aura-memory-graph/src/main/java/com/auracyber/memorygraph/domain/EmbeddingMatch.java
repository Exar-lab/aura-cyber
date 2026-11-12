package com.auracyber.memorygraph.domain;

/** A nearest-neighbor hit: the id the embedding was stored under and its similarity score. */
public record EmbeddingMatch(String id, double score) {
}
