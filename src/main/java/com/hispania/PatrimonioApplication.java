package com.hispania;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API de Hispania Atlas.
 *
 * <p>La aplicacion se organiza en tres capas, y cada una solo conoce a la de abajo:
 *
 * <ul>
 *   <li>{@code presentation} -- controllers REST y DTOs ({@code request} /
 *       {@code response}). Es la unica capa que conoce HTTP.</li>
 *   <li>{@code services} -- interfaces con la logica de negocio
 *       ({@code interfaces}) y sus implementaciones ({@code impl}), mas el mapeo
 *       entidad &lt;-&gt; DTO.</li>
 *   <li>{@code persistence} -- entidades JPA ({@code entity}) y repositorios
 *       Spring Data ({@code repository}). Es la unica capa que conoce SQL.</li>
 * </ul>
 *
 * <p>El escaneo de componentes cubre los tres paquetes, asi que las interfaces de
 * {@code services} se implementan automaticamente por sus clases {@code impl}.
 */
@SpringBootApplication
public class PatrimonioApplication {

    public static void main(String[] args) {
        exigirCredencial("DB_PASSWORD", "SPRING_DATASOURCE_PASSWORD", "spring.datasource.password");
        exigirCredencial("JWT_SECRET", "JWT_SECRET_PROPERTY", "jwt.secret");
        SpringApplication.run(PatrimonioApplication.class, args);
    }

    /**
     * Falla rapido si una credencial obligatoria no esta definida.
     *
     * <p>{@code application.properties} declara ambas credenciales como
     * {@code ${DB_PASSWORD}} y {@code ${JWT_SECRET}}, sin valor por defecto, para
     * que una clave real no acabe escrita en el repositorio. El problema es que un
     * placeholder sin default <strong>no</strong> produce un error util: Spring lo
     * resuelve a cadena vacia y el primer fallo visible es el de PostgreSQL,
     * {@code FATAL: password authentication failed for user "postgres"}, que senala a
     * la contrasena de Postgres cuando el problema real es que no se exporto la
     * variable. Con la clave del JWT el sintoma es peor: la aplicacion arranca y
     * todos los logins fallan.
     *
     * <p>La comprobacion va aqui, en {@code main} y no en un {@code @PostConstruct},
     * porque un componente normal se inicializa DESPUES de que Flyway ya ha
     * intentado conectarse: llegaria tarde. Antes de crear el contexto es el unico
     * sitio que todavia no ha pasado nada.
     *
     * <p>El nombre canonico se comprueba SIEMPRE, y solo despues las alternativas.
     * Antes iba al reves: el nombre iba al mensaje de error y las alternativas a la
     * busqueda, de modo que {@code DB_PASSWORD} no se miraba nunca aunque fuera la
     * variable de entorno correctamente configurada. Separar el nombre del mensaje
     * del nombre que se busca es justo el error que esta comprobacion existe para
     * evitar.
     *
     * <p>Se aceptan varias rutas, para no bloquear a quien use una alternativa
     * valida.
     *
     * @param nombreVariable      nombre canonico, el que aparece en el mensaje de
     *                           error y el primero que se busca
     * @param rutasAlternativas   formas admitidas adicionales: propiedad del sistema
     *                           o variable de entorno
     */
    private static void exigirCredencial(String nombreVariable, String... rutasAlternativas) {
        List<String> candidatas = new ArrayList<>();
        candidatas.add(nombreVariable);
        candidatas.addAll(List.of(rutasAlternativas));
        exigirCredencial(nombreVariable, candidatas, PatrimonioApplication::buscarEnElEntorno);
    }

    static void exigirCredencial(String nombreVariable, List<String> candidatas,
            Function<String, String> resolver) {
        for (String ruta : candidatas) {
            String valor = resolver.apply(ruta);
            if (valor != null && !valor.isBlank() && !valor.contains("${")) {
                return;
            }
        }

        throw new IllegalStateException("""
                Falta la variable de entorno %s.

                  - Copia la plantilla:      copy .env.example .env
                  - En PowerShell:           $env:%s = "<valor>"
                  - En bash:                 export %s=<valor>
                  - En IntelliJ:             Run > Edit Configurations > Environment variables

                No lleva valor por defecto a proposito, para que ninguna clave real
                acabe en el historial de git. En .env.example hay un valor de ejemplo:
                copialo, pero cambialo. Para la clave del JWT, genera una con:
                  openssl rand -base64 48""".formatted(nombreVariable, nombreVariable, nombreVariable));
    }

    private static String buscarEnElEntorno(String ruta) {
        String valor = System.getProperty(ruta);
        return valor != null ? valor : System.getenv(ruta);
    }
}
