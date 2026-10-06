package com.hispania.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes de CORS leidos de {@code application.properties} con el prefijo
 * {@code app.cors}.
 *
 * <p>Con Spring Boot 3+/4 no hace falta escribir un filtro: declarando
 * {@code @ConfigurationProperties} en una clase, los valores llegan solos y el tipo
 * pasa a ser una lista en lugar de una cadena que habia que partir a mano.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Todos los orígenes permitidos.
     */
    private String[] allowedOrigins = {"*"};

    /**
     * Verbos HTTP admitidos en peticiones cross-origin.
     */
    private String[] allowedMethods = {
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
    };

    public String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String[] getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(String[] allowedMethods) {
        this.allowedMethods = allowedMethods;
    }
}
