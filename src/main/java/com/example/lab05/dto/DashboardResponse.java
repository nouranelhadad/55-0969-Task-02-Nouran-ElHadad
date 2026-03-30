package com.example.lab05.dto;

import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.model.cassandra.SensorReading;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        String personName,
        Double totalSpent,        // From MongoDB [cite: 161]
        Integer purchaseCount,    // From MongoDB [cite: 162]
        List<PurchaseReceipt> recentPurchases, // From MongoDB [cite: 163]
        List<Map<String, Object>> friendRecommendations, // From Neo4j [cite: 164]
        List<String> friendsOfFriends, // From Neo4j [cite: 166]
        List<SensorReading> recentActivity, // From Cassandra [cite: 167]
        List<String> youMightAlsoLike, // From Elasticsearch [cite: 169]
        boolean servedFromCache // From Redis [cite: 171]
) implements Serializable {}