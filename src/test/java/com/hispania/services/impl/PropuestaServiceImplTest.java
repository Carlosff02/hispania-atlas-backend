package com.hispania.services.impl;

import com.hispania.exception.ForbiddenException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.CategoriaLugar;
import com.hispania.persistence.entity.EstadoPropuesta;
import com.hispania.persistence.entity.PropuestaLugar;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.persistence.repository.PropuestaLugarRepository;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.request.LugarRequest;
import com.hispania.presentation.dto.request.PropuestaRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.services.interfaces.LugarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del flujo de propuestas.
 *
 * <p>Lo que se comprueba aqui es la parte con consecuencias: que aprobar cree de
 * verdad el lugar, que la autorevision quede acotada a quien no puede escribir por la
 * via directa y que un rechazo sin motivo no llegue a la base. El repositorio es un
 * doble, asi que estas pruebas no necesitan PostgreSQL; el CHECK que exige el motivo
 * se verifica aparte, en la migracion V6.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Flujo de propuestas de lugares")
class PropuestaServiceImplTest {

    @Mock
    private PropuestaLugarRepository propuestas;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private PaisRepository paises;

    @Mock
    private LugarService lugarService;

    private PropuestaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PropuestaServiceImpl(propuestas, usuarios, paises, lugarService);
    }

    private static Usuario usuario(Long id, String username) {
        return usuario(id, username, com.hispania.persistence.entity.Rol.USUARIO);
    }

    private static Usuario usuario(Long id, String username, com.hispania.persistence.entity.Rol rol) {
        Usuario u = new Usuario(username, username + "@test.com", "hash", username, rol);
        // El id lo asigna la base de datos, asi que en las pruebas se fuerza por
        // reflexion sobre el campo, que es la unica forma sin base de datos.
        try {
            var campo = Usuario.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo asignar el id en la prueba", e);
        }
        return u;
    }

    private static PropuestaRequest propuestaDe(String nombre, String country) {
        return new PropuestaRequest(nombre, country, -13.532, -71.967,
                CategoriaLugar.ARQUEOLOGIA, "mapa", "Precolombino", "Descripcion", "img.jpg");
    }

    @Test
    @DisplayName("proponer deja la propuesta PENDIENTE")
    void proponer() {
        Usuario autor = usuario(1L, "ana");
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));
        when(paises.existsByCode("PE")).thenReturn(true);
        when(propuestas.save(any(PropuestaLugar.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.proponer(propuestaDe("Machu Picchu", "pe"), 1L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(response.propuestoPor()).isEqualTo("ana");
        assertThat(response.country()).isEqualTo("PE");
    }

    @Test
    @DisplayName("proponer con un pais inexistente falla antes de guardar nada")
    void propuestaConPaisInexistente() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1L, "ana")));
        when(paises.existsByCode("ZZ")).thenReturn(false);

        assertThatThrownBy(() -> service.proponer(propuestaDe("Lugar raro", "ZZ"), 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("aprobar crea el lugar con un id derivado del nombre y sin tildes")
    void aprobarCreaElLugar() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaLugar pendiente = new PropuestaLugar("Machu Picchu", "PE", -13.532, -71.967,
                CategoriaLugar.ARQUEOLOGIA, null, null, null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));
        when(propuestas.save(any(PropuestaLugar.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.APROBADA, null), 2L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.APROBADA);
        assertThat(response.revisadoPor()).isEqualTo("luis");

        // El id debe cumplir el formato que exige la columna: minusculas,
        // digitos y guion bajo, sin tildes ni puntos decimales.
        ArgumentCaptor<LugarRequest> captor = ArgumentCaptor.forClass(LugarRequest.class);
        verify(lugarService).crear(captor.capture());
        assertThat(captor.getValue().id()).matches("^[a-z0-9_]+$");
        assertThat(captor.getValue().id()).startsWith("machu_picchu_");
    }

    @Test
    @DisplayName("rechazar sin motivo falla y no delega el cambio")
    void rechazarSinMotivo() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaLugar pendiente = new PropuestaLugar("Algo", "PE", 0, 0,
                CategoriaLugar.ARTE, null, null, null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));

        assertThatThrownBy(() -> service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.RECHAZADA, "   "), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        verify(propuestas, never()).save(any());
        verify(lugarService, never()).crear(any());
    }

    @Test
    @DisplayName("rechazar guarda el motivo y no crea el lugar")
    void rechazarConMotivo() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaLugar pendiente = new PropuestaLugar("Algo", "PE", 0, 0,
                CategoriaLugar.ARTE, null, null, null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));
        when(propuestas.save(any(PropuestaLugar.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.RECHAZADA,
                "No es un lugar turistico"), 2L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.RECHAZADA);
        assertThat(response.motivoRechazo()).isEqualTo("No es un lugar turistico");
        verify(lugarService, never()).crear(any());
    }

    @Test
    @DisplayName("un USUARIO no aprueba su propia propuesta: no tiene otra via para resolverla")
    void usuarioNoSeAutoAprueba() {
        Usuario autor = usuario(1L, "ana");
        PropuestaLugar pendiente = new PropuestaLugar("Algo", "PE", 0, 0,
                CategoriaLugar.ARTE, null, null, null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));

        assertThatThrownBy(() -> service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.APROBADA, null), 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("propuesta tuya");

        verify(propuestas, never()).save(any());
        verify(lugarService, never()).crear(any());
    }

    @ParameterizedTest(name = "un {0} si se aprueba su propia propuesta")
    @EnumSource(value = Rol.class, names = {"COLABORADOR", "ADMIN", "ADMIN_SISTEMA"})
    @DisplayName("quien puede crear lugares directamente aprueba tambien los suyos")
    void quienEscribeDirectoSeAutoAprueba(Rol rol) {
        // El caso que motiva la regla: alguien propuso como USUARIO, le ascendieron y
        // su propuesta seguia pendiente sin que nadie mas pudiera resolverla. Con la
        // excepcion general anterior, ese administrador se quedaba sin salida.
        Usuario autor = usuario(1L, "ana", rol);
        PropuestaLugar pendiente = new PropuestaLugar("Algo", "PE", 0, 0,
                CategoriaLugar.ARTE, null, null, null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));
        when(propuestas.save(any(PropuestaLugar.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.APROBADA, null), 1L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.APROBADA);
        assertThat(response.revisadoPor()).isEqualTo("ana");
        verify(lugarService).crear(any());
    }

    @Test
    @DisplayName("el rango se toma de la cuenta cargada, que es lo mas reciente")
    void elRangoVieneDeLaCuenta() {
        // La cuenta que se carga desde la base es USUARIO, y por eso no se autoaprueba.
        // Es lo que se busca: si le degradaron sin que su token hubiera caducado, el
        // servicio no le reconoce el poder aunque la sesion diga lo contrario.
        Usuario autor = usuario(1L, "ana", Rol.USUARIO);
        PropuestaLugar pendiente = new PropuestaLugar("Algo", "PE", 0, 0,
                CategoriaLugar.ARTE, null, null, null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));

        assertThatThrownBy(() -> service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.APROBADA, null), 1L))
                .isInstanceOf(ForbiddenException.class);

        verify(lugarService, never()).crear(any());
    }

    @Test
    @DisplayName("una propuesta ya revisada no se vuelve a revisar")
    void revisionRepetida() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaLugar yaAprobada = new PropuestaLugar("Algo", "PE", 0, 0,
                CategoriaLugar.ARTE, null, null, null, null, autor);
        yaAprobada.aprobar(revisor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(yaAprobada));

        assertThatThrownBy(() -> service.revisar(10L, new RevisionRequest(
                com.hispania.presentation.dto.request.EstadoPropuestaRequest.RECHAZADA, "cambio de idea"), 2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ya fue revisada");
    }
}
