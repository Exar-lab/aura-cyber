package com.auracyber.memorygraph.qdrant;

import com.auracyber.memorygraph.domain.BehavioralEmbeddingIndex;
import com.auracyber.memorygraph.domain.EmbeddingIndexException;
import com.auracyber.memorygraph.domain.EmbeddingMatch;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.QueryPoints;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

/**
 * Behavioral embedding index backed by a single Qdrant collection, compared
 * by cosine similarity.
 */
public class QdrantBehavioralEmbeddingIndex implements BehavioralEmbeddingIndex {

	static final String EMBEDDING_ID_PAYLOAD_KEY = "embedding_id";

	private final QdrantClient client;
	private final String collectionName;

	public QdrantBehavioralEmbeddingIndex(QdrantClient client, String collectionName) {
		this.client = client;
		this.collectionName = collectionName;
	}

	/**
	 * Creates the backing collection for embeddings of the given dimension unless it already exists.
	 *
	 * @throws EmbeddingIndexException when Qdrant rejects or cannot serve the request.
	 */
	public void createCollectionIfAbsent(int dimensions) {
		if (await(client.collectionExistsAsync(collectionName), "check collection " + collectionName)) {
			return;
		}
		VectorParams vectorParams = VectorParams.newBuilder()
				.setSize(dimensions)
				.setDistance(Distance.Cosine)
				.build();
		await(client.createCollectionAsync(collectionName, vectorParams), "create collection " + collectionName);
	}

	@Override
	public void upsert(String id, float[] embedding) {
		PointStruct point = PointStruct.newBuilder()
				.setId(id(pointId(id)))
				.setVectors(vectors(embedding))
				.putPayload(EMBEDDING_ID_PAYLOAD_KEY, value(id))
				.build();
		await(client.upsertAsync(collectionName, List.of(point)), "upsert embedding " + id);
	}

	@Override
	public List<EmbeddingMatch> nearest(float[] embedding, int limit) {
		QueryPoints query = QueryPoints.newBuilder()
				.setCollectionName(collectionName)
				.setQuery(QueryFactory.nearest(embedding))
				.setLimit(limit)
				.setWithPayload(enable(true))
				.build();
		return await(client.queryAsync(query), "search nearest embeddings").stream()
				.map(point -> new EmbeddingMatch(
						point.getPayloadOrThrow(EMBEDDING_ID_PAYLOAD_KEY).getStringValue(), point.getScore()
				))
				.toList();
	}

	/**
	 * Qdrant only accepts unsigned integers or UUIDs as point ids, so the
	 * caller's free-form id is mapped to a deterministic name-based UUID and
	 * the original is kept in the point payload.
	 */
	static UUID pointId(String id) {
		return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
	}

	private <T> T await(ListenableFuture<T> future, String operation) {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new EmbeddingIndexException("Interrupted during Qdrant " + operation, e);
		} catch (ExecutionException e) {
			throw new EmbeddingIndexException("Qdrant failed to " + operation, e.getCause());
		}
	}
}
