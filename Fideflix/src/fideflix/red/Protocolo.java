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
 *     OK|...        exito (LOGIN devuelve OK|nombre|fechaRegistro,
 *                          CREAR devuelve OK|totalUsuarios)
 *     DENEGADO      credenciales invalidas en LOGIN
 *     DUPLICADO|em  ya existe un usuario con ese email
 *     ERROR|detalle fallo interno del servidor (ej. error de disco)
 *
 * Esta clase centraliza las constantes para que cliente y servidor nunca
 * se desincronicen (si el comando cambia, cambia en UN solo lugar).
 *
 * ADVERTENCIA DE SEGURIDAD (para la vida real, no para esta practica):
 * - Las contrasenas viajan en texto plano por la red. En produccion esto
 *   exige TLS (SSLSocket / certificados) y almacenar hashes (bcrypt/argon2),
 *   nunca la contrasena literal. Ver OWASP ASVS v4 cap. 2 (autenticacion).
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

    // ─── Comandos (peticiones) ───────────────────────────────────────────
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_CREAR = "CREAR";

    // ─── Codigos de respuesta ────────────────────────────────────────────
    public static final String RSP_OK        = "OK";
    public static final String RSP_DENEGADO  = "DENEGADO";
    public static final String RSP_DUPLICADO = "DUPLICADO";
    public static final String RSP_ERROR     = "ERROR";

    /* Constructor privado: clase de constantes, no se instancia. */
    private Protocolo() {
    }
}
