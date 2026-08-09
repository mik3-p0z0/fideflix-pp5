package fideflix.red;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.function.Consumer;

/*
 * NUCLEO DEL SERVIDOR (sin interfaz grafica).
 *
 * Arquitectura de hilos:
 *
 *   [Hilo de Swing/EDT]      -> dibuja VentanaServidor, atiende botones.
 *   [Hilo "aceptador"]       -> bucle infinito en accept(), UNO solo.
 *   [Hilos HiloCliente x N]  -> uno por cada cliente conectado.
 *
 * accept() es BLOQUEANTE: se queda dormido hasta que llega una conexion.
 * Por eso NO puede ejecutarse en el hilo de Swing (congelaria la ventana);
 * vive en su propio hilo aceptador. Cada conexion aceptada se delega de
 * inmediato a un HiloCliente nuevo y el aceptador vuelve a accept():
 * asi se logra la "escucha constante" que exige la consigna.
 *
 * Separar esta clase de VentanaServidor (logica vs interfaz) sigue la misma
 * division por capas del resto del proyecto: red/, logica/, interfaz/,
 * persistencia/.
 */
public class ServidorFideflix {

    /*
     * ─── PP5: SE ELIMINO EL CANDADO GLOBAL ──────────────────────────
     * Aqui vivia CANDADO_ARCHIVO, el objeto sobre el que todos los
     * HiloCliente sincronizaban para no pisarse al escribir usuarios.dat.
     * Con un archivo era imprescindible: leer-modificar-guardar no es
     * atomico y el ultimo en guardar borraba el trabajo del otro.
     *
     * Con MySQL el candado no solo sobra: perjudica. El motor ya
     * garantiza el acceso concurrente mediante transacciones y bloqueo
     * a nivel de FILA (InnoDB), que es mucho mas fino que un candado
     * global de la JVM. Mantenerlo obligaria a que los N hilos pasen de
     * a uno por la base, anulando en la practica el multihilo que la
     * consigna pide.
     *
     * La atomicidad ya no la sostiene un synchronized de Java: la
     * sostienen el COMMIT/ROLLBACK y las restricciones del motor.
     */

    private ServerSocket serverSocket;
    private Thread hiloAceptador;

    /*
     * volatile: la bandera la escribe el hilo de Swing (boton Detener) y la
     * lee el hilo aceptador. volatile garantiza que el cambio sea VISIBLE
     * entre hilos de inmediato (sin cachearse en registros de CPU).
     */
    private volatile boolean activo = false;

    /* Bitacora inyectada: la ventana pasa un Consumer que escribe en su
     * JTextArea. La logica de red no importa nada de Swing. */
    private final Consumer<String> log;

    public ServidorFideflix(Consumer<String> log) {
        this.log = log;
    }

    /*
     * Abre el puerto y lanza el hilo aceptador.
     * synchronized aqui evita que dos clics rapidos en "Iniciar" creen
     * dos ServerSocket sobre el mismo puerto.
     */
    public synchronized void iniciar() throws IOException {
        if (activo) {
            return; // ya estaba corriendo
        }
        // ServerSocket = enchufe de ESCUCHA. Reserva el puerto ante el SO.
        // Si el puerto ya esta ocupado lanza BindException (subclase de IOException).
        serverSocket = new ServerSocket(Protocolo.PUERTO);
        activo = true;

        hiloAceptador = new Thread(this::aceptarClientes, "aceptador-fideflix");
        // Daemon: si solo queda este hilo vivo, la JVM puede terminar.
        hiloAceptador.setDaemon(true);
        hiloAceptador.start();

        log.accept("Servidor iniciado. Escuchando en el puerto " + Protocolo.PUERTO + "...");
    }

    /*
     * Bucle del hilo aceptador. Cada accept() devuelve un Socket nuevo,
     * exclusivo para ese cliente, y se delega a un HiloCliente.
     */
    private void aceptarClientes() {
        int contador = 0;
        while (activo) {
            try {
                Socket socketCliente = serverSocket.accept(); // BLOQUEA aqui
                contador++;
                log.accept("Conexion #" + contador + " aceptada desde "
                        + socketCliente.getRemoteSocketAddress());

                Thread hilo = new Thread(new HiloCliente(socketCliente, log),
                                         "cliente-" + contador);
                hilo.start();

            } catch (SocketException e) {
                // Ocurre cuando detener() cierra el ServerSocket para
                // despertar al accept(). Si fue apagado intencional, salimos
                // en silencio; si no, era un error real.
                if (activo) {
                    log.accept("Error en el socket de escucha: " + e.getMessage());
                }
            } catch (IOException e) {
                log.accept("Error aceptando conexion: " + e.getMessage());
            }
        }
        log.accept("Servidor detenido.");
    }

    /*
     * Detiene la escucha. Truco clasico: accept() no revisa banderas (esta
     * dormido), asi que cerrar el ServerSocket le provoca una SocketException
     * que lo despierta y permite salir del bucle limpiamente.
     * Los HiloCliente ya lanzados terminan su peticion en curso.
     */
    public synchronized void detener() {
        if (!activo) {
            return;
        }
        activo = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.accept("Error al cerrar el socket de escucha: " + e.getMessage());
        }
    }

    public boolean estaActivo() {
        return activo;
    }
}
