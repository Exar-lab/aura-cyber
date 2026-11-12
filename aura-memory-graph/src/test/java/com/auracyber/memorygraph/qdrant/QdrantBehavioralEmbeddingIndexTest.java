package com.auracyber.memorygraph.qdrant;

import com.auracyber.memorygraph.domain.EmbeddingIndexException;
import com.auracyber.memorygraph.domain.EmbeddingMatch;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CollectionExists;
import io.qdrant.client.grpc.Collections.CollectionExistsRequest;
import io.qdrant.client.grpc.Collections.CollectionExistsResponse;
import io.qdrant.client.grpc.Collections.CollectionOperationResponse;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.CollectionsGrpc;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsOperationResponse;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.QueryResponse;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;
import io.qdrant.client.grpc.Points.UpsertPoints;
import io.qdrant.client.grpc.PointsGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the adapter through the real Qdrant gRPC client against an
 * in-process gRPC server standing in for Qdrant, so the wire-level request
 * and response shapes are exercised without a running Qdrant instance.
 */
class QdrantBehavioralEmbeddingIndexTest {

	private static final String COLLECTION = "behavioral-embeddings";

	private final FakePointsService points = new FakePointsService();
	private final FakeCollectionsService collections = new FakeCollectionsService();
	private Server server;
	private QdrantClient client;
	private QdrantBehavioralEmbeddingIndex index;

	@BeforeEach
	void setUp() throws IOException {
		String serverName = InProcessServerBuilder.generateName();
		server = InProcessServerBuilder.forName(serverName)
				.directExecutor()
				.addService(points)
				.addService(collections)
				.build()
				.start();
		ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
		client = new QdrantClient(QdrantGrpcClient.newBuilder(channel, true, false).build());
		index = new QdrantBehavioralEmbeddingIndex(client, COLLECTION);
	}

	@AfterEach
	void tearDown() {
		client.close();
		server.shutdownNow();
	}

	@Test
	void upsertStoresVectorUnderDeterministicPointIdKeepingOriginalIdInPayload() {
		index.upsert("sentinel-1/flow-42", new float[] {0.1f, 0.2f, 0.3f});

		assertEquals(1, points.upserts.size());
		UpsertPoints request = points.upserts.getFirst();
		assertEquals(COLLECTION, request.getCollectionName());
		assertEquals(1, request.getPointsCount());
		PointStruct point = request.getPoints(0);
		assertEquals(
				QdrantBehavioralEmbeddingIndex.pointId("sentinel-1/flow-42").toString(), point.getId().getUuid()
		);
		assertEquals(List.of(0.1f, 0.2f, 0.3f), point.getVectors().getVector().getDataList());
		assertEquals(
				"sentinel-1/flow-42",
				point.getPayloadOrThrow(QdrantBehavioralEmbeddingIndex.EMBEDDING_ID_PAYLOAD_KEY).getStringValue()
		);
	}

	@Test
	void upsertingSameIdTwiceTargetsTheSamePoint() {
		index.upsert("behavior-a", new float[] {1f, 0f});
		index.upsert("behavior-a", new float[] {0f, 1f});

		assertEquals(
				points.upserts.get(0).getPoints(0).getId(),
				points.upserts.get(1).getPoints(0).getId()
		);
	}

	@Test
	void nearestQueriesByVectorAndMapsScoredPointsInServerOrder() {
		points.queryResults.add(scoredPoint("behavior-a", 0.75f));
		points.queryResults.add(scoredPoint("behavior-b", 0.5f));

		List<EmbeddingMatch> matches = index.nearest(new float[] {0.1f, 0.2f, 0.3f}, 5);

		assertEquals(
				List.of(new EmbeddingMatch("behavior-a", 0.75), new EmbeddingMatch("behavior-b", 0.5)), matches
		);
		assertEquals(1, points.queries.size());
		QueryPoints request = points.queries.getFirst();
		assertEquals(COLLECTION, request.getCollectionName());
		assertEquals(5, request.getLimit());
		assertTrue(request.getWithPayload().getEnable());
		assertEquals(List.of(0.1f, 0.2f, 0.3f), request.getQuery().getNearest().getDense().getDataList());
	}

