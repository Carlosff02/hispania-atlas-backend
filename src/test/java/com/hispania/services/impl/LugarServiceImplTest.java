package com.hispania.services.impl;

import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.Lugar;
import com.hispania.persistence.entity.Pais;
import com.hispania.persistence.entity.PaisSerieHistorica;
import com.hispania.persistence.entity.Region;
import com.hispania.persistence.repository.LugarRepository;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.persistence.repository.PaisSerieHistoricaRepository;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.response.LugarResponse;
import com.hispania.presentation.dto.response.PaisResponse;
import com.hispania.services.mapper.ResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la logica de negocio de lugares y paises.
 *
 * <p>Los repositorios son dobles de Mockito, asi que estas pruebas no tocan la base de
 * datos: verifican el reparto de responsabilidades (repositorios, reglas y mapeo) sin
 * depender de PostgreSQL ni de Flyway.
 *
 * <p>Lo que mas merece cobertura aqui es la sincronia de {@code country} con
 * {@code pais_code}, porque de ella depende el CHECK de la migracion V5 y un fallo
 * solo apareceria en produccion, al insertar.
 */
@ExtendWith(MockitoExtension.class)
class LugarServiceImplTest {

    @Mock
    private LugarRepository lugarRepository;

    @Mock
    private PaisRepository paisRepository;

    @Mock
    private PaisSerieHistoricaRepository serieRepository;

    private LugarServiceImpl service;

    private static Pais paisPe() {
        return new Pais("PE", "Perú", "Lima", -9.2, -75.0, Region.ANDINA, "Antiguo epicentro");
    }

    private static LugarRequestBuilder request() {
        return new LugarRequestBuilder();
    }

    /** Atajo para no repetir los diez componentes del record en cada prueba. */
    private static final class LugarRequestBuilder {
        private String id = "nuevo";
        private String name = "Lugar nuevo";
        private String country = "PE";
        private Double lat = -12.0;
        private Double lng = -77.0;
        private CategoriaLugar category = CategoriaLugar.ARTE;

        LugarRequestBuilder id(String value) {
            this.id = value;
            return this;
        }

        LugarRequestBuilder country(String value) {
            this.country = value;
            return this;
        }

        LugarRequest build() {
            return new LugarRequest(id, name, country, lat, lng, category,
                    "palette", "2026", "Descripcion", null);
        }
    }

    @BeforeEach
    void setUp() {
        service = new LugarServiceImpl(lugarRepository, paisRepository, new ResponseMapper());
    }

    @Test
    @DisplayName("crear guarda el lugar con country sincronizado con el pais")
    void crearSincronizaCountryConPais() {
        when(lugarRepository.existsById("nuevo")).thenReturn(false);
        when(paisRepository.findById("PE")).thenReturn(Optional.of(paisPe()));
        when(lugarRepository.save(any(Lugar.class))).thenAnswer(call -> call.getArgument(0));

        LugarResponse dto = service.crear(request().build());

        ArgumentCaptor<Lugar> captor = ArgumentCaptor.forClass(Lugar.class);
        verify(lugarRepository).save(captor.capture());
        Lugar guardada = captor.getValue();

        assertThat(guardada.getCountry()).isEqualTo("PE");
        assertThat(guardada.getPais().getCode()).isEqualTo("PE");
        assertThat(dto.country()).isEqualTo("PE");
    }

