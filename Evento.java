/*Un evento de la simulacion. Puede ser:
 *   LLEGADA -> llega una amenaza nueva
 *   FIN_SERVICIO -> un interceptor termina la recarga
 *   IMPACTO -> a una amenaza se le agota el tiempo
 *
 * Se ordenan por tiempo; ante empate, por tipo y por un numero de secuencia.*/
public class Evento implements Comparable<Evento> {

    public enum Tipo { LLEGADA, FIN_SERVICIO, IMPACTO }

    public final Tipo tipo;
    public final long tiempo;
    public final Amenaza amenaza;
    public final Interceptor interceptor; // solo para FIN_SERVICIO
    public final long seq;

    public Evento(Tipo tipo, long tiempo, Amenaza amenaza, Interceptor interceptor, long seq) {
        this.tipo = tipo;
        this.tiempo = tiempo;
        this.amenaza = amenaza;
        this.interceptor = interceptor;
        this.seq = seq;
    }

    @Override
    public int compareTo(Evento o) {

        if (this.tiempo != o.tiempo) 
            return Long.compare(this.tiempo, o.tiempo);

        if (this.tipo != o.tipo)
            return Integer.compare(this.tipo.ordinal(), o.tipo.ordinal());
        
        return Long.compare(this.seq, o.seq);
    }
}
