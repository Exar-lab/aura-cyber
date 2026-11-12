package com.auracyber.memorygraph.domain;

import java.util.List;

/**
 * Port for similarity search over behavioral embeddings (vectorized
 * telemetry/attack behavior). Implemented by a vector-store adapter.
 */
public interface BehavioralEmbeddingIndex {

	/**
	 * Stores the embedding under {@code id}, replacing any previous one.
	 *
	 * @throws EmbeddingIndexException when the vector store rejects or cannot serve the request.
	 */
	void upsert(String id, float[] embedding);

	/**
	 * @return up to {@code limit} stored embeddings closest to {@code embedding}, best match first.
	 * @throws EmbeddingIndexException when the vector store rejects or cannot serve the request.
	 */
	List<EmbeddingMatch> nearest(float[] embedding, int limit);
}
