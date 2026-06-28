//Estados posibles por los que pasa una amenaza durante la simulacion.
public enum Estado {
    PENDIENTE,      // esta en la cola esperando ser atendida
    EN_PROCESO,     // un interceptor la tomo y la esta atendiendo
    INTERCEPTADA,   // fue atendida a tiempo (exito)
    IMPACTADA       // se le agoto el tiempo antes de ser atendida (fallo)
}
