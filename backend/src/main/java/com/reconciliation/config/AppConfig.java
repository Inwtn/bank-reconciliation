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
     * Configuração de CORS para permitir acesso do frontend.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    /**
     * Configuração do Swagger/OpenAPI.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Motor de Conciliação Bancária")
                        .version("1.0.0")
                        .description("""
                            API REST para conciliação automática de extratos bancários.
                            
                            **Fluxo principal:**
                            1. Registre seus lançamentos (contas a pagar/receber) via `POST /lancamentos`
                            2. Faça upload do extrato bancário (OFX ou CSV) via `POST /conciliacao/importar`
                            3. O motor cruza os dados e retorna o resultado com conciliados e divergentes
                            """)
                        .contact(new Contact()
                                .name("Equipe de Desenvolvimento")
                                .email("dev@empresa.com.br"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