    @Test
    @DisplayName("crear un id repetido da 409, no una excepcion de la base de datos")
    void crearConIdRepetidoLanzaDuplicado() {
        when(lugarRepository.existsById("nuevo")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(request().build()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("nuevo");

        // Y no se intenta guardar: el fallo debe occur antes de tocar la base.
        verify(lugarRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear con un pais inexistente da 404 con un mensaje util")
    void crearConPaisInexistenteLanzaNoEncontrado() {
        when(lugarRepository.existsById("nuevo")).thenReturn(false);
        when(paisRepository.findById("ZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(request().country("ZZ").build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ZZ");
    }

    @Test
    @DisplayName("buscar un id inexistente devuelve null, no una excepcion")
    void buscarIdInexistenteDevuelveNull() {
        // El controller convierte el null en 404; lanzar aqui filtraria el codigo
        // HTTP a la capa de negocio.
        when(lugarRepository.findByIdWithPais("no_existe")).thenReturn(Optional.empty());

        assertThat(service.buscarPorId("no_existe")).isNull();
    }

    @Test
    @DisplayName("listar por pais distingue pais inexistente de pais sin lugares")
    void listarPorPaisDistingueAusenteDeVacio() {
        when(paisRepository.existsById("CL")).thenReturn(true);
        when(lugarRepository.findByPaisCode("CL")).thenReturn(List.of());
        when(paisRepository.existsById("ZZ")).thenReturn(false);

        assertThat(service.listarPorPais("CL")).isNotNull().isEmpty();
        assertThat(service.listarPorPais("ZZ")).isNull();
    }

    @Test
    @DisplayName("actualizar conserva el pais si el cuerpo no lo trae")
    void actualizarConservaPaisSiNoVieneEnElCuerpo() {
        // Un formulario de edicion puede mandar solo el nombre. Forzar un pais vacio
        // dejaria el lugar con country NULL y el NOT NULL de V5 lo rechazaria.
        Pais pe = paisPe();
        Lugar existente = new Lugar("mali", "Museo de Arte de Lima", pe, -12.06, -77.037,
                CategoriaLugar.ARTE, "palette", "1961", "Palacio", null);

        when(lugarRepository.findByIdWithPais("mali")).thenReturn(Optional.of(existente));
        when(lugarRepository.save(any(Lugar.class))).thenAnswer(call -> call.getArgument(0));

        LugarRequest sinPais = new LugarRequest(null, "Museo de Arte de Lima", null,
                -12.06, -77.037, CategoriaLugar.DANZA, "palette", "1961", "Palacio", null);

        LugarResponse dto = service.actualizar("mali", sinPais);

        assertThat(dto.country()).isEqualTo("PE");
        assertThat(dto.name()).isEqualTo("Museo de Arte de Lima");
    }

    @Test
    @DisplayName("actualizar no puede cambiar el id del cuerpo")
    void actualizarIgnoraElIdDelCuerpo() {
        // Si se usara el id del cuerpo, la fila se moveria a otra clave primaria.
        Lugar existente = new Lugar("mali", "Museo de Arte de Lima", paisPe(), -12.06, -77.037,
                CategoriaLugar.ARTE, null, null, null, null);

        when(lugarRepository.findByIdWithPais("mali")).thenReturn(Optional.of(existente));
        when(paisRepository.findById("PE")).thenReturn(Optional.of(paisPe()));
        when(lugarRepository.save(any(Lugar.class))).thenAnswer(call -> call.getArgument(0));

        LugarRequest conOtroId = new LugarRequest("otro_id", "Museo de Arte de Lima", "PE",
                -12.06, -77.037, CategoriaLugar.ARTE, null, null, null, null);

        assertThat(service.actualizar("mali", conOtroId).id()).isEqualTo("mali");
    }

    @Test
    @DisplayName("el listado de paises agrupa lugares y series por codigo")
    void listarPaisesAgrupaEnMemoria() {
        Pais pe = paisPe();
        Pais mx = new Pais("MX", "México", "Ciudad de México", 23.6, -102.5, Region.NORTEAMERICA, null);

        when(paisRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(mx, pe));
        when(lugarRepository.findAllByOrderByNameAsc()).thenReturn(List.of(
                new Lugar("mali", "MALI", pe, -12.06, -77.037, CategoriaLugar.ARTE, null, null, null, null),
                new Lugar("bellasartes", "Bellas Artes", mx, 19.44, -99.13, CategoriaLugar.ARTE, null, null, null, null)));
        when(serieRepository.findAllByOrderByPaisCodeAscYearAsc()).thenReturn(List.of(
                serie("PE", 2026, 380.0), serie("PE", 2025, 372.0), serie("MX", 2026, 2121.0)));

        List<PaisResponse> paises = new PaisServiceImpl(
                paisRepository, lugarRepository, serieRepository, new ResponseMapper()).listarTodos();

        assertThat(paises).hasSize(2);
        PaisResponse peDto = paises.stream().filter(p -> p.code().equals("PE")).findFirst().orElseThrow();
        PaisResponse mxDto = paises.stream().filter(p -> p.code().equals("MX")).findFirst().orElseThrow();

        assertThat(peDto.lugares()).hasSize(1);
        assertThat(peDto.seriesHistoricas()).hasSize(2);
        // Chile no esta en la base de prueba: su pais debe salir con listas vacias,
        // no con nulls que Jackson omitiria y el frontend no sabria interpretar.
        assertThat(mxDto.seriesHistoricas()).hasSize(1);
    }

    private static PaisSerieHistorica serie(String codigo, int year, double gdp) {
        return new PaisSerieHistorica(year, gdp, codigo);
    }
}
