package com.hispania.services.impl;

import com.hispania.exception.DuplicateResourceException;
import com.hispania.exception.ForbiddenException;
import com.hispania.exception.ResourceNotFoundException;
import com.hispania.persistence.entity.CategoriaExpresion;
import com.hispania.persistence.entity.EstadoPropuesta;
import com.hispania.persistence.entity.PropuestaExpresion;
import com.hispania.persistence.entity.Rol;
import com.hispania.persistence.entity.Usuario;
import com.hispania.persistence.repository.PaisRepository;
import com.hispania.persistence.repository.PropuestaExpresionRepository;
import com.hispania.persistence.repository.UsuarioRepository;
import com.hispania.presentation.dto.request.EstadoPropuestaRequest;
import com.hispania.presentation.dto.request.PropuestaExpresionRequest;
import com.hispania.presentation.dto.request.RevisionRequest;
import com.hispania.services.interfaces.ExpresionCulturalService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del flujo de propuestas de expresiones culturales.
 *
 * <p>Escalado de {@code PropuestaServiceImplTest}. El repositorio es un doble,
 * asi que no hace falta PostgreSQL. Lo que se comprueba es lo que tiene
 * consecuencias: que aprobar cree de verdad la expresion con el identificador
 * correcto, que la autorevision quede acotada a quien no puede escribir por la via
 * directa, que un rechazo sin motivo no llegue a la base y que el fallo al insertar
 * no deje la propuesta marcada como aprobada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Flujo de propuestas de expresiones culturales")
class PropuestaExpresionServiceImplTest {

    @Mock
    private PropuestaExpresionRepository propuestas;

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private PaisRepository paises;

    @Mock
    private ExpresionCulturalService expresionService;

