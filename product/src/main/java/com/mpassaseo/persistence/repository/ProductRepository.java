package com.mpassaseo.persistence.repository;

import com.mpassaseo.persistence.model.Product;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ProductRepository extends CrudRepository<Product, Long> {
    Optional<Product> findProductsByProductId(String productId);
}
