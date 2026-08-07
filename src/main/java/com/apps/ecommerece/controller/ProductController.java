package com.apps.ecommerece.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apps.ecommerece.dto.ProdcutCreateDto;
import com.apps.ecommerece.dto.ProductResponseDto;
import com.apps.ecommerece.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/all")
    public ResponseEntity<List<ProductResponseDto>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/{name}")
    public ResponseEntity<ProductResponseDto> findByName(@PathVariable String name) {
        return ResponseEntity.ok(productService.findByName(name));
    }

    @PostMapping("")
    public ResponseEntity<ProductResponseDto> create(@Valid @RequestBody ProdcutCreateDto dto) {
        ProductResponseDto created = productService.create(dto);

        return ResponseEntity
                .created(URI.create("/product/" + created.name()))
                .body(created);
    }

}
