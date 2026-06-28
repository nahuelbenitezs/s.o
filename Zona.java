/*
 * Zona objetivo de una amenaza.
 * Cada zona tiene una "criticidad" (1 a 10) que representa su importancia
 * relativa. Los valores fueron definidos en el Primer Avance y justificados
 * segun el impacto humano/infraestructura de cada zona.
 */
public enum Zona {
    HOSPITAL         (10, "Hospital"),
    CENTRAL_ELECTRICA(8,  "Central electrica"),
    DATACENTER       (6,  "Datacenter"),
    RESIDENCIAL      (4,  "Zona residencial"),
    INDUSTRIAL       (2,  "Zona industrial");

    private final int    criticidad;
    private final String nombre;

    Zona(int criticidad, String nombre) {
        this.criticidad = criticidad;
        this.nombre     = nombre;
    }

    public int    getCriticidad() { return criticidad; }
    public String getNombre()     { return nombre;     }

    @Override
    public String toString() { return nombre; }
}
