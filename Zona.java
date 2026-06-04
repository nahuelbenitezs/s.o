/*
 * Zona objetivo de una amenaza.
 * Cada zona tiene una "criticidad" (1 a 10) que representa su importancia.
 * Estos valores son los definidos en el Primer Avance.
 */
public enum Zona {
    HOSPITAL(10),
    CENTRAL_ELECTRICA(8),
    DATACENTER(6),
    RESIDENCIAL(4),
    INDUSTRIAL(2);

    private final int criticidad;

    Zona(int criticidad) {
        this.criticidad = criticidad;
    }

    public int getCriticidad() {
        return criticidad;
    }
}
