package com.mpassaseo.controller;

import com.mpassaseo.persistence.model.Product;
import com.mpassaseo.service.ProductService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@AllArgsConstructor
@RestController
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    public Iterable<Product> getProducts() {
        return productService.getProducts();
    }

    @GetMapping("/products/{productId}")
    public Optional<Product> getProductById(
            @PathVariable String productId) {

        return productService.getProductById(productId);
    }

    @PostMapping("/products")
    public Product save(
            @RequestBody Product product) {

        return productService.save(product);
    }

    @PutMapping("/products/{productId}")
    public Product update(
            @RequestBody Product product,
            @PathVariable String productId) {

        return productService.update(
                productId,
                product
        );
    }

    @DeleteMapping("/products/{productId}")
    public void delete(
            @PathVariable String productId) {

        productService.delete(productId);
    }
}