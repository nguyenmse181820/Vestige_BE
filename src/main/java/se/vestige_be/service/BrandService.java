package se.vestige_be.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.response.BrandResponse;
import se.vestige_be.pojo.Brand;
import se.vestige_be.repository.BrandRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class BrandService {
    private final BrandRepository brandRepository;

    public List<BrandResponse> findAll() {
        return brandRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }
    private BrandResponse convertToDTO(Brand brand) {
        return BrandResponse.builder()
                .brandId(brand.getBrandId())
                .name(brand.getName())
                .logoUrl(brand.getLogoUrl())
                .createdAt(brand.getCreatedAt())
                .build();
    }

    @Transactional
    public Brand createBrand(String name, String logoUrl) {
        if(brandRepository.existsByName(name)){
            throw new RuntimeException("Brand with name " + name + " already exists");
        }
        Brand brand = Brand.builder()
                .name(name)
                .logoUrl(logoUrl)
                .createdAt(LocalDateTime.now())
                .build();
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand updateBrand(Long brandId, String name, String logoUrl) {
        Brand existingBrand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("Brand not found with id: " + brandId));

        if(name != null && !name.equals(existingBrand.getName()) && brandRepository.existsByName(name)){
            throw new RuntimeException("Brand with name " + name + " already exists");
        }

        if(name != null) {
            existingBrand.setName(name);
        }
        if(logoUrl != null) {
            existingBrand.setLogoUrl(logoUrl);
        }
        return brandRepository.save(existingBrand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Brand with id '" + id + "' not found"));

        if(!brand.getProducts().isEmpty()) {
            throw new RuntimeException("Cannot delete brand '" + id + "': has " +
                    brand.getProducts().size() + " associated products");
        }

        brandRepository.delete(brand);
    }

}
