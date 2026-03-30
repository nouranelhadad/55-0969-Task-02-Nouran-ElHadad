package com.example.lab05.service;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.repository.mongo.PurchaseReceiptRepository;
import com.example.lab05.model.cassandra.SensorReading;
import com.example.lab05.model.cassandra.SensorReadingKey;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final ProductService productService;
    private final PurchaseReceiptRepository receiptRepository;
    private final SocialGraphService socialGraphService;
    private final SensorService sensorService;
    private final ProductSearchService searchService;
    private final RedisTemplate<String, Object> redisTemplate;

    public PurchaseService(ProductService productService,
                           PurchaseReceiptRepository receiptRepository,
                           SocialGraphService socialGraphService,
                           SensorService sensorService,
                           ProductSearchService searchService,
                           RedisTemplate<String, Object> redisTemplate) {
        this.productService = productService;
        this.receiptRepository = receiptRepository;
        this.socialGraphService = socialGraphService;
        this.sensorService = sensorService;
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
    }

    public PurchaseReceipt executePurchase(PurchaseRequest request) {

        // Step 1 — PostgreSQL
        var product = productService.getProductById(request.productId());

        if (product.getStockQuantity() < request.quantity()) {
            throw new RuntimeException("Insufficient stock");
        }

        product.setStockQuantity(product.getStockQuantity() - request.quantity());
        productService.updateProduct(product.getId(), product);

        // Step 2 — MongoDB
        PurchaseReceipt receipt = new PurchaseReceipt(
                request.personName(),
                product.getName(),
                product.getCategory(),
                request.quantity(),
                product.getPrice(),
                request.purchaseDetails()
        );

        receipt = receiptRepository.save(receipt);

        // Step 3 — Neo4j
        try {
            socialGraphService.purchase(
                    request.personName(),
                    product.getName(),
                    request.quantity(),
                    product.getPrice()
            );
        } catch (Exception e) {
            log.warn("Neo4j failed: {}", e.getMessage());
        }

        // Step 4 — Cassandra
        try {
            SensorReading reading = new SensorReading();

            reading.setKey(
                    new SensorReadingKey(
                            "user-activity-" + request.personName().toLowerCase(),
                            null // timestamp will be set inside service
                    )
            );

            reading.setLocation(product.getName());
            reading.setTemperature(1.0);

            sensorService.recordReading(reading);

        } catch (Exception e) {
            log.warn("Cassandra failed: {}", e.getMessage());
        }


        // Step 5 — Elasticsearch
        try {
            if (product.getStockQuantity() == 0) {
                var results = searchService.searchByName(product.getName());
                if (!results.isEmpty()) {
                    var esProduct = results.get(0);
                    esProduct.setInStock(false);
                    searchService.saveProduct(esProduct);
                }
            }
        } catch (Exception e) {
            log.warn("Elasticsearch failed: {}", e.getMessage());
        }

        // Step 6 — Redis
        try {
            redisTemplate.delete("dashboard:" + request.personName());
        } catch (Exception e) {
            log.warn("Redis eviction failed: {}", e.getMessage());
        }

        return receipt;
    }
}
