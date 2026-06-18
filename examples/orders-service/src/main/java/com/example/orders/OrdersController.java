package com.example.orders;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A trivial endpoint so springdoc has an API surface to describe in the exported OpenAPI spec. */
@RestController
@RequestMapping("/orders")
public class OrdersController {

    public record Order(String id, String customer, String status) {
    }

    @GetMapping
    public List<Order> list() {
        return List.of(new Order("o-1", "alice", "PAID"), new Order("o-2", "bob", "PENDING"));
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        return new Order(id, "alice", "PAID");
    }
}
