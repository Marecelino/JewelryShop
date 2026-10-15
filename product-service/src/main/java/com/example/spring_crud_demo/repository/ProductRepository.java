package com.example.spring_crud_demo.repository;
import com.example.spring_crud_demo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository
        extends JpaRepository<Product, Integer> {
    List<Product> findByCategory(String category);

}