    private PropuestaExpresionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PropuestaExpresionServiceImpl(propuestas, usuarios, paises, expresionService);
    }

    private static Usuario usuario(Long id, String username) {
        return usuario(id, username, Rol.USUARIO);
    }

    private static Usuario usuario(Long id, String username, Rol rol) {
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

    private static PropuestaExpresionRequest propuestaDe(String titulo, String country) {
        return new PropuestaExpresionRequest(titulo, CategoriaExpresion.MUSICA, country,
                "Descripcion", null, null);
    }

    private static PropuestaExpresion pendiente(Usuario autor) {
        return new PropuestaExpresion("Tango", CategoriaExpresion.MUSICA, "AR",
                "Descripcion", null, null, autor);
    }

    @Test
    @DisplayName("proponer deja la propuesta PENDIENTE")
    void proponer() {
        Usuario autor = usuario(1L, "ana");
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));
        when(paises.existsByCode("PE")).thenReturn(true);
        when(propuestas.save(any(PropuestaExpresion.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.proponer(propuestaDe("Marinera", "pe"), 1L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(response.autor()).isEqualTo("ana");
        // El pais se guarda en mayusculas aunque llegue en minusculas, porque la
        // FK de V8 apunta a paises.code, que las tiene.
        assertThat(response.country()).isEqualTo("PE");
    }

    @Test
    @DisplayName("proponer con un pais inexistente falla antes de guardar nada")
    void propuestaConPaisInexistente() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1L, "ana")));
        when(paises.existsByCode("ZZ")).thenReturn(false);

        assertThatThrownBy(() -> service.proponer(propuestaDe("Expresion rara", "ZZ"), 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("proponer una segunda vez lo mismo y pendiente se rechaza")
    void propuestaDuplicadaDelMismoAutor() {
        Usuario autor = usuario(1L, "ana");
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));
        when(paises.existsByCode("PE")).thenReturn(true);
        when(propuestas.existsPendienteDuplicada(1L, EstadoPropuesta.PENDIENTE,
                CategoriaExpresion.MUSICA, "PE")).thenReturn(true);

        assertThatThrownBy(() -> service.proponer(propuestaDe("Marinera", "PE"), 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("espera a que la revisen");

        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("aprobar crea la expresion con el titulo en minusculas y el pais de sufijo")
    void aprobarCreaLaExpresion() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));
        when(propuestas.save(any(PropuestaExpresion.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.APROBADA, null), 2L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.APROBADA);
        assertThat(response.revisor()).isEqualTo("luis");

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(expresionService).crear(id.capture(), anyString(), any(), anyString(),
                anyString(), any(), any());

        // El id debe cumplir el formato que exige la columna: minusculas, digitos y
        // guion bajo. El sufijo es el pais, porque una expresion no tiene
        // coordenadas que la distingan de la de otro pais.
        assertThat(id.getValue()).matches("^[a-z0-9_]+$");
        assertThat(id.getValue()).isEqualTo("tango_ar");
    }

    @Test
    @DisplayName("aprobar quita tildes y signos del titulo")
    void aprobarNormalizaElTitulo() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaExpresion conTilde = new PropuestaExpresion("Café Cultural!!",
                CategoriaExpresion.GASTRONOMIA, "PE", "Descripcion", null, null, autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(conTilde));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));
        when(propuestas.save(any(PropuestaExpresion.class))).thenAnswer(i -> i.getArgument(0));

        service.revisar(10L, new RevisionRequest(EstadoPropuestaRequest.APROBADA, null), 2L);

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(expresionService).crear(id.capture(), anyString(), any(), anyString(),
                anyString(), any(), any());

        assertThat(id.getValue()).matches("^[a-z0-9_]+$");
        assertThat(id.getValue()).isEqualTo("cafe_cultural_pe");
    }

    @Test
    @DisplayName("aprobar propaga el choque si el pais ya tiene esa categoria")
    void aprobarConCategoriaYaCubierta() {
        // Es el caso que justifica que la aprobacion y la insercion vivan en la misma
        // transaccion: si el pais ya tiene una expresion de esa categoria, la
        // UNIQUE de V8 revienta. Lo que se comprueba aqui es que el fallo suba y
        // que la propuesta NO quede marcada como aprobada, porque la ha revocado
        // el rollback.
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));
        when(expresionService.crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any()))
                .thenThrow(new DuplicateResourceException("Argentina ya tiene una expresion de categoria MUSICA"));

        assertThatThrownBy(() -> service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.APROBADA, null), 2L))
                .isInstanceOf(DuplicateResourceException.class);

        // La propuesta sigue como estaba en memoria: PENDIENTE y sin revisor.
        assertThat(pendiente.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(pendiente.getRevisadoPor()).isNull();
        verify(propuestas, never()).save(any());
    }

    @Test
    @DisplayName("rechazar sin motivo falla y no delega el cambio")
    void rechazarSinMotivo() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));

        assertThatThrownBy(() -> service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.RECHAZADA, "   "), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        verify(propuestas, never()).save(any());
        verify(expresionService, never()).crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("rechazar guarda el motivo y no crea la expresion")
    void rechazarConMotivo() {
        Usuario autor = usuario(1L, "ana");
        Usuario revisor = usuario(2L, "luis");
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(2L)).thenReturn(Optional.of(revisor));
        when(propuestas.save(any(PropuestaExpresion.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.RECHAZADA, "No es una expresion"), 2L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.RECHAZADA);
        assertThat(response.motivoRechazo()).isEqualTo("No es una expresion");
        verify(expresionService, never()).crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("un USUARIO no aprueba su propia propuesta: no tiene otra via para resolverla")
    void usuarioNoSeAutoAprueba() {
        Usuario autor = usuario(1L, "ana");
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));

        assertThatThrownBy(() -> service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.APROBADA, null), 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("propuesta tuya");

        verify(propuestas, never()).save(any());
        verify(expresionService, never()).crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any());
    }

    @ParameterizedTest(name = "un {0} si se aprueba su propia propuesta")
    @EnumSource(value = Rol.class, names = {"COLABORADOR", "ADMIN", "ADMIN_SISTEMA"})
    @DisplayName("quien puede crear expresiones directamente aprueba tambien los suyos")
    void quienEscribeDirectoSeAutoAprueba(Rol rol) {
        // El caso que motiva la regla: alguien propuso como USUARIO, le ascendieron y
        // su propuesta seguia pendiente sin que nadie mas pudiera resolverla. Con la
        // excepcion general anterior, ese administrador se quedaba sin salida.
        Usuario autor = usuario(1L, "ana", rol);
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));
        when(propuestas.save(any(PropuestaExpresion.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.APROBADA, null), 1L);

        assertThat(response.estado()).isEqualTo(EstadoPropuesta.APROBADA);
        assertThat(response.revisor()).isEqualTo("ana");
        verify(expresionService).crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("el rango se toma de la cuenta cargada, que es lo mas reciente")
    void elRangoVieneDeLaCuenta() {
        // La cuenta que se carga desde la base es USUARIO, y por eso no se autoaprueba.
        // Es lo que se busca: si le degradaron sin que su token hubiera caducado, el
        // servicio no le reconoce el poder aunque la sesion diga lo contrario.
        Usuario autor = usuario(1L, "ana", Rol.USUARIO);
        PropuestaExpresion pendiente = pendiente(autor);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(pendiente));
        when(usuarios.findById(1L)).thenReturn(Optional.of(autor));

        assertThatThrownBy(() -> service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.APROBADA, null), 1L))
                .isInstanceOf(ForbiddenException.class);

        verify(expresionService, never()).crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("una propuesta ya revisada no se vuelve a revisar")
    void revisionRepetida() {
        Usuario autor = usuario(1L, "ana");
        PropuestaExpresion yaAprobada = pendiente(autor);
        yaAprobada.revisar(usuario(2L, "luis"), EstadoPropuesta.APROBADA, null);

        when(propuestas.findByIdConAutores(10L)).thenReturn(Optional.of(yaAprobada));

        assertThatThrownBy(() -> service.revisar(10L,
                new RevisionRequest(EstadoPropuestaRequest.RECHAZADA, "cambio de idea"), 2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ya fue revisada");

        verify(expresionService, never()).crear(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any());
    }
}
