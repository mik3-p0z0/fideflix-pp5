package fideflix.red;

import fideflix.logica.Usuario;
import fideflix.persistencia.PersistenciaUsuarios;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Consumer;

/*
 * HILO DE ATENCION de UN cliente (patron "thread-per-connection").
 *
 * ¿Por que un hilo por cliente? Si el servidor atendiera las conexiones en
 * el mismo hilo donde hace accept(), el cliente numero 2 tendria que esperar
 * a que termine el numero 1 (atencion secuencial). Con un hilo por conexion,
 * N clientes se atienden EN PARALELO, que es exactamente lo que pide la
 * consigna: "escucha constante de peticiones de multiples aplicaciones
 * cliente... es necesario el uso de hilos".
 *
 * Implementa Runnable (la tarea) en vez de extender Thread (el trabajador):
 * separa QUE se hace de QUIEN lo hace, y deja libre la herencia de la clase.
 */
public class HiloCliente implements Runnable {

    private final Socket socket;
    /* Callback de bitacora: el servidor decide DONDE se muestra el log
     * (en nuestro caso, el JTextArea de VentanaServidor). El hilo no
     * conoce a Swing: bajo acoplamiento. */
    private final Consumer<String> log;

    public HiloCliente(Socket socket, Consumer<String> log) {
        this.socket = socket;
        this.log = log;
    }

    /*
     * Ciclo de vida del hilo: leer 1 peticion -> procesarla -> responder 1
     * linea -> cerrar. El try-with-resources garantiza el cierre del socket
     * aunque el cliente se desconecte de forma abrupta.
     */
    @Override
    public void run() {
        String direccion = String.valueOf(socket.getRemoteSocketAddress());
        try (Socket s = socket;
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(s.getInputStream(),
                                           StandardCharsets.UTF_8));
             PrintWriter salida = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(),
                                            StandardCharsets.UTF_8), true)) {

            String peticion = entrada.readLine();
            if (peticion == null) {
                log.accept("[" + direccion + "] conexion cerrada sin datos.");
                return;
            }

            // Seguridad: NUNCA registrar contrasenas en la bitacora.
            log.accept("[" + direccion + "] Peticion: " + censurar(peticion));

            String respuesta = procesar(peticion);
            salida.println(respuesta);

            log.accept("[" + direccion + "] Respuesta: " + respuesta);

        } catch (IOException e) {
            log.accept("[" + direccion + "] Error de E/S: " + e.getMessage());
        }
    }

    /*
     * "Router" del protocolo: decide que operacion ejecutar segun el comando.
     * split con limite -1 conserva campos vacios al final (p.ej. contrasena
     * vacia) para poder validarlos en vez de recibir menos columnas.
     */
    private String procesar(String peticion) {
        String[] p = peticion.split(Protocolo.SEPARADOR_REGEX, -1);
        switch (p[0]) {
            case Protocolo.CMD_CREAR:
                return procesarCrear(p);
            case Protocolo.CMD_LOGIN:
                return procesarLogin(p);
            default:
                // Comando desconocido: respuesta controlada, nunca una excepcion
                // hacia el cliente (no revelar internos del servidor).
                return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                        + "Comando no reconocido";
        }
    }

    /*
     * CREAR|nombre|email|contrasena|fecha
     *
     * ZONA CRITICA: leer-modificar-guardar el archivo NO es atomico.
     * Si dos hilos lo hicieran a la vez, el ultimo en guardar pisaria al
     * otro (condicion de carrera, "lost update"). El bloque synchronized
     * sobre un candado UNICO y compartido serializa el acceso al archivo:
     * solo un hilo a la vez puede entrar.
     */
    private String procesarCrear(String[] p) {
        if (p.length != 5) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                    + "Formato invalido para CREAR";
        }
        String nombre = p[1], email = p[2], contrasena = p[3], fecha = p[4];
        if (nombre.isEmpty() || email.isEmpty() || contrasena.isEmpty() || fecha.isEmpty()) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                    + "Todos los campos son obligatorios";
        }
        try {
            synchronized (ServidorFideflix.CANDADO_ARCHIVO) {
                ArrayList<Usuario> usuarios = PersistenciaUsuarios.cargar();
                Usuario nuevo = new Usuario(nombre, email, contrasena, fecha);

                // Usuario.equals() compara por email -> contains() detecta duplicados.
                if (usuarios.contains(nuevo)) {
                    return Protocolo.RSP_DUPLICADO + Protocolo.SEPARADOR + email;
                }
                usuarios.add(nuevo);
                PersistenciaUsuarios.guardar(usuarios);
                return Protocolo.RSP_OK + Protocolo.SEPARADOR + usuarios.size();
            }
        } catch (IOException | ClassNotFoundException e) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR + e.getMessage();
        }
    }

    /*
     * LOGIN|email|contrasena
     * Respuesta OK|nombre|fechaRegistro para que el cliente pueda armar
     * su objeto Usuario y saludar en el menu principal.
     * La lectura tambien se sincroniza para no leer el archivo mientras
     * otro hilo lo esta reescribiendo a medias.
     */
    private String procesarLogin(String[] p) {
        if (p.length != 3) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                    + "Formato invalido para LOGIN";
        }
        String email = p[1], contrasena = p[2];
        try {
            Usuario encontrado;
            synchronized (ServidorFideflix.CANDADO_ARCHIVO) {
                ArrayList<Usuario> usuarios = PersistenciaUsuarios.cargar();
                encontrado = PersistenciaUsuarios.autenticar(usuarios, email, contrasena);
            }
            if (encontrado == null) {
                return Protocolo.RSP_DENEGADO;
            }
            return Protocolo.RSP_OK + Protocolo.SEPARADOR
                    + encontrado.getNombre() + Protocolo.SEPARADOR
                    + encontrado.getFechaRegistro();
        } catch (IOException | ClassNotFoundException e) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR + e.getMessage();
        }
    }

    /*
     * Reemplaza el campo contrasena por *** antes de escribir al log.
     * LOGIN|email|pass  -> indice 2.  CREAR|nom|email|pass|fecha -> indice 3.
     */
    private String censurar(String peticion) {
        String[] p = peticion.split(Protocolo.SEPARADOR_REGEX, -1);
        int idx = -1;
        if (p[0].equals(Protocolo.CMD_LOGIN) && p.length == 3)  idx = 2;
        if (p[0].equals(Protocolo.CMD_CREAR) && p.length == 5)  idx = 3;
        if (idx >= 0) p[idx] = "*****";
        return String.join(Protocolo.SEPARADOR, p);
    }
}
