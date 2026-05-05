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
    public void init() {
        try {
            if (!collectionExists()) {
                System.out.println("Creating Qdrant collection: " + collectionName);
                createCollection();
                System.out.println("Collection created successfully with indexes");
            } else {
                System.out.println("Collection already exists: " + collectionName);
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize Qdrant collection: " + e.getMessage());
            System.err.println("Application will continue but RAG functionality will not be available until Qdrant is configured.");
            // Don't throw exception - allow application to start
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
                        .setSize(1536)
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

        List<Float> vectorList = new ArrayList<>();
        for (float f : vector) {
            vectorList.add(f);
        }

        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap = new HashMap<>();
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (value instanceof String) {
                    payloadMap.put(key, io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                            .setStringValue((String) value).build());
                } else if (value instanceof Number) {
                    payloadMap.put(key, io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                            .setIntegerValue(((Number) value).longValue()).build());
                } else {
                    payloadMap.put(key, io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                            .setStringValue(String.valueOf(value)).build());
                }
            });
        }

        PointStruct point = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(id).build())
                .setVectors(Vectors.newBuilder().setVector(
                        io.qdrant.client.grpc.Points.Vector.newBuilder().addAllData(vectorList).build()
                ).build())
                .putAllPayload(payloadMap)
                .build();

        UpsertPoints upsertPoints = UpsertPoints.newBuilder()
                .setCollectionName(collectionName)
                .addPoints(point)
                .build();

        try {
            var result = qdrantClient.upsertAsync(upsertPoints).get();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public List<String> searchSimilar(float[] queryVector, Long serviceId, int limit, com.dip.domain.ChunkType chunkTypeFilter)
            throws ExecutionException, InterruptedException {

        List<ScoredPoint> scoredPoints = searchSimilarWithScores(queryVector, serviceId, limit, chunkTypeFilter);
        
        return scoredPoints.stream()
                .filter(point -> point.getPayloadMap().containsKey("chunk_id"))
                .map(point -> point.getPayloadMap().get("chunk_id").getStringValue())
                .collect(java.util.stream.Collectors.toList());
    }
    
    public List<ScoredPoint> searchSimilarWithScores(float[] queryVector, Long serviceId, int limit, com.dip.domain.ChunkType chunkTypeFilter)
            throws ExecutionException, InterruptedException {

        System.out.println("[QDRANT DEBUG] Search parameters - serviceId: " + serviceId + ", limit: " + limit + ", chunkTypeFilter: " + chunkTypeFilter);
        System.out.println("[QDRANT DEBUG] Query vector dimensions: " + queryVector.length);
        System.out.println("[QDRANT DEBUG] Collection name: " + collectionName);

        Filter.Builder filterBuilder = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("service_id")
                                .setMatch(Match.newBuilder().setInteger(serviceId).build())
                                .build())
                        .build());

        if (chunkTypeFilter != null) {
            System.out.println("[QDRANT DEBUG] Adding chunk_type filter: " + chunkTypeFilter.name());
            filterBuilder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey("chunk_type")
                            .setMatch(Match.newBuilder().setKeyword(chunkTypeFilter.name()).build())
                            .build())
                    .build());
        } else {
            System.out.println("[QDRANT DEBUG] No chunk_type filter applied - searching all chunk types");
        }

        List<Float> queryVectorList = new ArrayList<>();
        for (float f : queryVector) {
            queryVectorList.add(f);
        }

        try {
            List<ScoredPoint> results = qdrantClient.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addAllVector(queryVectorList)
                            .setLimit(limit)
                            .setFilter(filterBuilder.build())
                            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build()
            ).get();
            
            System.out.println("[QDRANT DEBUG] Search completed. Found " + results.size() + " results");
            for (int i = 0; i < Math.min(results.size(), 3); i++) {
                ScoredPoint point = results.get(i);
                System.out.println("[QDRANT DEBUG] Result " + i + ": score=" + point.getScore() + 
                                 ", payloadKeys=" + point.getPayloadMap().keySet());
            }
            
            return results;
        } catch (Exception e) {
            System.err.println("[QDRANT ERROR] Search failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
