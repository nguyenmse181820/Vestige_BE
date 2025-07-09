package se.vestige_be.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.vestige_be.dto.request.UserAddressRequest;
import se.vestige_be.dto.response.UserAddressResponse;
import se.vestige_be.exception.ResourceNotFoundException;
import se.vestige_be.exception.UnauthorizedException;
import se.vestige_be.pojo.User;
import se.vestige_be.pojo.UserAddress;
import se.vestige_be.repository.UserAddressRepository;
import se.vestige_be.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    public List<UserAddressResponse> getUserAddresses(Long userId) {
        List<UserAddress> addresses = userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        return addresses.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserAddressResponse createAddress(UserAddressRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean defaultChoice;

        List<UserAddress> existingAddresses = userAddressRepository.findByUserUserId(userId);

        if(existingAddresses.isEmpty()) {
            defaultChoice = true;
        } else {
            if(request.getIsDefault() != null) {
                defaultChoice = request.getIsDefault();
            } else {
                defaultChoice = false;
            }
        }

        if (defaultChoice) {
            unsetOtherDefaults(userId);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .isDefault(defaultChoice)
                .build();

        address = userAddressRepository.save(address);
        return convertToResponse(address);
    }

    public UserAddressResponse getAddressById(Long addressId, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to access this address");
        }

        return convertToResponse(address);
    }

    @Transactional
    public UserAddressResponse updateAddress(Long addressId, UserAddressRequest request, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to update this address");
        }

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            unsetOtherDefaults(userId);
        }

        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        if (request.getIsDefault() != null) {
            List<UserAddress> allUserAddresses = userAddressRepository.findByUserUserId(userId);
            if (allUserAddresses.size() == 1 && address.getAddressId().equals(allUserAddresses.getFirst().getAddressId()) && Boolean.FALSE.equals(request.getIsDefault())) {
                address.setIsDefault(true);
            } else {
                address.setIsDefault(request.getIsDefault());
            }
        }

        address = userAddressRepository.save(address);
        return convertToResponse(address);
    }

    @Transactional
    public void deleteAddress(Long addressId, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to delete this address");
        }

        boolean wasDefault = address.getIsDefault();

        userAddressRepository.delete(address);

        if(wasDefault) {
            List<UserAddress> remainingAddresses = userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
            UserAddress newDefaultAddress = remainingAddresses.getFirst();
            newDefaultAddress.setIsDefault(true);

            userAddressRepository.save(newDefaultAddress);
        }
    }

    @Transactional
    public UserAddressResponse setDefaultAddress(Long addressId, Long userId) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to modify this address");
        }

        unsetOtherDefaults(userId);

        address.setIsDefault(true);
        address = userAddressRepository.save(address);

        return convertToResponse(address);
    }

    private void unsetOtherDefaults(Long userId) {
        List<UserAddress> defaultAddresses = userAddressRepository.findByUserUserIdAndIsDefaultTrue(userId);
        defaultAddresses.forEach(addr -> {
            if (Boolean.TRUE.equals(addr.getIsDefault())) {
                addr.setIsDefault(false);
            }
        });
        userAddressRepository.saveAll(defaultAddresses);
    }

    private UserAddressResponse convertToResponse(UserAddress address) {
        return UserAddressResponse.builder()
                .addressId(address.getAddressId())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}