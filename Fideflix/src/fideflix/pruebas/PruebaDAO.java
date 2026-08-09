package fideflix.pruebas;

import fideflix.logica.Audiovisual;
import fideflix.logica.Comentario;
import fideflix.logica.Documental;
import fideflix.logica.ItemCatalogo;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;
import fideflix.logica.Usuario;
import fideflix.persistencia.AudiovisualDAO;
import fideflix.persistencia.CatalogoDAO;
import fideflix.persistencia.ComentarioDAO;
import fideflix.persistencia.UsuarioDAO;
import java.sql.SQLException;
import java.util.List;

/*
 * BANCO DE PRUEBAS DE LA CAPA DE PERSISTENCIA.
 *
 * Se ejecuta con clic derecho -> Run File (Shift+F6).
 * No forma parte del flujo de la aplicacion.
 *
 * Por que existe: valida los DAO ANTES de conectarlos al servidor y a
 * las ventanas. Si algo falla despues, ya sabras que no es la capa de
 * datos. Depurar es reducir sospechosos, y estas pruebas eliminan a la
 * mitad de la lista.
 *
 * IMPORTANTE: la prueba deja la base como la encontro (borra lo que
 * inserta). Una prueba que ensucia el estado obliga a resetear la base
 * a mano cada vez, y una prueba incomoda de correr termina no
 * corriendose.
 */
public class PruebaDAO {

    private static int pruebasOk = 0;
    private static int pruebasFallidas = 0;

