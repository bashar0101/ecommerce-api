package com.apps.ecommerce.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.apps.ecommerce.dto.ProductCreateRequest;
import com.apps.ecommerce.dto.ProductResponse;
import com.apps.ecommerce.entity.Product;
import com.apps.ecommerce.exception.ResourceNotFoundException;
import com.apps.ecommerce.repository.ProductRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepo productRepo;

    public ProductResponse create(ProductCreateRequest dto) {
        Product newProduct = new Product();
        newProduct.setName(dto.name());
        newProduct.setPrice(dto.price());
        newProduct.setStock(dto.stock());
        newProduct.setDescription(dto.description());
        return toDto(productRepo.save(newProduct));
    }

    public ProductResponse findByName(String name) {
        Product p = productRepo.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException(Product.class, name));

        return toDto(p);
    }

    public ProductResponse findById(UUID id) {
        return toDto(getEntity(id));
    }

    public void deleteById(UUID id) {
        productRepo.delete(getEntity(id));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductCreateRequest dto) {
        Product product = getEntity(id);

        product.setName(dto.name());
        product.setPrice(dto.price());
        product.setStock(dto.stock());
        product.setDescription(dto.description());

        return toDto(productRepo.save(product));
    }

    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepo.findAll(pageable)
                .map(this::toDto);
    }

    private Product getEntity(UUID id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Product.class, id));
    }

    private ProductResponse toDto(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getPrice(),
                p.getStock(),
                p.getDescription());
    }

}
