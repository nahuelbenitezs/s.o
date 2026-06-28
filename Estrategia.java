import java.util.List;

/*
 * Estrategia de planificacion. Cada estrategia ordena las amenazas pendientes
 * de mayor a menor prioridad en el instante tNow. El Despachador atiende segun
 * ese orden. Cambiar de estrategia es cambiar de implementacion de esta interfaz.
 */
public interface Estrategia {

    String nombre();

    List<Amenaza> ordenar(List<Amenaza> pendientes, long tNow);
}
