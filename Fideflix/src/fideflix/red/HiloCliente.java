package fideflix.red;

import fideflix.logica.Audiovisual;
import fideflix.logica.Comentario;
import fideflix.logica.ItemCatalogo;
import fideflix.logica.Usuario;
import fideflix.persistencia.AudiovisualDAO;
import fideflix.persistencia.CatalogoDAO;
import fideflix.persistencia.ComentarioDAO;
import fideflix.persistencia.UsuarioDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/*
 * HILO DE ATENCION de UN cliente (patron "thread-per-connection").
 *
 * ¿Por que un hilo por cliente? Si el servidor atendiera las conexiones
 * en el mismo hilo donde hace accept(), el cliente numero 2 tendria que
 * esperar a que termine el numero 1 (atencion secuencial). Con un hilo
 * por conexion, N clientes se atienden EN PARALELO, que es exactamente
 * lo que pide la consigna.
 *
 * Implementa Runnable (la tarea) en vez de extender Thread (el
 * trabajador): separa QUE se hace de QUIEN lo hace, y deja libre la
 * herencia de la clase.
 *
 * ═══════════════════════════════════════════════════════════════════
 * CAMBIOS DE LA PP5
 * ═══════════════════════════════════════════════════════════════════
 *
 * 1. SIN synchronized. En la PP4 cada operacion se serializaba sobre
 *    CANDADO_ARCHIVO porque escribir un archivo desde varios hilos
 *    corrompe los datos. MySQL resuelve la concurrencia con
 *    transacciones y bloqueo por fila; mantener un candado global
 *    convertiria este servidor multihilo en uno secuencial.
 *
 * 2. RESPUESTAS DE VARIAS LINEAS. procesar() ya no devuelve un String
 *    sino una List<String>. Las respuestas simples son una lista de un
 *    elemento; LISTAR devuelve el encabezado "OK|n" seguido de n
 *    registros.
 *
 * 3. Se mantiene el esquema "una peticion por conexion" (como HTTP/1.0).
 *    La alternativa seria un bucle que mantenga el socket abierto
 *    mientras el cliente exista: mas eficiente, pero exige comando de
 *    salida, deteccion de clientes caidos y gestion de sesion. Se
 *    prefiere lo simple SABIENDO que existe lo complejo, y queda
 *    declarado en el README como evolucion natural.
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
     * Ciclo de vida del hilo: leer 1 peticion -> procesarla -> escribir
     * las lineas de respuesta -> cerrar. El try-with-resources garantiza
     * el cierre del socket aunque el cliente se desconecte de forma
     * abrupta.
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

            List<String> respuesta = procesar(peticion);
            for (String linea : respuesta) {
                salida.println(linea);
            }

            // Se registra solo el encabezado. Volcar los N registros al
            // log inundaria la consola y la volveria inutil justo cuando
            // hace falta leerla.
            log.accept("[" + direccion + "] Respuesta: " + respuesta.get(0)
                     + (respuesta.size() > 1
                        ? " (+" + (respuesta.size() - 1) + " registros)" : ""));

        } catch (IOException e) {
            log.accept("[" + direccion + "] Error de E/S: " + e.getMessage());
        }
    }

    /*
     * "Router" del protocolo: decide que operacion ejecutar segun el
     * comando. split con limite -1 conserva campos vacios al final
     * (p.ej. una descripcion vacia) para poder validarlos, en vez de
     * recibir menos columnas y confundirlo con un formato invalido.
     */
    private List<String> procesar(String peticion) {
        String[] p = peticion.split(Protocolo.SEPARADOR_REGEX, -1);

        return switch (p[0]) {
            // ─── Usuarios ────────────────────────────────────────────
            case Protocolo.CMD_CREAR   -> una(procesarCrear(p));
            case Protocolo.CMD_LOGIN   -> una(procesarLogin(p));

            // ─── CRUD de audiovisuales ───────────────────────────────
            case Protocolo.CMD_LISTAR      -> procesarListar(p);
            case Protocolo.CMD_OBTENER     -> procesarObtener(p);
            case Protocolo.CMD_CREAR_AV    -> una(procesarCrearAv(p));
            case Protocolo.CMD_ACTUALIZAR  -> una(procesarActualizar(p));
            case Protocolo.CMD_ELIMINAR    -> una(procesarEliminar(p));

            // ─── Comentarios y catalogos ─────────────────────────────
            case Protocolo.CMD_COMENTAR       -> una(procesarComentar(p));
            case Protocolo.CMD_LISTAR_COMENTS -> procesarListarComentarios(p);
            case Protocolo.CMD_CATALOGOS      -> procesarCatalogos();

            // Comando desconocido: respuesta controlada, nunca una
            // excepcion hacia el cliente (no revelar internos).
            default -> una(Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                    + "Comando no reconocido");
        };
    }

    // ═════════════════════════════════════════════════════════════════
    // USUARIOS
    // ═════════════════════════════════════════════════════════════════

    /*
     * CREAR|nombre|email|contrasena|fecha   ->   OK|id
     *
     * ESTRATEGIA CONTRA DUPLICADOS: en vez de preguntar "¿ya existe?" y
     * despues insertar (dos pasos, con una ventana entre medio por donde
     * otro hilo puede colarse: condicion de carrera TOCTOU), se intenta
     * insertar y se deja que el UNIQUE del motor rechace. Un solo paso
     * atomico, imposible de burlar por concurrencia.
     *
     * La fecha que envia el cliente se ignora: la pone la base con
     * CURDATE(). Es un hecho que el sistema conoce, no un dato que se le
     * pregunta al usuario (que ademas podria mentir).
     */
    private String procesarCrear(String[] p) {
        if (p.length != 5) {
            return formatoInvalido("CREAR");
        }
        String nombre = p[1], email = p[2], contrasena = p[3];
        if (nombre.isEmpty() || email.isEmpty() || contrasena.isEmpty()) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                    + "Todos los campos son obligatorios";
        }
        try {
            Usuario nuevo = new Usuario(nombre, email, contrasena, "");
            UsuarioDAO.registrar(nuevo);
            return Protocolo.RSP_OK + Protocolo.SEPARADOR + nuevo.getId();

        } catch (SQLIntegrityConstraintViolationException e) {
            // Capturar esta subclase concreta (y no SQLException a secas)
            // permite distinguir "dato duplicado" de "la base se cayo".
            log.accept("Intento de registro duplicado: " + email);
            return Protocolo.RSP_DUPLICADO + Protocolo.SEPARADOR + email;

        } catch (SQLException e) {
            return errorControlado("registrar usuario", e);
        }
    }

    /*
     * LOGIN|email|contrasena   ->   OK|id|nombre|fechaRegistro
     *
     * El id lo necesita el cliente para publicar comentarios: la tabla
     * 'comentario' exige un usuario_id (llave foranea).
     */
    private String procesarLogin(String[] p) {
        if (p.length != 3) {
            return formatoInvalido("LOGIN");
        }
        try {
            Usuario u = UsuarioDAO.autenticar(p[1], p[2]);
            if (u == null) {
                // Respuesta identica para "email inexistente" y
                // "contrasena incorrecta": distinguirlos permitiria
                // enumerar cuentas validas sin adivinar ni una clave.
                return Protocolo.RSP_DENEGADO;
            }
            return Protocolo.RSP_OK + Protocolo.SEPARADOR + u.getId()
                 + Protocolo.SEPARADOR + u.getNombre()
                 + Protocolo.SEPARADOR + u.getFechaRegistro();

        } catch (SQLException e) {
            return errorControlado("autenticar", e);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // CRUD DE AUDIOVISUALES
    // ═════════════════════════════════════════════════════════════════

    /*
     * LISTAR|tipo   ->   OK|n  +  n lineas de registro
     *
     * El encabezado con el conteo permite al cliente saber cuantas
     * lineas leer, sin necesidad de un centinela de fin de mensaje.
     * Mismo principio que el Content-Length de HTTP.
     */
    private List<String> procesarListar(String[] p) {
        if (p.length != 2) {
            return una(formatoInvalido("LISTAR"));
        }
        try {
            List<Audiovisual> obras = AudiovisualDAO.listar(p[1]);

            List<String> salida = new ArrayList<>(obras.size() + 1);
            salida.add(Protocolo.RSP_OK + Protocolo.SEPARADOR + obras.size());
            for (Audiovisual av : obras) {
                salida.add(CodificadorAudiovisual.aLinea(av));
            }
            return salida;

        } catch (SQLException e) {
            return una(errorControlado("listar audiovisuales", e));
        }
    }

    /*
     * OBTENER|id   ->   OK|1 + 1 linea,  o  NO_ENCONTRADO
     */
    private List<String> procesarObtener(String[] p) {
        if (p.length != 2) {
            return una(formatoInvalido("OBTENER"));
        }
        try {
            Audiovisual av = AudiovisualDAO.obtener(entero(p[1]));
            if (av == null) {
                return una(Protocolo.RSP_NO_ENCONTRADO);
            }
            return List.of(Protocolo.RSP_OK + Protocolo.SEPARADOR + 1,
                           CodificadorAudiovisual.aLinea(av));

        } catch (SQLException e) {
            return una(errorControlado("obtener audiovisual", e));
        }
    }

    /*
     * CREAR_AV|tipo|titulo|desc|anio|imdb|clasif|genero|e1|e2|e3
     *   ->   OK|idGenerado
     *
     * El DAO envuelve los dos INSERT (tabla base + tabla hija) en una
     * transaccion: si el segundo falla, el rollback evita dejar una
     * fila huerfana.
     */
    private String procesarCrearAv(String[] p) {
        // 1 comando + 10 campos = 11
        if (p.length != CodificadorAudiovisual.CAMPOS) {
            return formatoInvalido("CREAR_AV");
        }
        try {
            // offset 1: el campo 'tipo' viene justo despues del comando.
            Audiovisual av = CodificadorAudiovisual.desdeCampos(p, 1);

            if (av.getTitulo().isBlank()) {
                return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                        + "El titulo es obligatorio";
            }
            AudiovisualDAO.insertar(av);
            return Protocolo.RSP_OK + Protocolo.SEPARADOR + av.getId();

        } catch (SQLIntegrityConstraintViolationException e) {
            // UNIQUE (titulo, anio_estreno): ya existe esa obra.
            return Protocolo.RSP_DUPLICADO + Protocolo.SEPARADOR
                    + "Ya existe una obra con ese titulo y anio";

        } catch (IllegalArgumentException e) {
            // Tipo desconocido enviado por el cliente. Se responde de
            // forma controlada en vez de dejar morir el hilo.
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR + e.getMessage();

        } catch (SQLException e) {
            return errorControlado("crear audiovisual", e);
        }
    }

    /*
     * ACTUALIZAR|id|tipo|titulo|desc|anio|imdb|clasif|genero|e1|e2|e3
     *   ->   OK|id,  o  NO_ENCONTRADO
     */
    private String procesarActualizar(String[] p) {
        // 1 comando + id + 10 campos = 12
        if (p.length != CodificadorAudiovisual.CAMPOS + 1) {
            return formatoInvalido("ACTUALIZAR");
        }
        try {
            int id = entero(p[1]);
            // offset 2: aqui el 'tipo' viene despues del comando y del id.
            Audiovisual av = CodificadorAudiovisual.desdeCampos(p, 2);
            av.setId(id);

            // actualizar() devuelve false cuando executeUpdate() afecto 0
            // filas, es decir cuando el id no existe. Esa distincion es
            // la que permite responder NO_ENCONTRADO en vez de un OK
            // mentiroso.
            if (!AudiovisualDAO.actualizar(av)) {
                return Protocolo.RSP_NO_ENCONTRADO;
            }
            return Protocolo.RSP_OK + Protocolo.SEPARADOR + id;

        } catch (SQLIntegrityConstraintViolationException e) {
            return Protocolo.RSP_DUPLICADO + Protocolo.SEPARADOR
                    + "Ya existe otra obra con ese titulo y anio";

        } catch (IllegalArgumentException e) {
            return Protocolo.RSP_ERROR + Protocolo.SEPARADOR + e.getMessage();

        } catch (SQLException e) {
            return errorControlado("actualizar audiovisual", e);
        }
    }

    /*
     * ELIMINAR|id   ->   OK|id,  o  NO_ENCONTRADO
     *
     * Basta con borrar la fila base: ON DELETE CASCADE se lleva la fila
     * hija y los comentarios asociados. La integridad la sostiene el
     * motor, no una secuencia de llamadas que alguien podria olvidar.
     */
    private String procesarEliminar(String[] p) {
        if (p.length != 2) {
            return formatoInvalido("ELIMINAR");
        }
        try {
            int id = entero(p[1]);
            if (!AudiovisualDAO.eliminar(id)) {
                return Protocolo.RSP_NO_ENCONTRADO;
            }
            return Protocolo.RSP_OK + Protocolo.SEPARADOR + id;

        } catch (SQLException e) {
            return errorControlado("eliminar audiovisual", e);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // COMENTARIOS Y CATALOGOS
    // ═════════════════════════════════════════════════════════════════

    /*
     * COMENTAR|idAudiovisual|idUsuario|texto   ->   OK|idComentario
     *
     * El texto llega escapado (puede traer '|' y saltos de linea) y se
     * desescapa antes de guardarlo: en la base debe quedar el texto
     * original, no la version codificada para transporte.
     */
    private String procesarComentar(String[] p) {
        if (p.length != 4) {
            return formatoInvalido("COMENTAR");
        }
        try {
            int idAv      = entero(p[1]);
            int idUsuario = entero(p[2]);
            String texto  = Protocolo.desescapar(p[3]).trim();

            if (texto.isEmpty()) {
                return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                        + "El comentario no puede estar vacio";
            }
            int idComentario = ComentarioDAO.insertar(idAv, idUsuario, texto);
            return Protocolo.RSP_OK + Protocolo.SEPARADOR + idComentario;

        } catch (SQLIntegrityConstraintViolationException e) {
            // Fallo una llave foranea: la obra o el usuario no existen.
            return Protocolo.RSP_NO_ENCONTRADO;

        } catch (SQLException e) {
            return errorControlado("publicar comentario", e);
        }
    }

    /*
     * LISTAR_COMENTS|idAudiovisual
     *   ->   OK|n  +  n lineas:  id|autor|fecha|texto
     */
    private List<String> procesarListarComentarios(String[] p) {
        if (p.length != 2) {
            return una(formatoInvalido("LISTAR_COMENTS"));
        }
        try {
            List<Comentario> comentarios =
                    ComentarioDAO.listarPorAudiovisual(entero(p[1]));

            List<String> salida = new ArrayList<>(comentarios.size() + 1);
            salida.add(Protocolo.RSP_OK + Protocolo.SEPARADOR + comentarios.size());

            for (Comentario c : comentarios) {
                salida.add(String.join(Protocolo.SEPARADOR,
                        String.valueOf(c.id()),
                        Protocolo.escapar(c.autor()),
                        c.fechaHora(),
                        Protocolo.escapar(c.texto())));
            }
            return salida;

        } catch (SQLException e) {
            return una(errorControlado("listar comentarios", e));
        }
    }

    /*
     * CATALOGOS   ->   OK|n  +  n lineas:
     *                    GENERO|id|nombre
     *                    CLASIFICACION|id|codigo
     *
     * Una sola peticion trae los dos catalogos: la ventana de formulario
     * necesita ambos para armar sus combos, y dos viajes de red para lo
     * que puede resolverse en uno es desperdicio puro.
     */
    private List<String> procesarCatalogos() {
        try {
            List<ItemCatalogo> generos = CatalogoDAO.listarGeneros();
            List<ItemCatalogo> clasifs = CatalogoDAO.listarClasificaciones();

            List<String> salida = new ArrayList<>(generos.size() + clasifs.size() + 1);
            salida.add(Protocolo.RSP_OK + Protocolo.SEPARADOR
                     + (generos.size() + clasifs.size()));

            for (ItemCatalogo g : generos) {
                salida.add("GENERO" + Protocolo.SEPARADOR + g.id()
                         + Protocolo.SEPARADOR + Protocolo.escapar(g.nombre()));
            }
            for (ItemCatalogo c : clasifs) {
                salida.add("CLASIFICACION" + Protocolo.SEPARADOR + c.id()
                         + Protocolo.SEPARADOR + Protocolo.escapar(c.nombre()));
            }
            return salida;

        } catch (SQLException e) {
            return una(errorControlado("obtener catalogos", e));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ═════════════════════════════════════════════════════════════════

    /* Envuelve una respuesta de una sola linea. List.of() devuelve una
     * lista inmutable: nadie puede modificarla despues por accidente. */
    private static List<String> una(String linea) {
        return List.of(linea);
    }

    private static String formatoInvalido(String comando) {
        return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                + "Formato invalido para " + comando;
    }

    /*
     * Conversion defensiva: el dato viene de la red y puede ser
     * cualquier cosa. Devolver 0 ante texto invalido hace que el DAO no
     * encuentre nada y el servidor responda NO_ENCONTRADO, que es el
     * comportamiento correcto ante un id sin sentido. Lo inaceptable
     * seria dejar escapar una NumberFormatException: mataria el hilo y
     * el cliente quedaria esperando una respuesta que nunca llega.
     */
    private static int entero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    /*
     * Traduce una SQLException a una respuesta de protocolo.
     *
     * ADVERTENCIA DE SEGURIDAD: al cliente se le devuelve un mensaje
     * GENERICO. Los errores de MySQL revelan nombres de tablas, de
     * columnas y a veces fragmentos de la consulta: informacion util
     * para quien quiera atacar el sistema (OWASP A05:2021, Security
     * Misconfiguration). El detalle completo queda en la bitacora del
     * servidor, que es donde lo necesita quien administra, y no viaja
     * por la red.
     */
    private String errorControlado(String operacion, SQLException e) {
        log.accept("ERROR al " + operacion + " [SQLState=" + e.getSQLState()
                 + ", codigo=" + e.getErrorCode() + "]: " + e.getMessage());
        return Protocolo.RSP_ERROR + Protocolo.SEPARADOR
                + "No se pudo completar la operacion. Intente de nuevo.";
    }

    /*
     * Reemplaza el campo contrasena por asteriscos antes de escribir al
     * log. LOGIN|email|pass -> indice 2. CREAR|nom|email|pass|fecha ->
     * indice 3.
     *
     * Ademas recorta las peticiones largas: una descripcion de 500
     * caracteres en la bitacora la vuelve ilegible justo cuando hace
     * falta leerla.
     */
    private String censurar(String peticion) {
        String[] p = peticion.split(Protocolo.SEPARADOR_REGEX, -1);
        int idx = -1;
        if (p[0].equals(Protocolo.CMD_LOGIN) && p.length == 3)  idx = 2;
        if (p[0].equals(Protocolo.CMD_CREAR) && p.length == 5)  idx = 3;
        if (idx >= 0) p[idx] = "*****";

        String resultado = String.join(Protocolo.SEPARADOR, p);
        return resultado.length() > 160
                ? resultado.substring(0, 160) + "... (recortado)"
                : resultado;
    }
}
