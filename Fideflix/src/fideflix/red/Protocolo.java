package fideflix.red;

/*
 * PROTOCOLO DE APLICACION de Fideflix.
 *
 * Un protocolo es simplemente el "idioma" que cliente y servidor acuerdan
 * hablar sobre el socket. TCP solo transporta bytes; el SIGNIFICADO de esos
 * bytes lo definimos nosotros aqui. (Igual que HTTP define GET/POST sobre TCP,
 * nosotros definimos LOGIN/CREAR sobre TCP).
 *
 * Formato: UNA linea de texto por peticion y UNA linea por respuesta,
 * con campos separados por '|'.
 *
 *   Peticiones (cliente -> servidor):
 *     CREAR|nombre|email|contrasena|fechaRegistro
 *     LOGIN|email|contrasena
 *
 *   Respuestas (servidor -> cliente):
 *     OK|...        exito (LOGIN devuelve OK|id|nombre|fechaRegistro,
 *                          CREAR devuelve OK|idGenerado)
 *     DENEGADO      credenciales invalidas en LOGIN
 *     DUPLICADO|em  ya existe un usuario con ese email
 *     ERROR|detalle fallo interno del servidor
 *
 * ─── CAMBIOS DE LA PP5 ──────────────────────────────────────────────
 *
 * 1. LOGIN ahora devuelve tambien el ID del usuario. El cliente lo
 *    necesita para publicar comentarios: la tabla 'comentario' exige un
 *    usuario_id (llave foranea) y sin el no se puede atribuir el
 *    comentario a nadie.
 *
 * 2. CREAR devuelve el ID generado por MySQL en lugar del total de
 *    usuarios. El id identifica al registro que se acaba de crear; el
 *    total obligaba a contar la tabla entera en cada alta para un dato
 *    que nadie usaba.
 *
 * 3. El campo fechaRegistro de CREAR se sigue aceptando por
 *    compatibilidad del formato, pero el servidor lo IGNORA: la fecha
 *    la pone la base con CURDATE(). Es un hecho que el sistema conoce,
 *    no un dato que se le pregunta al usuario.
 *
 * Esta clase centraliza las constantes para que cliente y servidor nunca
 * se desincronicen (si el comando cambia, cambia en UN solo lugar).
 *
 * ADVERTENCIA DE SEGURIDAD:
 * - Las contrasenas SI se almacenan como hash desde la PP5 (ver
 *   UsuarioDAO), pero siguen VIAJANDO EN TEXTO PLANO por el socket.
 *   Resolverlo exige TLS (SSLSocket / certificados). Limitacion
 *   declarada en el README. Ver OWASP ASVS v4 cap. 2 (autenticacion).
 * - El hash actual es SHA-256 sin salt: minimo academico, insuficiente
 *   para produccion (ver la advertencia extendida en UsuarioDAO).
 */
public final class Protocolo {

    /* Direccion del servidor. "localhost" = misma maquina.
     * Si el servidor corriera en otra PC de la red, aqui iria su IP. */
    public static final String HOST = "localhost";

    /* Puerto de escucha. Se elige uno > 1024 (los menores estan reservados
     * al sistema operativo / servicios conocidos). */
    public static final int PUERTO = 5000;

    /* Separador de campos del mensaje. */
    public static final String SEPARADOR = "|";

    /* El mismo separador, escapado para usarlo con String.split(),
     * porque '|' es un caracter especial (OR) en expresiones regulares. */
    public static final String SEPARADOR_REGEX = "\\|";

    // ─── Comandos: usuarios ──────────────────────────────────────────────
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_CREAR = "CREAR";

    // ─── Comandos: CRUD de audiovisuales (PP5) ───────────────────────────
    /* LISTAR|tipo            tipo = TODOS | PELICULA | DOCUMENTAL | SERIE
     *                        -> OK|n  y luego n lineas de registro        */
    public static final String CMD_LISTAR = "LISTAR";

    /* OBTENER|id             -> OK|1 + 1 linea, o NO_ENCONTRADO           */
    public static final String CMD_OBTENER = "OBTENER";

    /* CREAR_AV|tipo|titulo|desc|anio|imdb|clasif|genero|esp1|esp2|esp3
     *                        -> OK|idGenerado                             */
    public static final String CMD_CREAR_AV = "CREAR_AV";

