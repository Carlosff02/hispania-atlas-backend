package com.hispania.presentation.controllers;

import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.response.LugarResponse;
import com.hispania.services.interfaces.LugarService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del controller de lugares con el slice de Spring MVC.
 *
 * <p>El servicio va simulado con {@code @MockitoBean}, asi que estas pruebas no
 * levantan la base de datos: comprueban el cableado HTTP, la forma del JSON y, sobre
 * todo, los grupos de validacion, que es la parte facil de romper sin que nadie se
 * entere.
 *
 * <p>En Spring Boot 4 {@code @WebMvcTest} ya no viene en
 * {@code spring-boot-test-autoconfigure}: requiere el modulo
 * {@code spring-boot-webmvc-test} y el paquete
 * {@code org.springframework.boot.webmvc.test.autoconfigure}.
 */
@WebMvcTest(LugarController.class)
class LugarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LugarService lugarService;

    private static LugarResponse lugar() {
        return new LugarResponse("mali", "Museo de Arte de Lima", "PE",
                -12.06, -77.037, "ARTE", "palette", "1961", "Palacio de la Exposición", null);
    }

    @Test
    @DisplayName("GET devuelve 200 con la lista de lugares")
    void listarDevuelve200() throws Exception {
        when(lugarService.listarTodos()).thenReturn(List.of(lugar()));

        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mali"))
                .andExpect(jsonPath("$[0].category").value("ARTE"));
    }

    @Test
    @DisplayName("GET con categoria desconocida devuelve 400, no 500")
    void categoriaDesconocidaDevuelve400() throws Exception {
        // Jackson no puede convertir "XXX" a CategoriaLugar; sin el manejador global
        // esto seria un 500 por un error que es del cliente.
        mockMvc.perform(get("/api/places").param("category", "XXX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/places"));
    }

    @Test
    @DisplayName("GET de un id inexistente devuelve 404 y no un 200 con cuerpo vacio")
    void idInexistenteDevuelve404() throws Exception {
        when(lugarService.buscarPorId("no_existe")).thenReturn(null);

        mockMvc.perform(get("/api/places/no_existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el 404 de un recurso lleva el mismo cuerpo ApiError que el resto")
    void notFoundDelServicioUsaApiError() throws Exception {
        when(lugarService.buscarPorId("no_existe"))
                .thenThrow(ResourceNotFoundException.de("Lugar", "no_existe"));

        mockMvc.perform(get("/api/places/no_existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Lugar con identificador 'no_existe' no encontrado"))
                .andExpect(jsonPath("$.path").value("/api/places/no_existe"));
    }

    @Test
    @DisplayName("un id repetido produce 409 con mensaje legible")
    void idRepetidoDevuelve409() throws Exception {
        when(lugarService.crear(any())).thenThrow(DuplicateResourceException.de("Lugar", "mali"));

        mockMvc.perform(post("/api/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"mali","name":"Museo","country":"PE","lat":-12.0,"lng":-77.0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("POST valido devuelve 201 con la cabecera Location")
    void postValidoDevuelve201ConLocation() throws Exception {
        when(lugarService.crear(any())).thenReturn(lugar());

        mockMvc.perform(post("/api/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"mali","name":"Museo de Arte de Lima","country":"PE",
                                 "lat":-12.06,"lng":-77.037,"category":"ARTE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/places/mali"))
                .andExpect(jsonPath("$.id").value("mali"));
    }

    @Test
    @DisplayName("POST sin id devuelve 400: el grupo OnCreate lo exige")
    void postSinIdDevuelve400() throws Exception {
        mockMvc.perform(post("/api/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin id","country":"PE","lat":-12.0,"lng":-77.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'id')]").exists());
    }

    @Test
    @DisplayName("un POST con varios fallos los devuelve todos, no solo el primero")
    void postReportaTodosLosErrores() throws Exception {
        mockMvc.perform(post("/api/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"ConMayusculas","country":"PE","lat":999,"lng":-77.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'id')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'lat')]").exists());
    }

    @Test
    @DisplayName("PUT sin id en el cuerpo se acepta: el id lo impone la ruta")
    void putSinIdEnElCuerpoEsValido() throws Exception {
        when(lugarService.actualizar(eq("mali"), any())).thenReturn(lugar());

        mockMvc.perform(put("/api/places/mali")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Museo de Arte de Lima","country":"PE","lat":-12.06,"lng":-77.037}
                                """))
                .andExpect(status().isOk());

        // Y el servicio recibe el registro del grupo OnUpdate, con el id nulo.
        ArgumentCaptor<LugarRequest> captor = ArgumentCaptor.forClass(LugarRequest.class);
        verify(lugarService).actualizar(eq("mali"), captor.capture());
        assertThat(captor.getValue().id()).isNull();
    }

    @Test
    @DisplayName("PUT sigue validando el resto de campos aunque no pida el id")
    void putValidaLosCamposComunes() throws Exception {
        // Si OnUpdate no extendiera Default, estas reglas no se ejecutarian.
        mockMvc.perform(put("/api/places/mali")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"country":"PE","lat":-12.06,"lng":-77.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("DELETE devuelve 204 sin cuerpo")
    void deleteDevuelve204() throws Exception {
        doNothing().when(lugarService).eliminar(anyString());

        mockMvc.perform(delete("/api/places/mali"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("DELETE de un id inexistente devuelve 404")
    void deleteInexistenteDevuelve404() throws Exception {
        doThrow(ResourceNotFoundException.de("Lugar", "no_existe")).when(lugarService).eliminar("no_existe");

        mockMvc.perform(delete("/api/places/no_existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el filtro por categoria llega al servicio como enumerado")
    void filtroPorCategoriaLlegaComoEnumerado() throws Exception {
        when(lugarService.listarPorCategoria(CategoriaLugar.DANZA)).thenReturn(List.of());

        mockMvc.perform(get("/api/places").param("category", "DANZA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
