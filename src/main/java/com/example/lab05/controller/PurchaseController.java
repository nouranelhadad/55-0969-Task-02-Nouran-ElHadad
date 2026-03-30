package com.example.lab05.controller;

import com.example.lab05.dto.PurchaseRequest;
import com.example.lab05.model.mongo.PurchaseReceipt;
import com.example.lab05.service.PurchaseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/55-0969/purchases")
public class PurchaseController {
    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public PurchaseReceipt makePurchase(@RequestBody PurchaseRequest request) {
        return purchaseService.executePurchase(request); // [cite: 113]
    }
}