/*
 * Una amenaza aerea que va dirigida a una zona.
 * Guarda id, zona, instante de llegada, instante de impacto y estado.
 */
public class Amenaza {

    private final int id;
    private final Zona zona;
    private final long instanteLlegada;      // ms en que aparece
    private final int tiempoRestanteInicial; // ms hasta el impacto al aparecer
    private final long instanteImpacto;      // = llegada + tiempoRestanteInicial

    private Estado estado = Estado.PENDIENTE;
    private long instanteInicioServicio = -1;
    private long instanteFinServicio = -1;
    private int interceptorAsignado = -1;

    public Amenaza(int id, Zona zona, int tiempoRestanteInicial, long instanteLlegada) {
        this.id = id;
        this.zona = zona;
        this.tiempoRestanteInicial = tiempoRestanteInicial;
        this.instanteLlegada = instanteLlegada;
        this.instanteImpacto = instanteLlegada + tiempoRestanteInicial;
    }

    // ms que le quedan hasta el impacto en el instante tNow
    public int tiempoRestanteEn(long tNow) {
        return (int) (instanteImpacto - tNow);
    }

    // P = criticidad*10000 + (5000 - tiempoRestante). Se usa para mostrar en el log.
    public int prioridad(long tNow) {
        final int K = 10000;
        final int TMAX = 5000;
        return (zona.getCriticidad() * K) + (TMAX - tiempoRestanteEn(tNow));
    }

    public int getId() { return id; }
    public Zona getZona() { return zona; }
    public long getInstanteLlegada() { return instanteLlegada; }
    public long getInstanteImpacto() { return instanteImpacto; }
    public int getTiempoRestanteInicial() { return tiempoRestanteInicial; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado e) { this.estado = e; }

    public long getInstanteInicioServicio() { return instanteInicioServicio; }
    public void setInstanteInicioServicio(long t) { this.instanteInicioServicio = t; }

    public long getInstanteFinServicio() { return instanteFinServicio; }
    public void setInstanteFinServicio(long t) { this.instanteFinServicio = t; }

    public int getInterceptorAsignado() { return interceptorAsignado; }
    public void setInterceptorAsignado(int n) { this.interceptorAsignado = n; }

    @Override
    public String toString() {
        return "Amenaza#" + id + " [" + zona + ", crit=" + zona.getCriticidad()
                + ", llega=" + instanteLlegada + "ms, impacta=" + instanteImpacto + "ms]";
    }
}
