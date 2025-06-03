package se.vestige_be.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Thêm JavaTimeModule để hỗ trợ LocalDateTime, LocalDate, etc.
        mapper.registerModule(new JavaTimeModule());

        // Disable ghi timestamp dưới dạng số
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Enable pretty print cho debug (có thể tắt trong production)
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }
}