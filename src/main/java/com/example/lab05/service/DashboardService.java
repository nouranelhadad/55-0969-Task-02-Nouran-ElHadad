package com.example.lab05.service;

import com.example.lab05.dto.DashboardResponse;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.neo4j.Person;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;
import com.example.lab05.service.SensorService;
import com.example.lab05.service.ProductSearchService;
import com.example.lab05.service.SocialGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final PurchaseReceiptRepository purchaseRepo;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService searchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public DashboardService(PurchaseReceiptRepository purchaseRepo, SocialGraphService socialGraphService,
                            SensorService sensorService, ProductSearchService searchService,
                            RedisTemplate<String, Object> redisTemplate) {
        this.purchaseRepo = purchaseRepo;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
    }

    public DashboardResponse getDashboard(String personName) {
        String cacheKey = "dashboard:" + personName;

        try {
            DashboardResponse cached = (DashboardResponse) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return new DashboardResponse(
                        cached.personName(), cached.totalSpent(), cached.purchaseCount(),
                        cached.recentPurchases(), cached.friendRecommendations(),
                        cached.friendsOfFriends(), cached.recentActivity(),
                        cached.youMightAlsoLike(), true
                );
            }
        } catch (Exception e) {
            log.warn("Redis cache check failed for {}: {}", personName, e.getMessage());
        }

        List<PurchaseReceipt> allReceipts = purchaseRepo.findByPersonName(personName);
        double totalSpent = allReceipts.stream().mapToDouble(PurchaseReceipt::getTotalPrice).sum();
        int purchaseCount = allReceipts.size();
        List<PurchaseReceipt> recentPurchases = allReceipts.stream()
                .sorted(Comparator.comparing(PurchaseReceipt::getPurchasedAt).reversed())
                .limit(5).collect(Collectors.toList());

        List<Map<String, Object>> friendRecs = new ArrayList<>();
        List<String> fof = new ArrayList<>();
        try {
            friendRecs = socialGraphService.getRecommendations(personName, 5);
            fof = socialGraphService.getFriendsOfFriends(personName).stream()
                    .map(Person::getName) // Uses public getter for Person name
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch Neo4j data for {}: {}", personName, e.getMessage());
        }

        List<SensorReading> activity = new ArrayList<>();
        try {
            activity = sensorService.getReadingsBySensorId(
                    "user-activity-" + personName.toLowerCase()
            ).stream().limit(10).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch activity for {}: {}", personName, e.getMessage());
        }

        List<String> suggestions = new ArrayList<>();
        try {
            Set<String> boughtItems = allReceipts.stream().map(PurchaseReceipt::getProductName).collect(Collectors.toSet());
            Set<String> categories = allReceipts.stream().map(PurchaseReceipt::getProductCategory).collect(Collectors.toSet());

            for (String category : categories) {

                searchService.getByCategory(category).stream()
                        .filter(p -> !boughtItems.contains(p.getName()))
                        .limit(2)
                        .forEach(p -> suggestions.add(p.getName()));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch ES suggestions for {}: {}", personName, e.getMessage());
        }

        DashboardResponse response = new DashboardResponse(
                personName, totalSpent, purchaseCount, recentPurchases,
                friendRecs, fof, activity, suggestions, false
        );

        try {
            redisTemplate.opsForValue().set(cacheKey, response, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to cache dashboard for {}: {}", personName, e.getMessage());
        }

        return response;
    }
}