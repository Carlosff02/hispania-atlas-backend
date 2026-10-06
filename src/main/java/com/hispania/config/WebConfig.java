package com.hispania.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para el frontend.
 *
 * <p>Solo hace falta en desarrollo: si el backend corre en el 8080 y Angular en el
 * 4200, son origenes distintos y el navegador bloquea la respuesta aunque el servidor
 * la sirva bien. Con el proxy de {@code proxy.conf.json} el frontend llama a rutas del
 * mismo origen y CORS deja de intervenir.
 *
 * <p>Sustituye al {@code CorsFilter} de JAX-RS que usaba Quarkus. Aquel duplicaba lo
 * que ya hacia {@code quarkus.http.cors}, y ademas daba problemas: esos dos headers a
 * la vez no son validos, porque un navegador rechaza
 * {@code Access-Control-Allow-Origin: *} en cuanto ve
 * {@code Access-Control-Allow-Credentials: true}.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getAllowedOrigins())
                .allowedMethods(corsProperties.getAllowedMethods())
                .allowedHeaders("*")
                .maxAge(3600);

        // allowCredentials queda desactivado a proposito: la API es de solo lectura y
        // sin sesiones, asi que no hay cookies que admitir y conviene evitar el
        // conflicto con el comodin de allowedOrigins.
    }
}
