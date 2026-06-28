/*
 * Tipos de misil. Cada tipo modifica la dificultad de intercepcion y el
 * danio potencial si impacta.
 *
 * - velocidad    : factor que reduce el tiempo restante por tick (1.0 = normal)
 * - factorDanio  : multiplicador del danio sobre la zona (criticidad * factorDanio)
 * - descripcion  : texto explicativo
 *
 * Esto enriquece la planificacion: un misil hipersonico con danio alto sobre
 * un hospital tiene una prioridad mucho mayor que un misil convencional sobre
 * zona industrial.
 */
public enum TipoMisil {

    CONVENCIONAL (1.0, 1.0, "Misil convencional"),
    HIPERSONICO  (2.0, 1.5, "Misil hipersonico (doble velocidad, danio 1.5x)"),
    BALISTICO    (1.5, 2.0, "Misil balistico (danio critico 2x)"),
    CRUCERO      (0.8, 1.2, "Misil de crucero (lento pero dificil de rastrear)");

    private final double velocidad;
    private final double factorDanio;
    private final String descripcion;

    TipoMisil(double velocidad, double factorDanio, String descripcion) {
        this.velocidad   = velocidad;
        this.factorDanio = factorDanio;
        this.descripcion = descripcion;
    }

    public double getVelocidad()    { return velocidad;    }
    public double getFactorDanio()  { return factorDanio;  }
    public String getDescripcion()  { return descripcion;  }
}
