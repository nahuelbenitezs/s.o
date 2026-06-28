import java.util.concurrent.atomic.AtomicReference;

/*
 * Clase Amenaza: representa una amenaza aerea entrante.
 *
 * Los atributos basicos son los que pide la letra: id, zona objetivo,
 * instante de aparicion, tiempo restante hasta el impacto y estado.
 * Se agrego tambien TipoMisil, ya que sin ese dato no es posible
 * calcular correctamente las prioridades en los distintos escenarios.
 *
 * Sincronizacion:
 * La situacion concreta que se resuelve es la siguiente: el Interceptor intenta
 * pasar el estado de PENDIENTE a EN_PROCESO, mientras que el Monitor
 * puede intentar, en el mismo instante, pasarlo de PENDIENTE a
 * IMPACTADA. Como las dos transiciones parten del mismo estado, el CAS
 * garantiza que unicamente uno de los dos hilos tenga exito, el otro
 * falla y no genera ningun efecto. De esta manera evitamos
 * que una misma amenaza quede contabilizada dos veces.
 *
 * Calculo de prioridad:
 * Cada estrategia define su propia formula dentro del metodo
 * prioridad(). La PriorityBlockingQueue utiliza un Comparator basado en
 * ese metodo, por lo que el orden es correcto en el momento en que cada
 * amenaza se inserta. Sin embargo, el tiempoRestante disminuye a medida
 * que avanza la simulacion, lo cual hace que ese orden inicial pierda
 * vigencia con el tiempo. Para no implementar una reordenacion continua,
 * el Simulador realiza un drainTo seguido de una reinsercion cada vez
 * que se cambia de estrategia en caliente. Dentro de una misma corrida,
 * esto resulta suficiente para que el desorden no sea relevante.
 */
public class Amenaza {

    private final int id;
    private final Zona zona;
    private final TipoMisil tipoMisil;
    private final long instanteAparicion; // en ms, contado desde que arranca la simulacion

    // Se declara volatile porque el Monitor consulta este valor de forma
    // constante y necesita ver siempre la ultima actualizacion en memoria
    // principal, sin riesgo de leer un valor cacheado. La escritura queda
    // a cargo de un unico hilo (el Monitor), por lo que no existe una
    // condicion de carrera al modificarlo.
    private volatile int tiempoRestante;

    private final AtomicReference<Estado> estado = new AtomicReference<>(Estado.PENDIENTE);

    public Amenaza(int id, Zona zona, TipoMisil tipoMisil, int tiempoRestante, long instanteAparicion) {
        this.id = id;
        this.zona = zona;
        this.tipoMisil = tipoMisil;
        this.tiempoRestante = tiempoRestante;
        this.instanteAparicion = instanteAparicion;
    }

    // Calculo de prioridad
    /**
     * Calcula el nivel de urgencia de la amenaza segun la estrategia activa.
     * Cuanto mayor el valor devuelto, antes deberia ser atendida.
     */
    public double prioridad(Estrategia estrategia) {
        int t = Math.max(tiempoRestante, 1); // se evita la division por cero
        int crit = zona.getCriticidad();
        double vel = tipoMisil.getVelocidad();
        double danio = tipoMisil.getFactorDanio();

        switch (estrategia) {

            case MENOR_TIEMPO:
                // A menor tiempo restante, mayor prioridad. Ademas, si el
                // misil es muy veloz, se incrementa aun mas la prioridad,
                // ya que en la practica el impacto ocurrira antes de lo
                // que indica el tiempoRestante sin ajustar.
                return (10_000.0 / t) * vel;

            case MAYOR_CRITICIDAD:
                // Predomina la criticidad de la zona objetivo. El tiempo
                // restante se utiliza unicamente como criterio de
                // desempate entre amenazas de igual criticidad.
                return crit * 1_000.0 + (1_000.0 / t);

            case COMBINADA:
                // Formula definida en el primer avance, a la cual se le
                // incorpora el tipo de misil para que tambien influyan
                // la velocidad y el factor de daño.
                return (crit * 100.0) + (1_000.0 - t) * (vel * danio);

            case DANIO_ESPERADO:
                // Pensada para minimizar el danio esperado del sistema:
                // privilegia zonas criticas, misiles peligrosos y
                // amenazas cuyo impacto es inminente.
                return crit * danio * (10_000.0 / t);

            default:
                return 0;
        }
    }

    // Transiciones de estado mediante CAS (sin uso de locks)

    // El Interceptor invoca este metodo para reservar la amenaza antes de
    // intentar derribarla. El resultado es true unicamente si fue este
    // hilo el que logro la transicion. si otro interceptor ya la habia
    // tomado, o el Monitor ya la marco como impactada entonces devuelve false
    public boolean intentarTomar() {
        return estado.compareAndSet(Estado.PENDIENTE, Estado.EN_PROCESO);
    }

    // Se invoca una vez completada la interceptacion. No es necesario
    // utilizar CAS en este punto, dado que el hilo ya tiene la propiedad
    // exclusiva de la amenaza desde que intentarTomar() devolvio true.
    public void marcarInterceptada() {
        estado.set(Estado.INTERCEPTADA);
    }

    // El Monitor invoca este metodo cuando el tiempo restante de la
    // amenaza llega a cero. Si la operacion devuelve false, significa
    // que un interceptor obtuvo la amenaza un instante antes, por lo
    // que no corresponde contabilizarla como impacto. Este es el caso
    // concreto que motiva el uso de CAS en lugar de synchronized.
    public boolean intentarImpactar() {
        return estado.compareAndSet(Estado.PENDIENTE, Estado.IMPACTADA);
    }

    // Getters y metodos auxiliares

    public void reducirTiempo(int ms) {
        // La reduccion no es directamente igual a ms: se multiplica por
        // la velocidad del misil, de modo que un misil mas rapido reduce
        // en mayor medida el tiempo restante para un mismo intervalo real.
        tiempoRestante -= (int)(ms * tipoMisil.getVelocidad());
    }

    public Estado getEstado() { return estado.get(); }
    public int getTiempoRestante() { return tiempoRestante; }
    public int getId() { return id; }
    public Zona getZona() { return zona; }
    public TipoMisil getTipoMisil() { return tipoMisil; }
    public long getInstanteAparicion() { return instanteAparicion; }

    // daño que produciria esta amenaza en caso de impactar calculado
    // como el producto entre la criticidad de la zona y el factor de
    // daño del misil
    public double danioEfectivo() {
        return zona.getCriticidad() * tipoMisil.getFactorDanio();
    }

    @Override
    public String toString() {
        return String.format("Amenaza#%d [%s | %s | crit=%d | t=%dms]", id, zona, tipoMisil.name(), zona.getCriticidad(), tiempoRestante);
    }
}