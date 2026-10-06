package com.hispania.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import java.time.Instant;

/**
 * Propuesta de expresion cultural pendiente de revision.
 *
 * <p>Misma logica que {@link PropuestaLugar}: un USUARIO propone, un COLABORADOR
 * aprueba o rechaza, y al aprobar se inserta en {@code expresiones_culturales}.
 *
 * <p>No hay columna {@code id} de expresion aqui, y es deliberado, igual que en
 * la cola de lugares: el slug se genera al aprobar, que es cuando se puede
 * comprobar que no choca con ninguna existente. Pedirlo al proponer solo genera
 * errores por duplicados que el proponente no puede ver.
 *
 * <p>Lo que NO hay, a diferencia de {@code propuestas_lugar}, son latitud,
 * longitud, icono y periodo. Una expresion cultural no se visita ni tiene
 * fundacion, y anadir esos campos para rellenarlos con cadenas vacias seria
 * guardar datos que no existen.
 */
@Entity
@Table(name = "propuestas_expresion")
public class PropuestaExpresion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titulo", length = 150, nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 50, nullable = false)
    private CategoriaExpresion categoria;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "desc_text", length = 1000, nullable = false)
    private String descText;

    /**
     * Nombre de fichero dentro de {@code static/expresiones/}, no una URL.
     *
     * <p>No hay endpoint de subida, asi que hoy esto solo lo rellena quien ya
     * dejo el fichero en el proyecto. Es una limitacion consciente y no un
     * olvido: un endpoint de subida necesita decidir tamano maximo, formatos y
     * que se hace con un fichero que nadie revisa.
     */
    @Column(name = "imagen", length = 100)
    private String imagen;

    @Column(name = "creditos", length = 300)
    private String creditos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPropuesta estado = EstadoPropuesta.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propuesto_por", nullable = false)
    private Usuario propuestoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(name = "revisado_at")
    private Instant revisadoAt;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PropuestaExpresion() {
        // Requerido por JPA.
    }

    public PropuestaExpresion(String titulo, CategoriaExpresion categoria, String country,
                               String descText, String imagen, String creditos,
                               Usuario propuestoPor) {
        this.titulo = titulo;
        this.categoria = categoria;
        this.country = country;
        this.descText = descText;
        this.imagen = imagen;
        this.creditos = creditos;
        this.propuestoPor = propuestoPor;
        this.estado = EstadoPropuesta.PENDIENTE;
    }

    @PrePersist
    void alInsertar() {
        this.createdAt = Instant.now();
    }

    /** Marca la propuesta como revisada por {@code revisor}, con su resultado. */
    public void revisar(Usuario revisor, EstadoPropuesta nuevoEstado, String motivo) {
        this.estado = nuevoEstado;
        this.revisadoPor = revisor;
        this.revisadoAt = Instant.now();
        this.motivoRechazo = motivo;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public CategoriaExpresion getCategoria() {
        return categoria;
    }

    public String getCountry() {
        return country;
    }

    public String getDescText() {
        return descText;
    }

    public String getImagen() {
        return imagen;
    }

    public String getCreditos() {
        return creditos;
    }

    public EstadoPropuesta getEstado() {
        return estado;
    }

    public Usuario getPropuestoPor() {
        return propuestoPor;
    }

    public Usuario getRevisadoPor() {
        return revisadoPor;
    }

    public Instant getRevisadoAt() {
        return revisadoAt;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
