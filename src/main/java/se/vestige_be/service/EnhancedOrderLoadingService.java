package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.vestige_be.dto.response.OrderDetailResponse;
import se.vestige_be.mapper.OrderMapper;
import se.vestige_be.pojo.Order;
import se.vestige_be.repository.OrderRepository;

import java.util.Optional;

/**
 * Enhanced Order Loading Service to prevent lazy loading issues
 * This service ensures all necessary relationships are loaded before mapping
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedOrderLoadingService {
    
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    
    /**
     * Load order with all relationships for safe mapping
     */
    public Optional<OrderDetailResponse> findOrderWithAllRelationships(Long orderId) {
        if (orderId == null) {
            log.warn("OrderId is null when finding order with relationships");
            return Optional.empty();
        }
        
        try {
            Optional<Order> orderOpt = orderRepository.findByIdWithAllRelationships(orderId);
            
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                log.debug("Successfully loaded order {} with all relationships", orderId);
                OrderDetailResponse response = orderMapper.convertToDetailResponse(order);
                return Optional.ofNullable(response);
            } else {
                log.debug("Order {} not found", orderId);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Failed to load order {} with relationships: {}", orderId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if order exists without loading full relationships
     */
    public boolean orderExists(Long orderId) {
        if (orderId == null) {
            return false;
        }
        
        try {
            return orderRepository.existsById(orderId);
        } catch (Exception e) {
            log.error("Failed to check if order {} exists: {}", orderId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Load order with basic information only (faster for simple checks)
     */
    public Optional<Order> findOrderBasic(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        
        try {
            return orderRepository.findById(orderId);
        } catch (Exception e) {
            log.error("Failed to load basic order {}: {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }
}