    /* ACTUALIZAR|id|tipo|titulo|desc|anio|imdb|clasif|genero|e1|e2|e3
     *                        -> OK|id, o NO_ENCONTRADO                    */
    public static final String CMD_ACTUALIZAR = "ACTUALIZAR";

    /* ELIMINAR|id            -> OK|id, o NO_ENCONTRADO                    */
    public static final String CMD_ELIMINAR = "ELIMINAR";

    // ─── Comandos: comentarios y catalogos ───────────────────────────────
    /* COMENTAR|idAv|idUsuario|texto      -> OK|idComentario               */
    public static final String CMD_COMENTAR = "COMENTAR";

    /* LISTAR_COMENTS|idAv    -> OK|n + n lineas: id|autor|fecha|texto     */
    public static final String CMD_LISTAR_COMENTS = "LISTAR_COMENTS";

    /* CATALOGOS              -> OK|n + n lineas: GENERO|id|nombre
     *                                         o CLASIFICACION|id|codigo   */
    public static final String CMD_CATALOGOS = "CATALOGOS";

    // ─── Tipos de audiovisual (deben coincidir con el ENUM de MySQL) ─────
    public static final String TIPO_TODOS      = "TODOS";
    public static final String TIPO_PELICULA   = "PELICULA";
    public static final String TIPO_DOCUMENTAL = "DOCUMENTAL";
    public static final String TIPO_SERIE      = "SERIE";

    // ─── Codigos de respuesta ────────────────────────────────────────────
    public static final String RSP_OK            = "OK";
    public static final String RSP_DENEGADO      = "DENEGADO";
    public static final String RSP_DUPLICADO     = "DUPLICADO";
    public static final String RSP_ERROR         = "ERROR";
    /* PP5: el id solicitado no existe. Se distingue de ERROR porque no es
     * un fallo del sistema sino un resultado legitimo del negocio. */
    public static final String RSP_NO_ENCONTRADO = "NO_ENCONTRADO";

    // ═════════════════════════════════════════════════════════════════════
    // ESCAPE DE CAMPOS DE TEXTO LIBRE
    // ═════════════════════════════════════════════════════════════════════
    /*
     * ─── EL PROBLEMA ────────────────────────────────────────────────────
     * El formato "una linea por mensaje, campos separados por |" funciona
     * mientras los datos sean cortos y controlados. Con el CRUD de
     * audiovisuales entran descripciones y comentarios escritos por
     * personas, y ahi aparecen dos roturas:
     *
     *   1. Un '|' DENTRO de un campo agrega una columna fantasma y
     *      descuadra el split(): los campos siguientes se leen corridos.
     *
     *   2. Un SALTO DE LINEA parte el mensaje en dos. Como el receptor usa
     *      readLine(), leeria media peticion y dejaria la otra mitad en el
     *      buffer, envenenando tambien el mensaje siguiente.
     *
     * No es hipotetico: pasa la primera vez que alguien pega texto copiado
     * de una pagina web.
     *
     * ─── LA SOLUCION ────────────────────────────────────────────────────
     * Sustituir esos dos caracteres por marcadores inofensivos antes de
     * enviar, y restaurarlos al recibir. Es el mismo principio por el que
     * una URL escribe %20 en lugar de un espacio: si un caracter tiene
     * significado estructural, no puede viajar crudo dentro de un dato.
     *
     * ADVERTENCIA: escapar() y desescapar() DEBEN aplicarse en pareja y en
     * orden inverso. Los marcadores se eligieron para que no aparezcan de
     * forma natural en un texto escrito por una persona.
     */
    private static final String MARCA_SEPARADOR = "&sep;";
    private static final String MARCA_SALTO     = "&nl;";

    public static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace(SEPARADOR, MARCA_SEPARADOR)
                    .replace("\r", "")          // CR de Windows: se descarta
                    .replace("\n", MARCA_SALTO);
    }

    public static String desescapar(String texto) {
        if (texto == null) {
            return "";
        }
        // Orden inverso al de escapar().
        return texto.replace(MARCA_SALTO, "\n")
                    .replace(MARCA_SEPARADOR, SEPARADOR);
    }

    /* Constructor privado: clase de constantes, no se instancia. */
    private Protocolo() {
    }
}
