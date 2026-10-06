package com.hispania.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Lugar turistico o cultural asociado a un pais.
 *
 * <p>El identificador es una cadena legible ({@code "machu"}, {@code "chan_chan"})
 * y no un autoincremental, porque el frontend lo usa como clave de React/Angular
 * y como valor del hash de la ruta.
 *
 * <h2>Por que hay dos columnas para el pais</h2>
 * La tabla guarda {@code pais_code} (clave foranea) y {@code country} (texto plano).
 * Se conservaron las dos porque el frontend ya consume {@code country} y cambiar el
 * contrato sería un breaking change sin beneficio. La migración V5 añadió el CHECK
 * {@code country = pais_code} para que no puedan divergir, y por eso esta entidad
 * <strong>nunca</strong> asigna {@code country} de forma independiente: se usa
 * {@link #setPais(Pais)}, que escribe ambas columnas a la vez.
 */
@Entity
@Table(name = "lugares")
public class Lugar {

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    /**
     * Pais al que pertenece el lugar. Se carga de forma perezosa porque
     * {@code GET /api/places} no necesita el nombre del pais, solo su codigo.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pais_code", referencedColumnName = "code", nullable = false)
    private Pais pais;

    /**
     * Copia en texto plano de {@code pais.getCode()}. Se mantiene sincronizada por
     * {@link #setPais(Pais)}; el CHECK de V5 lo garantiza a nivel de base de datos.
     */
    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private CategoriaLugar category;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "period", length = 50)
    private String period;

    @Column(name = "desc_text", length = 1000)
    private String descText;

    @Column(name = "img", length = 500)
    private String img;

    protected Lugar() {
        // Requerido por JPA.
    }

    public Lugar(String id, String name, Pais pais, double lat, double lng,
                CategoriaLugar category, String icon, String period,
                String descText, String img) {
        this.id = id;
        this.name = name;
        setPais(pais);
        this.lat = lat;
        this.lng = lng;
        this.category = category;
        this.icon = icon;
        this.period = period;
        this.descText = descText;
        this.img = img;
    }

    /**
     * Asigna el pais y mantiene {@code country} alineado con la clave foranea.
     * Es la unica via para cambiar el pais de un lugar, precisamente para que las
     * dos columnas no puedan quedar desincronizadas.
     */
    public void setPais(Pais pais) {
        this.pais = pais;
        this.country = pais != null ? pais.getCode() : null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Pais getPais() {
        return pais;
    }

    public String getCountry() {
        return country;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public CategoriaLugar getCategory() {
        return category;
    }

    public void setCategory(CategoriaLugar category) {
        this.category = category;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getDescText() {
        return descText;
    }

    public void setDescText(String descText) {
        this.descText = descText;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }
}
