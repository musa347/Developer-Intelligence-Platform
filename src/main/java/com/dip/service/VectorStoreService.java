package com.dip.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final QdrantClient qdrantClient;

    @org.springframework.beans.factory.annotation.Value("${qdrant.collection-name}")
    private String collectionName;

    @PostConstruct
    public void init() throws ExecutionException, InterruptedException {
        if (!collectionExists()) {
            System.out.println("Creating Qdrant collection: " + collectionName);
            createCollection();
            System.out.println("Collection created successfully with indexes");
        } else {
            System.out.println("Collection already exists: " + collectionName);
        }
    }

    private boolean collectionExists() throws ExecutionException, InterruptedException {
        try {
            qdrantClient.getCollectionInfoAsync(collectionName).get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createCollection() throws ExecutionException, InterruptedException {
        qdrantClient.createCollectionAsync(
                collectionName,
                VectorParams.newBuilder()
                        .setSize(768)
                        .setDistance(Distance.Cosine)
                        .build()
        ).get();
        qdrantClient.createPayloadIndexAsync(
                collectionName,
                "service_id",
                PayloadSchemaType.Integer,
                null,
                null,
                null,
                null
        ).get();
        qdrantClient.createPayloadIndexAsync(
                collectionName,
                "chunk_type",
                PayloadSchemaType.Keyword,
                null,
                null,
                null,
                null
        ).get();
    }

    public void upsertVector(String id, float[] vector, Map<String, Object> payload)
            throws ExecutionException, InterruptedException {

        PointStruct point = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(id).build())
                .setVectors(Vectors.newBuilder().setVector(
                        io.qdrant.client.grpc.Points.Vector.newBuilder().addAllData(toList(vector)).build()
                ).build())
                .putAllPayload(toPayload(payload))
                .build();

        qdrantClient.upsertAsync(collectionName, List.of(point)).get();
    }

    public List<String> searchSimilar(float[] queryVector, Long serviceId, int limit, com.dip.domain.ChunkType chunkTypeFilter)
            throws ExecutionException, InterruptedException {

        Filter.Builder filterBuilder = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("service_id")
                                .setMatch(Match.newBuilder().setInteger(serviceId).build())
                                .build())
                        .build());

        if (chunkTypeFilter != null) {
            filterBuilder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey("chunk_type")
                            .setMatch(Match.newBuilder().setKeyword(chunkTypeFilter.name()).build())
                            .build())
                    .build());
        }

        List<ScoredPoint> results = qdrantClient.searchAsync(
                SearchPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .addAllVector(toList(queryVector))
                        .setLimit(limit)
                        .setFilter(filterBuilder.build())
                        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                        .build()
        ).get();

        return results.stream()
                .map(point -> point.getPayloadOrThrow("chunk_id").getStringValue())
                .toList();
    }

    private List<Float> toList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private Map<String, io.qdrant.client.grpc.JsonWithInt.Value> toPayload(Map<String, Object> map) {
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
        map.forEach((key, value) -> {
            if (value instanceof String) {
                payload.put(key, io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                        .setStringValue((String) value).build());
            } else if (value instanceof Long) {
                payload.put(key, io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                        .setIntegerValue((Long) value).build());
            }
        });
        return payload;
    }
}
