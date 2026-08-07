package com.apps.ecommerece.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.apps.ecommerece.dto.ProdcutCreateDto;
import com.apps.ecommerece.dto.ProductResponseDto;
import com.apps.ecommerece.entity.Product;
import com.apps.ecommerece.exception.ResourceNotFoundException;
import com.apps.ecommerece.repository.ProductRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepo productRepo;

    public ProductResponseDto create(ProdcutCreateDto dto) {
        Product newProduct = Product.builder()
                .name(dto.name())
                .description(dto.description())
                .price(dto.price())
                .stock(dto.stock())
                .build();
        return toDto(productRepo.save(newProduct));
    }

    public ProductResponseDto findByName(String name) {
        Product p = productRepo.findByname(name)
                .orElseThrow(() -> new ResourceNotFoundException(Product.class, name));

        return toDto(p);
    }

    public List<ProductResponseDto> findAll() {
        return productRepo.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ProductResponseDto toDto(Product p) {
        return new ProductResponseDto(
                p.getId(),
                p.getName(),
                p.getPrice(),
                p.getStock(),
                p.getDescription());
    }

}
