package com.hispania.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Serie economica anual de un pais.
 *
 * <p>Cada fila guarda todas las metricas de un anio en la misma tabla, en vez de una
 * tabla por metrica. Asi {@code Pais.seriesHistoricas} es una unica coleccion y el
 * frontend recibe un solo array.
 *
 * <p>La migracion V5 declaro {@code UNIQUE (pais_code, year)}, de modo que no puede
 * haber dos filas para el mismo pais y anio. El frontend da por hecho que la ultima
 * posicion del array es el anio mas reciente, y esa restriccion es lo que lo
 * garantiza.
 *
 * <p>Solo {@code year} y {@code gdp} son obligatorios porque son los unicos que el
 * modelo {@code SeriePunto} del frontend representa; el resto se expands a 0 si
 * llegan nulos.
 */
@Entity
@Table(name = "paises_series_historicas")
public class PaisSerieHistorica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "gdp_current", nullable = false)
    private double gdp;

    @Column(name = "gdp_pc")
    private Double gdpPc;

    @Column(name = "pop")
    private Double pop;

    @Column(name = "growth")
    private Double growth;

    @Column(name = "inflation")
    private Double inflation;

    @Column(name = "exports")
    private Double exports;

    @Column(name = "imports")
    private Double imports;

    @Column(name = "hdi")
    private Double hdi;

    @Column(name = "debt")
    private Double debt;

    @Column(name = "trade")
    private Double trade;

    @Column(name = "pais_code", nullable = false)
    private String paisCode;

    protected PaisSerieHistorica() {
        // Requerido por JPA.
    }

    public PaisSerieHistorica(int year, double gdp, String paisCode) {
        this.year = year;
        this.gdp = gdp;
        this.paisCode = paisCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public double getGdp() {
        return gdp;
    }

    public void setGdp(double gdp) {
        this.gdp = gdp;
    }

    public Double getGdpPc() {
        return gdpPc;
    }

    public void setGdpPc(Double gdpPc) {
        this.gdpPc = gdpPc;
    }

    public Double getPop() {
        return pop;
    }

    public void setPop(Double pop) {
        this.pop = pop;
    }

    public Double getGrowth() {
        return growth;
    }

    public void setGrowth(Double growth) {
        this.growth = growth;
    }

    public Double getInflation() {
        return inflation;
    }

    public void setInflation(Double inflation) {
        this.inflation = inflation;
    }

    public Double getExports() {
        return exports;
    }

    public void setExports(Double exports) {
        this.exports = exports;
    }

    public Double getImports() {
        return imports;
    }

    public void setImports(Double imports) {
        this.imports = imports;
    }

    public Double getHdi() {
        return hdi;
    }

    public void setHdi(Double hdi) {
        this.hdi = hdi;
    }

    public Double getDebt() {
        return debt;
    }

    public void setDebt(Double debt) {
        this.debt = debt;
    }

    public Double getTrade() {
        return trade;
    }

    public void setTrade(Double trade) {
        this.trade = trade;
    }

    public String getPaisCode() {
        return paisCode;
    }

    public void setPaisCode(String paisCode) {
        this.paisCode = paisCode;
    }
}
