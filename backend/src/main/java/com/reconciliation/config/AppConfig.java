package com.reconciliation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    /**
     * Allow the dashboard to call the API from its configured origin.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("Authorization", "Content-Type")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    /**
     * Swagger/OpenAPI documentation.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank Reconciliation Engine")
                        .version("1.0.0")
                        .description("""
                            REST API for automatic bank statement reconciliation.
                            
                            **Main flow:**
                            1. Create internal financial entries with `POST /lancamentos`.
                            2. Upload an OFX or CSV bank statement with `POST /conciliacao/importar`.
                            3. Review matched, unmatched, and pending records in the response.
                            """)
                        .contact(new Contact()
                                .name("Isaac Almeida")
                                .url("https://github.com/Inwtn/bank-reconciliation"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
