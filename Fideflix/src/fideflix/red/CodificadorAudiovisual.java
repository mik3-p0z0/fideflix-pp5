package fideflix.red;

import fideflix.logica.Audiovisual;
import fideflix.logica.Documental;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;

/*
 * TRADUCTOR entre objetos Audiovisual y lineas del protocolo.
 *
 * ─── POR QUE UNA CLASE APARTE ───────────────────────────────────────
 * Esta conversion la necesitan LOS DOS EXTREMOS: el servidor para
 * responder y el cliente para interpretar. Si cada lado escribiera su
 * propia version, tarde o temprano una de las dos cambiaria sin la otra
 * y aparecerian bugs de campos corridos, que son especialmente molestos
 * porque el programa no falla: simplemente muestra el estudio en la
 * columna del director.
 *
 * Teniendo una sola clase compartida, el formato existe en UN lugar.
 * Es el mismo criterio por el que Protocolo centraliza las constantes.
 *
 * ─── FORMATO DE REGISTRO (11 campos) ────────────────────────────────
 *   id|tipo|titulo|descripcion|anio|imdb|clasificacion|genero|e1|e2|e3
 *
 * Los tres ultimos campos son los ESPECIFICOS de cada subtipo:
 *
 *   PELICULA   -> duracion | director   | estudio
 *   DOCUMENTAL -> duracion | director   | tema
 *   SERIE      -> temporadas | episodios | estado
 *
 * Un unico formato para los tres tipos evita triplicar el enrutador del
 * servidor y el codigo de la tabla en la interfaz. El precio es que los
 * tres ultimos campos cambian de significado segun el tipo: por eso el
 * campo 'tipo' viaja ANTES que ellos, para que el receptor sepa como
 * interpretarlos cuando llegue.
 */
public final class CodificadorAudiovisual {

    /* Cantidad de campos de una linea de registro. Tenerlo como
     * constante permite validar la entrada en vez de confiar en que
     * siempre venga bien formada. */
    public static final int CAMPOS = 11;

    private CodificadorAudiovisual() {
    }

    // ═════════════════════════════════════════════════════════════════
    // OBJETO -> LINEA   (lo usa el servidor al responder)
    // ═════════════════════════════════════════════════════════════════
    /*
     * Registro completo, con id al frente:
     *   id|tipo|titulo|desc|anio|imdb|clasif|genero|e1|e2|e3
     */
    public static String aLinea(Audiovisual av) {
        return av.getId() + Protocolo.SEPARADOR + aCampos(av);
    }

    /*
     * Peticion CREAR_AV lista para enviar.
     * No lleva id porque todavia no existe: lo asigna la base.
     */
    public static String aPeticionCrear(Audiovisual av) {
        return Protocolo.CMD_CREAR_AV + Protocolo.SEPARADOR + aCampos(av);
    }

    /*
     * Peticion ACTUALIZAR lista para enviar. Aqui el id SI viaja, y va
     * antes del tipo: por eso el parser usa offset 2 en este caso y 1 en
     * el de CREAR_AV.
     */
    public static String aPeticionActualizar(Audiovisual av) {
        return Protocolo.CMD_ACTUALIZAR + Protocolo.SEPARADOR + av.getId()
             + Protocolo.SEPARADOR + aCampos(av);
    }

