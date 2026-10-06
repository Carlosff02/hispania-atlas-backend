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
import java.time.Instant;

/**
 * Expresion cultural de un pais: una tradicion, un movimiento artistico, un
 * genero musical, una tecnica.
 *
 * <p>No es un {@link Lugar} con otro nombre, y por eso tiene tabla propia. Un
 * lugar se visita y por eso tiene latitud, longitud y periodo; "La Marinera" y
 * "El Tango" no se visitan y no tienen fundacion. Meter una danza en
 * `lugares` obligaria a inventar esas tres columnas.
 *
 * <p>A diferencia de {@link Lugar}, que guarda el pais dos veces (la referencia
 * {@code pais} y el {@code country} suelto), aqui solo hay una FK. El texto libre
 * es exactamente el fallo que produjo `paises.region` con `CONO_SUR`: un dato
 * que no participa en ninguna consulta deja de notarse cuando se ensucia.
 *
 * <p>La imagen es un NOMBRE de fichero servido desde
 * {@code static/expresiones/}, no una URL. Las cuatro que tenia el frontend eran
 * enlaces a Bing y Pinterest, de licencia desconocida.
 */
@Entity
@Table(name = "expresiones_culturales")
public class ExpresionCultural {

    /** Slug legible, como el id de {@link Lugar}: va en la URL y en el alt de la tarjeta. */
    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @Column(name = "titulo", length = 150, nullable = false)
    private String titulo;

    /**
     * Se persiste por NOMBRE, no por ordinal.
     *
     * <p>Sin {@code @Enumerated(EnumType.STRING)} Hibernate guarda la posicion en
     * el enum (un {@code SMALLINT}), y ahi el orden de las constantes es parte del
     * formato: insertar {@code MUSICA} entre {@code DANZA} y {@code TEATRO}
     * cambiaria de significado todas las filas ya guardadas. Con el nombre, el
     * dato se lee en claro y el CHECK de V8 puede compararlo con la lista.
     *
     * <p>No es una hipotesis: {@code ddl-auto=validate} lo detecto al arrancar
     * contra Postgres, por encontrar {@code varchar} donde Hibernate esperaba
     * {@code smallint}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 50, nullable = false)
    private CategoriaExpresion categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pais_code", referencedColumnName = "code", nullable = false)
    private Pais pais;

    @Column(name = "desc_text", length = 1000, nullable = false)
    private String descText;

    @Column(name = "imagen", length = 100)
    private String imagen;

    /**
     * Autor y licencia de la imagen. El CHECK de V8 no permite una imagen sin
     * creditos: material de origen desconocido no entra.
     */
    @Column(name = "creditos", length = 300)
    private String creditos;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpresionCultural() {
        // Requerido por JPA.
    }

    public ExpresionCultural(String id, String titulo, CategoriaExpresion categoria,
                             Pais pais, String descText, String imagen,
                             String creditos, Instant createdAt) {
        this.id = id;
        this.titulo = titulo;
        this.categoria = categoria;
        this.pais = pais;
        this.descText = descText;
        this.imagen = imagen;
        this.creditos = creditos;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public CategoriaExpresion getCategoria() {
        return categoria;
    }

    public Pais getPais() {
        return pais;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