    public static void main(String[] args) {
        try {
            catalogos();
            lectura();
            cicloCompleto();
            transaccion();
            inyeccionSQL();
            comentarios();

            System.out.println();
            System.out.println("═".repeat(60));
            System.out.printf("RESULTADO: %d correctas, %d fallidas%n",
                              pruebasOk, pruebasFallidas);
            System.out.println("═".repeat(60));

        } catch (SQLException e) {
            System.err.println("ERROR NO CONTROLADO: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. CATALOGOS
    // ─────────────────────────────────────────────────────────────────
    private static void catalogos() throws SQLException {
        titulo("1. CATALOGOS");

        List<ItemCatalogo> generos = CatalogoDAO.listarGeneros();
        List<ItemCatalogo> clasifs = CatalogoDAO.listarClasificaciones();

        System.out.println("  Generos        : " + generos.size());
        System.out.println("  Clasificaciones: " + clasifs.size());
        System.out.println("  Muestra        : " + generos.get(0)
                         + " (id=" + generos.get(0).id() + ")");

        verificar("Hay generos cargados", generos.size() >= 10);
        verificar("Hay clasificaciones cargadas", clasifs.size() >= 7);
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. LECTURA Y MAPEO DE HERENCIA
    // ─────────────────────────────────────────────────────────────────
    private static void lectura() throws SQLException {
        titulo("2. LECTURA Y MAPEO DE HERENCIA");

        List<Audiovisual> todos = AudiovisualDAO.listar(null);
        System.out.println("  Total de obras: " + todos.size());
        for (Audiovisual a : todos) {
            System.out.printf("    #%-3d %-14s %s%n",
                    a.getId(), a.getClass().getSimpleName(), a.getTitulo());
        }

        verificar("listar(null) devuelve 6 obras", todos.size() == 6);
        verificar("listar(\"SERIE\") devuelve 2", AudiovisualDAO.listar("SERIE").size() == 2);
        verificar("listar(\"PELICULA\") devuelve 2", AudiovisualDAO.listar("PELICULA").size() == 2);
        verificar("listar(\"DOCUMENTAL\") devuelve 2", AudiovisualDAO.listar("DOCUMENTAL").size() == 2);

        // El mapeo debe producir la SUBCLASE correcta, no un Audiovisual
        // generico. Esto es lo que valida el switch sobre 'tipo'.
        long peliculas   = todos.stream().filter(a -> a instanceof Pelicula).count();
        long series      = todos.stream().filter(a -> a instanceof Serie).count();
        long documentales= todos.stream().filter(a -> a instanceof Documental).count();
        verificar("Se instancian las subclases correctas",
                  peliculas == 2 && series == 2 && documentales == 2);

        // Los campos especificos de la subclase deben venir poblados.
        Serie s = (Serie) todos.stream()
                .filter(a -> a instanceof Serie).findFirst().orElseThrow();
        System.out.println("  Serie de muestra: " + s.getTitulo()
                         + " - " + s.getNumTemporadas() + " temporadas, "
                         + s.getNumEpisodios() + " episodios, " + s.getEstado());
        verificar("La serie trae sus campos especificos", s.getNumTemporadas() > 0);

        verificar("obtener(99999) devuelve null", AudiovisualDAO.obtener(99999) == null);
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. CICLO CRUD COMPLETO
    // ─────────────────────────────────────────────────────────────────
    private static void cicloCompleto() throws SQLException {
        titulo("3. CICLO CRUD COMPLETO");

        // CREATE
        Pelicula nueva = new Pelicula("Pelicula De Prueba PP5",
                "Insertada por PruebaDAO para validar la transaccion.",
                2025, "PG-13", 7.5, "Accion",
                120, "Director Prueba", "Estudio Prueba");

        AudiovisualDAO.insertar(nueva);
        int id = nueva.getId();
        System.out.println("  CREATE -> id generado: " + id);
        verificar("insertar() asigna un id", id > 0);

        // READ
        Audiovisual leida = AudiovisualDAO.obtener(id);
        verificar("obtener() la recupera", leida != null);
        verificar("Se recupera como Pelicula", leida instanceof Pelicula);
        verificar("El estudio se guardo en la tabla hija",
                  "Estudio Prueba".equals(((Pelicula) leida).getEstudio()));

        // UPDATE
        leida.setTitulo("Pelicula De Prueba PP5 (editada)");
        ((Pelicula) leida).setEstudio("Estudio Editado");
        boolean actualizada = AudiovisualDAO.actualizar(leida);
        verificar("actualizar() devuelve true", actualizada);

        Audiovisual releida = AudiovisualDAO.obtener(id);
        verificar("El titulo cambio en la base",
                  releida.getTitulo().endsWith("(editada)"));
        verificar("El campo de la tabla hija tambien cambio",
                  "Estudio Editado".equals(((Pelicula) releida).getEstudio()));

        // UPDATE de un id inexistente
        Pelicula fantasma = new Pelicula("No existe", "", 2020, "R", 5.0, "Drama",
                                          90, "X", "Y");
        fantasma.setId(99999);
        verificar("actualizar() con id inexistente devuelve false",
                  !AudiovisualDAO.actualizar(fantasma));

        // DELETE
        verificar("eliminar() devuelve true", AudiovisualDAO.eliminar(id));
        verificar("Ya no se encuentra", AudiovisualDAO.obtener(id) == null);
        verificar("eliminar() de un id inexistente devuelve false",
                  !AudiovisualDAO.eliminar(99999));
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. TRANSACCION  (la prueba que no se puede saltar)
    // ─────────────────────────────────────────────────────────────────
    private static void transaccion() throws SQLException {
        titulo("4. ATOMICIDAD DE LA TRANSACCION");

        int antes = AudiovisualDAO.listar(null).size();

        // duracion_min es SMALLINT UNSIGNED: su maximo es 65535.
        // 99999 lo desborda y hace fallar el SEGUNDO INSERT, el de la
        // tabla hija. El primero (la tabla base) ya se ejecuto.
        //
        // SIN transaccion quedaria una fila huerfana en 'audiovisual'.
        // CON transaccion, el rollback la borra y el conteo no cambia.
        Pelicula rota = new Pelicula("Pelicula Que Debe Fallar",
                "El INSERT de la tabla hija va a reventar a proposito.",
                2025, "R", 6.0, "Terror",
                99999, "Director", "Estudio");

        boolean fallo = false;
        try {
            AudiovisualDAO.insertar(rota);
        } catch (SQLException e) {
            fallo = true;
            System.out.println("  Fallo esperado: " + e.getMessage());
        }

        int despues = AudiovisualDAO.listar(null).size();
        System.out.println("  Obras antes: " + antes + " | despues: " + despues);

        verificar("El insert fallo como se esperaba", fallo);
        verificar("ROLLBACK: no quedo fila huerfana en audiovisual", antes == despues);
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. INYECCION SQL
    // ─────────────────────────────────────────────────────────────────
    private static void inyeccionSQL() throws SQLException {
        titulo("5. RESISTENCIA A INYECCION SQL");

        // Carga clasica: cierra la cadena, cierra el parentesis, mete
        // una sentencia destructiva y comenta el resto de la consulta.
        // Con concatenacion de texto, esto borraria la tabla.
        // Con PreparedStatement es un titulo con caracteres raros.
        String cargaMaliciosa = "Prueba'); DROP TABLE comentario; --";

        Documental doc = new Documental(cargaMaliciosa,
                "Prueba de inyeccion. Debe guardarse como texto literal.",
                2025, "TV-14", 8.0, "Historia",
                60, "Director", "Seguridad");

        AudiovisualDAO.insertar(doc);
        Audiovisual recuperado = AudiovisualDAO.obtener(doc.getId());

        System.out.println("  Guardado como: " + recuperado.getTitulo());

        verificar("El payload se guardo como texto literal",
                  cargaMaliciosa.equals(recuperado.getTitulo()));

        // Si la tabla 'comentario' hubiera sido eliminada, esta llamada
        // lanzaria una SQLException en vez de devolver una lista.
        boolean tablaViva = true;
        try {
            ComentarioDAO.listarPorAudiovisual(1);
        } catch (SQLException e) {
            tablaViva = false;
        }
        verificar("La tabla 'comentario' sigue existiendo", tablaViva);

        AudiovisualDAO.eliminar(doc.getId());   // limpieza
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. COMENTARIOS Y USUARIOS
    // ─────────────────────────────────────────────────────────────────
    private static void comentarios() throws SQLException {
        titulo("6. USUARIOS Y COMENTARIOS");

        // El usuario de prueba lo creo 02_datos_iniciales.sql con la
        // contrasena literal 'admin123'.
        Usuario u = UsuarioDAO.autenticar("prueba@fideflix.com", "admin123");
        verificar("autenticar() con credenciales validas", u != null);

        if (u == null) {
            System.out.println("  (se omite el resto: no hay usuario de prueba)");
            return;
        }
        System.out.println("  Autenticado: " + u.getNombre() + " (id=" + u.getId() + ")");

        verificar("autenticar() con clave incorrecta devuelve null",
                  UsuarioDAO.autenticar("prueba@fideflix.com", "incorrecta") == null);
        verificar("autenticar() con email inexistente devuelve null",
                  UsuarioDAO.autenticar("nadie@fideflix.com", "admin123") == null);

        // Comentario sobre una obra concreta: la correccion del bug de
        // la lista static compartida.
        Audiovisual obra = AudiovisualDAO.listar(null).get(0);
        int idComentario = ComentarioDAO.insertar(obra.getId(), u.getId(),
                "Comentario de prueba generado por PruebaDAO.");
        verificar("insertar() comentario devuelve id", idComentario > 0);

        List<Comentario> deEsaObra = ComentarioDAO.listarPorAudiovisual(obra.getId());
        System.out.println("  Comentarios de \"" + obra.getTitulo() + "\": " + deEsaObra.size());
        for (Comentario c : deEsaObra) {
            System.out.println("    " + c);
        }
        verificar("El comentario aparece en SU obra", !deEsaObra.isEmpty());

        // La prueba de que el bug murio: otra obra NO debe verlo.
        Audiovisual otra = AudiovisualDAO.listar(null).stream()
                .filter(a -> a.getId() != obra.getId())
                .findFirst().orElseThrow();
        boolean contaminada = ComentarioDAO.listarPorAudiovisual(otra.getId())
                .stream().anyMatch(c -> c.id() == idComentario);
        verificar("El comentario NO aparece en otra obra (bug del static corregido)",
                  !contaminada);

        ComentarioDAO.eliminar(idComentario);   // limpieza
    }

    // ─────────────────────────────────────────────────────────────────
    // Utilidades de reporte
    // ─────────────────────────────────────────────────────────────────
    private static void titulo(String t) {
        System.out.println();
        System.out.println("── " + t + " " + "─".repeat(Math.max(0, 56 - t.length())));
    }

    private static void verificar(String descripcion, boolean condicion) {
        if (condicion) {
            pruebasOk++;
            System.out.println("  [OK]    " + descripcion);
        } else {
            pruebasFallidas++;
            System.out.println("  [FALLA] " + descripcion);
        }
    }
}