	@Test
	void nearestReturnsNoMatchesWhenIndexIsEmpty() {
		assertEquals(List.of(), index.nearest(new float[] {0.1f, 0.2f, 0.3f}, 5));
	}

	@Test
	void upsertFailureSurfacesAsEmbeddingIndexException() {
		points.failure = Status.UNAVAILABLE.withDescription("qdrant down");

		assertThrows(EmbeddingIndexException.class, () -> index.upsert("behavior-a", new float[] {1f, 0f}));
	}

	@Test
	void nearestFailureSurfacesAsEmbeddingIndexException() {
		points.failure = Status.NOT_FOUND.withDescription("collection missing");

		assertThrows(EmbeddingIndexException.class, () -> index.nearest(new float[] {1f, 0f}, 3));
	}

	@Test
	void createsCosineCollectionOfRequestedDimensionWhenAbsent() {
		collections.exists = false;

		index.createCollectionIfAbsent(384);

		assertEquals(1, collections.created.size());
		CreateCollection request = collections.created.getFirst();
		assertEquals(COLLECTION, request.getCollectionName());
		assertEquals(384, request.getVectorsConfig().getParams().getSize());
		assertEquals(Distance.Cosine, request.getVectorsConfig().getParams().getDistance());
	}

	@Test
	void leavesExistingCollectionUntouched() {
		collections.exists = true;

		index.createCollectionIfAbsent(384);

		assertTrue(collections.created.isEmpty());
	}

	@Test
	void pointIdIsStablePerIdAndDistinctAcrossIds() {
		UUID first = QdrantBehavioralEmbeddingIndex.pointId("behavior-a");

		assertEquals(first, QdrantBehavioralEmbeddingIndex.pointId("behavior-a"));
		assertNotEquals(first, QdrantBehavioralEmbeddingIndex.pointId("behavior-b"));
	}

	private static ScoredPoint scoredPoint(String embeddingId, float score) {
		return ScoredPoint.newBuilder()
				.setId(id(QdrantBehavioralEmbeddingIndex.pointId(embeddingId)))
				.setScore(score)
				.putPayload(QdrantBehavioralEmbeddingIndex.EMBEDDING_ID_PAYLOAD_KEY, value(embeddingId))
				.build();
	}

	private static final class FakePointsService extends PointsGrpc.PointsImplBase {

		private final List<UpsertPoints> upserts = new ArrayList<>();
		private final List<QueryPoints> queries = new ArrayList<>();
		private final List<ScoredPoint> queryResults = new ArrayList<>();
		private Status failure;

		@Override
		public void upsert(UpsertPoints request, StreamObserver<PointsOperationResponse> responseObserver) {
			if (failure != null) {
				responseObserver.onError(failure.asRuntimeException());
				return;
			}
			upserts.add(request);
			responseObserver.onNext(PointsOperationResponse.newBuilder()
					.setResult(UpdateResult.newBuilder().setStatus(UpdateStatus.Completed))
					.build());
			responseObserver.onCompleted();
		}

		@Override
		public void query(QueryPoints request, StreamObserver<QueryResponse> responseObserver) {
			if (failure != null) {
				responseObserver.onError(failure.asRuntimeException());
				return;
			}
			queries.add(request);
			responseObserver.onNext(QueryResponse.newBuilder().addAllResult(queryResults).build());
			responseObserver.onCompleted();
		}
	}

	private static final class FakeCollectionsService extends CollectionsGrpc.CollectionsImplBase {

		private final List<CreateCollection> created = new ArrayList<>();
		private boolean exists;

		@Override
		public void collectionExists(
				CollectionExistsRequest request, StreamObserver<CollectionExistsResponse> responseObserver
		) {
			responseObserver.onNext(CollectionExistsResponse.newBuilder()
					.setResult(CollectionExists.newBuilder().setExists(exists))
					.build());
			responseObserver.onCompleted();
		}

		@Override
		public void create(CreateCollection request, StreamObserver<CollectionOperationResponse> responseObserver) {
			created.add(request);
			responseObserver.onNext(CollectionOperationResponse.newBuilder().setResult(true).build());
			responseObserver.onCompleted();
		}
	}
}