    /*
     * Los 10 campos de datos, SIN id y SIN comando.
     *
     * Es el nucleo compartido por los tres metodos de arriba. Que exista
     * un unico lugar donde se decide el orden de los campos y donde se
     * aplica el escape es justamente el motivo de esta clase: si el
     * cliente armara sus peticiones a mano, tarde o temprano olvidaria
     * escapar un campo y el bug aparecerria solo con ciertos textos.
     */
    public static String aCampos(Audiovisual av) {

        // Los tres campos especificos se resuelven segun la subclase.
        String e1, e2, e3, tipo;

        switch (av) {
            case Pelicula p -> {
                tipo = Protocolo.TIPO_PELICULA;
                e1 = String.valueOf(p.getDuracion());
                e2 = p.getDirector();
                e3 = p.getEstudio();
            }
            case Documental d -> {
                tipo = Protocolo.TIPO_DOCUMENTAL;
                e1 = String.valueOf(d.getDuracion());
                e2 = d.getDirector();
                e3 = d.getTema();
            }
            case Serie s -> {
                tipo = Protocolo.TIPO_SERIE;
                e1 = String.valueOf(s.getNumTemporadas());
                e2 = String.valueOf(s.getNumEpisodios());
                e3 = s.getEstado();
            }
            default -> throw new IllegalArgumentException(
                    "Subtipo no soportado: " + av.getClass().getName());
        }

        // TODO campo de texto pasa por escapar(): titulo, descripcion,
        // director, estudio y tema son texto libre y pueden contener '|'
        // o saltos de linea que romperian el formato del mensaje.
        return String.join(Protocolo.SEPARADOR,
                tipo,
                Protocolo.escapar(av.getTitulo()),
                Protocolo.escapar(av.getDescripcion()),
                String.valueOf(av.getEstreno()),
                String.valueOf(av.getCalificacion_IMDb()),
                Protocolo.escapar(av.getClasificacion()),
                Protocolo.escapar(av.getGenero()),
                Protocolo.escapar(e1),
                Protocolo.escapar(e2),
                Protocolo.escapar(e3));
    }

    // ═════════════════════════════════════════════════════════════════
    // CAMPOS -> OBJETO
    // ═════════════════════════════════════════════════════════════════
    /*
     * Reconstruye el objeto a partir de los campos ya separados.
     *
     * @param c      arreglo de campos.
     * @param offset indice donde empieza el campo 'tipo'. Existe porque
     *               el mismo formato se reutiliza en dos contextos:
     *                 - linea de respuesta:  id|tipo|...   -> offset 1
     *                 - peticion CREAR_AV:   CMD|tipo|...  -> offset 1
     *                 - peticion ACTUALIZAR: CMD|id|tipo|..-> offset 2
     *               Un unico parser para los tres casos en vez de tres
     *               copias que se desincronizan.
     */
    public static Audiovisual desdeCampos(String[] c, int offset) {

        String tipo        = c[offset];
        String titulo      = Protocolo.desescapar(c[offset + 1]);
        String descripcion = Protocolo.desescapar(c[offset + 2]);
        int    anio        = enteroSeguro(c[offset + 3]);
        double imdb        = decimalSeguro(c[offset + 4]);
        String clasif      = Protocolo.desescapar(c[offset + 5]);
        String genero      = Protocolo.desescapar(c[offset + 6]);
        String e1          = Protocolo.desescapar(c[offset + 7]);
        String e2          = Protocolo.desescapar(c[offset + 8]);
        String e3          = Protocolo.desescapar(c[offset + 9]);

        return switch (tipo) {
            case Protocolo.TIPO_PELICULA ->
                    new Pelicula(titulo, descripcion, anio, clasif, imdb, genero,
                                 enteroSeguro(e1), e2, e3);

            case Protocolo.TIPO_DOCUMENTAL ->
                    new Documental(titulo, descripcion, anio, clasif, imdb, genero,
                                   enteroSeguro(e1), e2, e3);

            case Protocolo.TIPO_SERIE ->
                    new Serie(titulo, descripcion, anio, clasif, imdb, genero,
                              enteroSeguro(e1), enteroSeguro(e2), e3);

            default -> throw new IllegalArgumentException(
                    "Tipo de audiovisual desconocido: " + tipo);
        };
    }

    // ═════════════════════════════════════════════════════════════════
    // CONVERSIONES DEFENSIVAS
    // ═════════════════════════════════════════════════════════════════
    /*
     * Integer.parseInt lanza NumberFormatException con texto invalido.
     * Aqui eso seria grave: el dato viene DE LA RED, o sea de un emisor
     * que no controlamos. Cualquiera puede mandar una linea cruda al
     * puerto 5000 con telnet y escribir "abc" donde va un numero.
     *
     * Una excepcion no capturada en el hilo de atencion mataria ese hilo
     * y dejaria al cliente esperando una respuesta que nunca llega. Se
     * prefiere degradar a 0, que el DAO traduce a NULL y las
     * restricciones CHECK de la base terminan de filtrar.
     *
     * REGLA GENERAL: todo dato que cruza un limite de confianza (red,
     * archivo, entrada del usuario) se valida en el lado que lo recibe.
     * Nunca se asume que el emisor se porto bien.
     */
    private static int enteroSeguro(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    private static double decimalSeguro(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0.0;
        }
    }
}
