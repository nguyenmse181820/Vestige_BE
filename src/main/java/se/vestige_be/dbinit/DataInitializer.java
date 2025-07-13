package se.vestige_be.dbinit;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import se.vestige_be.pojo.*;
import se.vestige_be.pojo.enums.*;
import se.vestige_be.repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final UserAddressRepository userAddressRepository;
    private final OrderRepository orderRepository;


    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           CategoryRepository categoryRepository,
                           BrandRepository brandRepository,
                           ProductRepository productRepository,
                           UserAddressRepository userAddressRepository,
                           OrderRepository orderRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.userAddressRepository = userAddressRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        initRoles();
        initUsers();
        initCategories();
        initBrands();
        initUserAddresses();
        initProducts();
        initOrders();
    }

    private void initRoles() {
        try {
            long roleCount = roleRepository.count();
            if (roleCount == 0) {
                List<Role> roles = new ArrayList<>();
                roles.add(Role.builder().name("USER").build());
                roles.add(Role.builder().name("ADMIN").build());
                roles.add(Role.builder().name("SHIPPER").build());
                roleRepository.saveAll(roles);
                System.out.println("Default roles have been created");
            }
        } catch (Exception e) {
            System.err.println("Error initializing roles: " + e.getMessage());
        }
    }

    private void initUsers() {
        try {
            long userCount = userRepository.count();
            if (userCount == 0) {
                Role userRole = roleRepository.findByName("USER").orElseThrow(() -> new RuntimeException("USER role not found"));
                Role adminRole = roleRepository.findByName("ADMIN").orElseThrow(() -> new RuntimeException("ADMIN role not found"));
                Role shipperRole = roleRepository.findByName("SHIPPER").orElseThrow(() -> new RuntimeException("SHIPPER role not found"));
                String commonPassword = "Nguyenm$E181820";
                String encodedPassword = passwordEncoder.encode(commonPassword);

                List<User> users = new ArrayList<>();
                users.add(User.builder().username("admin").email("admin@vestige.com").passwordHash(encodedPassword).firstName("Admin").lastName("User").bio("Platform administrator").role(adminRole).isVerified(true).accountStatus("active").trustScore(100).build());
                users.add(User.builder().username("johndoe").email("john.doe@example.com").passwordHash(encodedPassword).firstName("John").lastName("Doe").bio("Fashion enthusiast and collector").role(userRole).isVerified(true).accountStatus("active").sellerRating(new BigDecimal("4.5")).sellerReviewsCount(25).successfulTransactions(20).trustScore(84).build());
                users.add(User.builder().username("jansmith").email("jane.smith@example.com").passwordHash(encodedPassword).firstName("Jane").lastName("Smith").bio("Luxury handbag specialist").role(userRole).isVerified(true).accountStatus("active").sellerRating(new BigDecimal("4.8")).sellerReviewsCount(45).successfulTransactions(38).trustScore(94).build());
                users.add(User.builder().username("mikewilson").email("mike.wilson@example.com").passwordHash(encodedPassword).firstName("Mike").lastName("Wilson").bio("Watch collector and trader").role(userRole).isVerified(true).accountStatus("active").sellerRating(new BigDecimal("4.3")).sellerReviewsCount(12).successfulTransactions(10).trustScore(80).build());
                users.add(User.builder()
                    .username("shipper01")
                    .email("shipping@vestige.com")
                    .passwordHash(encodedPassword)
                    .firstName("Kho")
                    .lastName("Vestige")
                    .role(shipperRole)
                    .isVerified(true)
                    .accountStatus("active")
                    .trustScore(100)
                    .build());
                
                // Create second shipper user with different password
                String shipperPassword = "Nguyem$E181820";
                String encodedShipperPassword = passwordEncoder.encode(shipperPassword);
                users.add(User.builder()
                    .username("shipper")
                    .email("shipper@vestige.com")
                    .passwordHash(encodedShipperPassword)
                    .firstName("Logistics")
                    .lastName("Team")
                    .role(shipperRole)
                    .isVerified(true)
                    .accountStatus("active")
                    .trustScore(100)
                    .build());
                userRepository.saveAll(users);
                System.out.println("Default users have been created:");
                System.out.println("- Regular users (admin, johndoe, jansmith, mikewilson, shipper01): password = Nguyenm$E181820");
                System.out.println("- Shipper user (shipper): password = Nguyem$E181820");
            }
        } catch (Exception e) {
            System.err.println("Error initializing users: " + e.getMessage());
        }
    }

    private void initCategories() {
        try {
            if (categoryRepository.count() == 0) {
                List<Category> categories = new ArrayList<>();
                Category electronics = Category.builder().name("Electronics").description("Gadgets and devices").build();
                Category fashion = Category.builder().name("Fashion").description("Clothing and accessories").build();
                Category books = Category.builder().name("Books").description("Various genres of books").build();

                categories.add(electronics);
                categories.add(fashion);
                categories.add(books);

                // Subcategories for Fashion
                Category mensFashion = Category.builder().name("Men's Fashion").description("Clothing for men").parentCategory(fashion).build();
                Category womensFashion = Category.builder().name("Women's Fashion").description("Clothing for women").parentCategory(fashion).build();

                categories.add(mensFashion);
                categories.add(womensFashion);

                categoryRepository.saveAll(categories);
                System.out.println("Default categories have been created.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing categories: " + e.getMessage());
        }
    }

    private void initBrands() {
        try {
            if (brandRepository.count() == 0) {
                List<Brand> brands = new ArrayList<>();
                brands.add(Brand.builder().name("Apple").logoUrl("apple.png").build());
                brands.add(Brand.builder().name("Samsung").logoUrl("samsung.png").build());
                brands.add(Brand.builder().name("Nike").logoUrl("nike.png").build());
                brands.add(Brand.builder().name("Gucci").logoUrl("gucci.png").build());
                brandRepository.saveAll(brands);
                System.out.println("Default brands have been created.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing brands: " + e.getMessage());
        }
    }

    private void initUserAddresses() {
        try {
            if (userAddressRepository.count() == 0) {
                User johnDoe = userRepository.findByUsername("johndoe").orElseThrow();
                User janeSmith = userRepository.findByUsername("jansmith").orElseThrow();

                List<UserAddress> addresses = new ArrayList<>();
                addresses.add(UserAddress.builder().user(johnDoe).addressLine1("123 Main St").city("Anytown").postalCode("12345").country("USA").isDefault(true).build());
                addresses.add(UserAddress.builder().user(johnDoe).addressLine1("456 Oak Ave").city("Otherville").postalCode("67890").country("USA").isDefault(false).build());
                addresses.add(UserAddress.builder().user(janeSmith).addressLine1("789 Pine Ln").city("Sometown").postalCode("54321").country("USA").isDefault(true).build());
                userAddressRepository.saveAll(addresses);
                System.out.println("Default user addresses have been created.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing user addresses: " + e.getMessage());
        }
    }

    private void initProducts() {
        try {
            if (productRepository.count() == 0) {
                User janeSmith = userRepository.findByUsername("jansmith").orElseThrow();
                User mikeWilson = userRepository.findByUsername("mikewilson").orElseThrow();

                Category electronics = categoryRepository.findByName("Electronics").orElseThrow();
                Category mensFashion = categoryRepository.findByName("Men's Fashion").orElseThrow();
                Category womensFashion = categoryRepository.findByName("Women's Fashion").orElseThrow();


                Brand apple = brandRepository.findByName("Apple").orElseThrow();
                Brand nike = brandRepository.findByName("Nike").orElseThrow();
                Brand gucci = brandRepository.findByName("Gucci").orElseThrow();


                List<Product> products = new ArrayList<>();

                // Products by Jane Smith
                products.add(Product.builder().seller(janeSmith).category(womensFashion).brand(gucci)
                        .title("Vintage Gucci Handbag").description("Authentic vintage Gucci handbag, excellent condition.")
                        .price(new BigDecimal("450.00")).originalPrice(new BigDecimal("600.00"))
                        .condition(ProductCondition.USED_EXCELLENT).status(ProductStatus.ACTIVE)
                        .images(List.of(ProductImage.builder().imageUrl("gucci_bag_1.jpg").isPrimary(true).displayOrder(1).build()))
                        .build());
                products.add(Product.builder().seller(janeSmith).category(womensFashion).brand(nike)
                        .title("Limited Edition Nike Sneakers (Women's)").description("Rare Nike Air Max, size 7, like new.")
                        .price(new BigDecimal("220.00")).condition(ProductCondition.LIKE_NEW).status(ProductStatus.ACTIVE)
                        .images(List.of(ProductImage.builder().imageUrl("nike_women_sneakers_1.jpg").isPrimary(true).displayOrder(1).build()))
                        .build());

                // Products by Mike Wilson
                products.add(Product.builder().seller(mikeWilson).category(electronics).brand(apple)
                        .title("Used iPhone 12 Pro").description("Good condition iPhone 12 Pro, 256GB, unlocked.")
                        .price(new BigDecimal("550.00")).condition(ProductCondition.USED_GOOD).status(ProductStatus.ACTIVE)
                        .images(List.of(ProductImage.builder().imageUrl("iphone12pro_1.jpg").isPrimary(true).displayOrder(1).build()))
                        .build());
                products.add(Product.builder().seller(mikeWilson).category(mensFashion).brand(nike)
                        .title("Classic Nike Hoodie (Men's)").description("Comfortable Nike hoodie, size L, barely worn.")
                        .price(new BigDecimal("45.00")).condition(ProductCondition.LIKE_NEW).status(ProductStatus.ACTIVE)
                        .images(List.of(ProductImage.builder().imageUrl("nike_men_hoodie_1.jpg").isPrimary(true).displayOrder(1).build()))
                        .build());

                products.forEach(p -> {
                    if (p.getImages() != null) {
                        p.getImages().forEach(img -> img.setProduct(p));
                    }
                });

                productRepository.saveAll(products);
                System.out.println("Default products have been created.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing products: " + e.getMessage());
        }
    }

    private void initOrders() {
        try {
            if (orderRepository.count() == 0) {
                User johnDoe = userRepository.findByUsername("johndoe").orElseThrow();
                UserAddress johnDoeAddress = userAddressRepository.findByUserUserIdAndIsDefaultTrue(johnDoe.getUserId()).get(0);

                Product gucciBag = productRepository.findBySellerUserIdAndTitle(
                        userRepository.findByUsername("jansmith").orElseThrow().getUserId(),
                        "Vintage Gucci Handbag"
                ).orElseThrow();
                Product iphone = productRepository.findBySellerUserIdAndTitle(
                        userRepository.findByUsername("mikewilson").orElseThrow().getUserId(),
                        "Used iPhone 12 Pro"
                ).orElseThrow();
                Product nikeSneakers = productRepository.findBySellerUserIdAndTitle(
                        userRepository.findByUsername("jansmith").orElseThrow().getUserId(),
                        "Limited Edition Nike Sneakers (Women's)"
                ).orElseThrow();


                List<Order> orders = new ArrayList<>();
                BigDecimal feePercentage = new BigDecimal("5.00");

                // Order 1: PENDING - John buys Gucci Bag from Jane
                Order order1 = Order.builder()
                        .buyer(johnDoe).shippingAddress(johnDoeAddress)
                        .paymentMethod(PaymentMethod.COD)
                        .totalAmount(gucciBag.getPrice())
                        .status(OrderStatus.PENDING).createdAt(LocalDateTime.now().minusDays(5))
                        .build();
                OrderItem item1_1 = OrderItem.builder().order(order1).product(gucciBag).seller(gucciBag.getSeller())
                        .price(gucciBag.getPrice()).platformFee(gucciBag.getPrice().multiply(feePercentage.divide(new BigDecimal(100))))
                        .feePercentage(feePercentage).status(OrderItemStatus.PENDING).escrowStatus(EscrowStatus.HOLDING)
                        .build();
                order1.setOrderItems(List.of(item1_1));
                gucciBag.setStatus(ProductStatus.SOLD); // Mark as sold
                productRepository.save(gucciBag);
                orders.add(order1);

                // Order 2: PROCESSING (Items PROCESSING) - John buys iPhone from Mike
                Order order2 = Order.builder()
                        .buyer(johnDoe).shippingAddress(johnDoeAddress)
                        .paymentMethod(PaymentMethod.STRIPE_CARD)
                        .totalAmount(iphone.getPrice())
                        .status(OrderStatus.PROCESSING).paidAt(LocalDateTime.now().minusDays(4)).createdAt(LocalDateTime.now().minusDays(4))
                        .build();
                OrderItem item2_1 = OrderItem.builder().order(order2).product(iphone).seller(iphone.getSeller())
                        .price(iphone.getPrice()).platformFee(iphone.getPrice().multiply(feePercentage.divide(new BigDecimal(100))))
                        .feePercentage(feePercentage).status(OrderItemStatus.PROCESSING).escrowStatus(EscrowStatus.HOLDING)
                        .build();
                order2.setOrderItems(List.of(item2_1));
                iphone.setStatus(ProductStatus.SOLD);
                productRepository.save(iphone);
                orders.add(order2);

                // Order 3: SHIPPED - John buys Nike Sneakers from Jane
                Product nikeHoodieJane = Product.builder().seller(userRepository.findByUsername("jansmith").orElseThrow())
                        .category(categoryRepository.findByName("Women's Fashion").orElseThrow())
                        .brand(brandRepository.findByName("Nike").orElseThrow())
                        .title("Jane's Nike Hoodie").description("Comfortable Nike hoodie, size M.")
                        .price(new BigDecimal("50.00")).condition(ProductCondition.USED_GOOD).status(ProductStatus.ACTIVE)
                        .images(List.of(ProductImage.builder().imageUrl("jane_hoodie_1.jpg").isPrimary(true).displayOrder(1).build()))
                        .build();
                nikeHoodieJane.getImages().forEach(img -> img.setProduct(nikeHoodieJane));
                productRepository.save(nikeHoodieJane);


                Order order3 = Order.builder()
                        .buyer(johnDoe).shippingAddress(johnDoeAddress)
                        .paymentMethod(PaymentMethod.COD)
                        .totalAmount(nikeHoodieJane.getPrice())
                        .status(OrderStatus.OUT_FOR_DELIVERY).paidAt(LocalDateTime.now().minusDays(3)).shippedAt(LocalDateTime.now().minusDays(2))
                        .createdAt(LocalDateTime.now().minusDays(3))
                        .build();
                OrderItem item3_1 = OrderItem.builder().order(order3).product(nikeHoodieJane).seller(nikeHoodieJane.getSeller())
                        .price(nikeHoodieJane.getPrice()).platformFee(nikeHoodieJane.getPrice().multiply(feePercentage.divide(new BigDecimal(100))))
                        .feePercentage(feePercentage).status(OrderItemStatus.OUT_FOR_DELIVERY).escrowStatus(EscrowStatus.HOLDING)
                        .build();
                order3.setOrderItems(List.of(item3_1));
                nikeHoodieJane.setStatus(ProductStatus.SOLD);
                productRepository.save(nikeHoodieJane);
                orders.add(order3);


                // Order 4: DELIVERED - John buys another item from Mike
                Product anotherElectronic = Product.builder().seller(userRepository.findByUsername("mikewilson").orElseThrow())
                        .category(categoryRepository.findByName("Electronics").orElseThrow())
                        .brand(brandRepository.findByName("Samsung").orElseThrow())
                        .title("Samsung Galaxy Buds").description("Wireless earbuds, great sound.")
                        .price(new BigDecimal("120.00")).condition(ProductCondition.LIKE_NEW).status(ProductStatus.ACTIVE)
                        .images(List.of(ProductImage.builder().imageUrl("galaxy_buds_1.jpg").isPrimary(true).displayOrder(1).build()))
                        .build();
                anotherElectronic.getImages().forEach(img -> img.setProduct(anotherElectronic));
                productRepository.save(anotherElectronic);

                Order order4 = Order.builder()
                        .buyer(johnDoe).shippingAddress(johnDoeAddress)
                        .paymentMethod(PaymentMethod.STRIPE_CARD)
                        .totalAmount(anotherElectronic.getPrice())
                        .status(OrderStatus.DELIVERED)
                        .paidAt(LocalDateTime.now().minusDays(10))
                        .shippedAt(LocalDateTime.now().minusDays(8))
                        .deliveredAt(LocalDateTime.now().minusDays(5))
                        .createdAt(LocalDateTime.now().minusDays(10))
                        .build();
                OrderItem item4_1 = OrderItem.builder().order(order4).product(anotherElectronic).seller(anotherElectronic.getSeller())
                        .price(anotherElectronic.getPrice()).platformFee(anotherElectronic.getPrice().multiply(feePercentage.divide(new BigDecimal(100))))
                        .feePercentage(feePercentage).status(OrderItemStatus.DELIVERED).escrowStatus(EscrowStatus.RELEASED) // Assuming released
                        .build();
                order4.setOrderItems(List.of(item4_1));
                anotherElectronic.setStatus(ProductStatus.SOLD);
                productRepository.save(anotherElectronic);
                orders.add(order4);

                // Order 5: CANCELLED - John attempted to buy Nike Sneakers from Jane, but cancelled
                Order order5 = Order.builder()
                        .buyer(johnDoe).shippingAddress(johnDoeAddress)
                        .paymentMethod(PaymentMethod.COD)
                        .totalAmount(nikeSneakers.getPrice())
                        .status(OrderStatus.CANCELLED).createdAt(LocalDateTime.now().minusDays(1))
                        .build();
                OrderItem item5_1 = OrderItem.builder().order(order5).product(nikeSneakers).seller(nikeSneakers.getSeller())
                        .price(nikeSneakers.getPrice()).platformFee(nikeSneakers.getPrice().multiply(feePercentage.divide(new BigDecimal(100))))
                        .feePercentage(feePercentage).status(OrderItemStatus.CANCELLED).escrowStatus(EscrowStatus.REFUNDED)
                        .build();
                order5.setOrderItems(List.of(item5_1));
                nikeSneakers.setStatus(ProductStatus.ACTIVE);
                productRepository.save(nikeSneakers);
                orders.add(order5);

                orderRepository.saveAll(orders);
                System.out.println("Default orders with various statuses have been created.");
            }
        } catch (Exception e) {
            System.err.println("Error initializing orders: " + e.getMessage());
            e.printStackTrace();
        }
    }
}