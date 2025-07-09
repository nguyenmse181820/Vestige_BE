package se.vestige_be.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for generating URL-friendly slugs from strings
 */
public class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");
    
    /**
     * Generate a URL-friendly slug from a string
     * 
     * @param input The input string (e.g., product title)
     * @return URL-friendly slug
     */
    public static String generateSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        
        String slug = input.trim().toLowerCase(Locale.ENGLISH);
        
        // Normalize unicode characters (remove accents, etc.)
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD);
        
        // Replace whitespace with hyphens
        slug = WHITESPACE.matcher(slug).replaceAll("-");
        
        // Remove non-latin characters (keep letters, numbers, hyphens)
        slug = NON_LATIN.matcher(slug).replaceAll("");
        
        // Replace multiple consecutive hyphens with single hyphen
        slug = MULTIPLE_HYPHENS.matcher(slug).replaceAll("-");
        
        // Remove leading and trailing hyphens
        slug = slug.replaceAll("^-+|-+$", "");
        
        // Limit length to prevent extremely long slugs
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
            // Make sure we don't cut in the middle of a word
            int lastHyphen = slug.lastIndexOf('-');
            if (lastHyphen > 50) {
                slug = slug.substring(0, lastHyphen);
            }
        }
        
        return slug;
    }
    
    /**
     * Generate a unique slug by appending a number if the base slug already exists
     * 
     * @param baseSlug The base slug
     * @param counter The counter to append
     * @return Unique slug with counter
     */
    public static String generateUniqueSlug(String baseSlug, int counter) {
        return counter <= 1 ? baseSlug : baseSlug + "-" + counter;
    }
      /**
     * Generate slug from product title with optional additional context
     * 
     * @param title Product title
     * @param sellerUsername Optional seller username to add uniqueness
     * @param condition Product condition (NEW, USED, etc.)
     * @param color Product color
     * @param size Product size
     * @return Generated slug with context
     */
    public static String generateProductSlugWithContext(String title, String sellerUsername, 
                                                       String condition, String color, String size) {
        StringBuilder slugBuilder = new StringBuilder();
        
        // Start with the main title
        String baseSlug = generateSlug(title);
        if (baseSlug.isEmpty()) {
            baseSlug = "product";
        }
        slugBuilder.append(baseSlug);
        
        // Add meaningful context to make it unique and descriptive
        if (size != null && !size.trim().isEmpty()) {
            slugBuilder.append("-").append(generateSlug(size));
        }
        
        if (color != null && !color.trim().isEmpty()) {
            slugBuilder.append("-").append(generateSlug(color));
        }
        
        if (condition != null && !condition.trim().isEmpty() && 
            !condition.equalsIgnoreCase("NEW")) { // Only add if not new
            slugBuilder.append("-").append(generateSlug(condition));
        }
        
        // Add seller context if needed for uniqueness
        if (sellerUsername != null && !sellerUsername.trim().isEmpty()) {
            slugBuilder.append("-by-").append(generateSlug(sellerUsername));
        }
        
        return slugBuilder.toString();
    }
    
    /**
     * Generate a unique slug by adding random suffix instead of numbers
     * 
     * @param baseSlug The base slug
     * @return Unique slug with random suffix
     */
    public static String generateUniqueSlugWithRandomSuffix(String baseSlug) {
        // Generate a short random string (6 characters)
        String randomSuffix = java.util.UUID.randomUUID().toString().substring(0, 6);
        return baseSlug + "-" + randomSuffix;
    }
    
    /**
     * Generate professional product slug with brand name first
     * Format: brand-title-color-size-condition (like adidas-samba-og-silver-metallic-cracked-leather-womens)
     * 
     * @param brandName Brand name
     * @param title Product title  
     * @param color Product color
     * @param size Product size
     * @param condition Product condition (NEW, USED, etc.)
     * @return Professional slug with brand first
     */
    public static String generateProfessionalProductSlug(String brandName, String title, 
                                                        String color, String size, String condition) {
        StringBuilder slugBuilder = new StringBuilder();
        
        // Start with brand name
        if (brandName != null && !brandName.trim().isEmpty()) {
            String brandSlug = generateSlug(brandName);
            if (!brandSlug.isEmpty()) {
                slugBuilder.append(brandSlug);
            }
        }
        
        // Add title
        if (title != null && !title.trim().isEmpty()) {
            String titleSlug = generateSlug(title);
            if (!titleSlug.isEmpty()) {
                if (slugBuilder.length() > 0) {
                    slugBuilder.append("-");
                }
                slugBuilder.append(titleSlug);
            }
        }
        
        // Add color
        if (color != null && !color.trim().isEmpty()) {
            String colorSlug = generateSlug(color);
            if (!colorSlug.isEmpty()) {
                slugBuilder.append("-").append(colorSlug);
            }
        }
        
        // Add size
        if (size != null && !size.trim().isEmpty()) {
            String sizeSlug = generateSlug(size);
            if (!sizeSlug.isEmpty()) {
                slugBuilder.append("-").append(sizeSlug);
            }
        }
        
        // Add condition (only if not NEW)
        if (condition != null && !condition.trim().isEmpty() && 
            !condition.equalsIgnoreCase("NEW")) {
            String conditionSlug = generateSlug(condition);
            if (!conditionSlug.isEmpty()) {
                slugBuilder.append("-").append(conditionSlug);
            }
        }
        
        // Fallback if everything is empty
        if (slugBuilder.length() == 0) {
            return "product";
        }
        
        return slugBuilder.toString();
    }
}
