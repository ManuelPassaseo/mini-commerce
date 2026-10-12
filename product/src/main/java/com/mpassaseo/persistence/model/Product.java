package com.mpassaseo.persistence.model;

import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@NoArgsConstructor
@Entity
@Table(name = "inventory")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, nullable = false)
    private String productId;

    private Integer quantity;

    private String name;
    private String description;
    private double price;

    public Product(String productId, Integer quantity, String name, String description, double price) {
        this.productId = productId;
        this.quantity = quantity;
        this.name = name;
        this.description = description;
        this.price = price;
    }
}