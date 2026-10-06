package com.hispania.persistence.entity;

import com.hispania.persistence.converter.RegionConvertidor;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pais de la region, con su capital y su coordenadas para el mapa.
 *
 * <p>La clave primaria es el codigo ISO de dos letras ({@code "PE"}, {@code "MX"}).
 * Las colecciones de lugares y series historicas se mapean en
 * {@link PaisSerieHistoricaEntity} y {@link LugarEntity} para no duplicar aqui las
 * claves foraneas.
 */
@Entity
@Table(name = "paises")
public class Pais {

    @Id
    @Column(name = "code", length = 2, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "capital", length = 100, nullable = false)
    private String capital;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lng", nullable = false)
    private double lng;

    @Convert(converter = RegionConvertidor.class)
    @Column(name = "region", length = 50, nullable = false)
    private Region region;

    @Column(name = "desc_text", length = 1000)
    private String descText;

    protected Pais() {
        // Requerido por JPA.
    }

    public Pais(String code, String name, String capital, double lat, double lng,
                Region region, String descText) {
        this.code = code;
        this.name = name;
        this.capital = capital;
        this.lat = lat;
        this.lng = lng;
        this.region = region;
        this.descText = descText;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCapital() {
        return capital;
    }

    public void setCapital(String capital) {
        this.capital = capital;
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

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public String getDescText() {
        return descText;
    }

    public void setDescText(String descText) {
        this.descText = descText;
    }
}
