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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Lugar propuesto por un usuario a la espera de que un colaborador lo apruebe o
 * lo rechace.
 *
 * <p>Copia los campos de {@link Lugar} pero vive en otra tabla a proposito: si se
 * escribiera directamente en {@code lugares}, cualquier usuario podria publicar
 * contenido sin pasar por moderacion, y deshacer una mala publicacion seria
 * borrando datos de la vista de todos. Al aprobar, el servicio inserta la fila en
 * {@code lugares} y marca esta propuesta como APROBADA; la propuesta se conserva
 * como registro de quien propuso que.
 *
 * <p>No tiene columna {@code id} de lugar. El identificador legible se genera en
 * el momento de aprobar, cuando ya se puede comprobar que no choque con los
 * existentes. Pedirlo al proponer solo generaria colisiones que el usuario no
 * puede ver ni resolver.
 */
@Entity
@Table(name = "propuestas_lugar")
public class PropuestaLugar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50, nullable = false)
    private CategoriaLugar category;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "period", length = 50)
    private String period;

    @Column(name = "desc_text", length = 1000)
    private String descText;

    @Column(name = "img", length = 500)
    private String img;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPropuesta estado = EstadoPropuesta.PENDIENTE;

    /** Usuario que propuso el lugar. Perezoso: la lista de pendientes lo lee siempre. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propuesto_por", nullable = false)
    private Usuario propuestoPor;

    /** Colaborador que aprobo o rechazo. Nulo mientras la propuesta esta pendiente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(name = "revisado_at")
    private Instant revisadoAt;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PropuestaLugar() {
        // Requerido por JPA.
    }

    public PropuestaLugar(String nombre, String country, double lat, double lng,
                          CategoriaLugar category, String icon, String period,
                          String descText, String img, Usuario propuestoPor) {
        this.nombre = nombre;
        this.country = country;
        this.lat = lat;
        this.lng = lng;
        this.category = category;
        this.icon = icon;
        this.period = period;
        this.descText = descText;
        this.img = img;
        this.propuestoPor = propuestoPor;
        this.estado = EstadoPropuesta.PENDIENTE;
    }

    @PrePersist
    void alInsertar() {
        this.createdAt = Instant.now();
    }

    /** Aprueba la propuesta y deja constancia de quien lo hizo. */
    public void aprobar(Usuario revisor) {
        this.estado = EstadoPropuesta.APROBADA;
        this.revisadoPor = revisor;
        this.revisadoAt = Instant.now();
        this.motivoRechazo = null;
    }

    /**
     * Rechaza la propuesta. El motivo es obligatorio: la base de datos lo exige
     * con un CHECK, y un rechazo sin explicacion es la principal fuente de
     * quejas de los usuarios en un sistema de moderacion.
     */
    public void rechazar(Usuario revisor, String motivo) {
        this.estado = EstadoPropuesta.RECHAZADA;
        this.revisadoPor = revisor;
        this.revisadoAt = Instant.now();
        this.motivoRechazo = motivo;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCountry() {
        return country;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public CategoriaLugar getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public String getPeriod() {
        return period;
    }

    public String getDescText() {
        return descText;
    }

    public String getImg() {
        return img;
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
