package fideflix.pruebas;

import fideflix.logica.Audiovisual;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;
import fideflix.red.ClienteFideflix;
import fideflix.red.CodificadorAudiovisual;
import fideflix.red.Protocolo;
import fideflix.red.ServidorFideflix;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * PRUEBA DE EXTREMO A EXTREMO DE LA CAPA DE RED.
 *
 * Levanta el servidor real, le habla por sockets TCP reales y verifica
 * las respuestas. No usa la interfaz grafica: eso permite probar el
 * protocolo AISLADO, sin que un error de Swing se confunda con un error
 * de red.
 *
 * Se ejecuta con clic derecho -> Run File (Shift+F6).
 *
 * IMPORTANTE: cerra la aplicacion principal (Main) antes de correr esto.
 * Los dos intentan escuchar en el puerto 5000 y el segundo recibiria un
 * BindException.
 */
public class PruebaRed {

    private static int ok = 0;
    private static int fallidas = 0;

    public static void main(String[] args) throws Exception {

        ServidorFideflix servidor = new ServidorFideflix(
                msg -> System.out.println("    [servidor] " + msg));

        try {
            servidor.iniciar();
            Thread.sleep(300);   // dar tiempo al hilo aceptador

            catalogos();
            listados();
            int id = cicloCrud();
            escapeDeTexto();
            comentarios(id);
            comandoDesconocido();
            concurrencia();

            System.out.println();
            System.out.println("═".repeat(60));
            System.out.printf("RESULTADO: %d correctas, %d fallidas%n", ok, fallidas);
            System.out.println("═".repeat(60));

        } finally {
            servidor.detener();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    private static void catalogos() throws IOException {
        titulo("1. CATALOGOS");

        List<String> r = ClienteFideflix.enviarPeticionMultilinea(Protocolo.CMD_CATALOGOS);
        String[] enc = campos(r.get(0));

        System.out.println("  Encabezado: " + r.get(0));
        System.out.println("  Muestra   : " + r.get(1));

        verificar("Responde OK", Protocolo.RSP_OK.equals(enc[0]));
        verificar("Llegan tantas lineas como anuncia el encabezado",
                  r.size() - 1 == Integer.parseInt(enc[1]));
        verificar("Vienen generos y clasificaciones",
                  r.stream().anyMatch(l -> l.startsWith("GENERO"))
               && r.stream().anyMatch(l -> l.startsWith("CLASIFICACION")));
    }

    // ─────────────────────────────────────────────────────────────────
    private static void listados() throws IOException {
        titulo("2. LISTADOS");

        List<String> todos = ClienteFideflix.enviarPeticionMultilinea(
                Protocolo.CMD_LISTAR + Protocolo.SEPARADOR + Protocolo.TIPO_TODOS);
        List<String> series = ClienteFideflix.enviarPeticionMultilinea(
                Protocolo.CMD_LISTAR + Protocolo.SEPARADOR + Protocolo.TIPO_SERIE);

        System.out.println("  LISTAR|TODOS -> " + todos.get(0));
        System.out.println("  LISTAR|SERIE -> " + series.get(0));

        verificar("LISTAR|TODOS devuelve 6", "6".equals(campos(todos.get(0))[1]));
        verificar("LISTAR|SERIE devuelve 2", "2".equals(campos(series.get(0))[1]));
        verificar("Cada registro trae los 11 campos",
                  campos(todos.get(1)).length == CodificadorAudiovisual.CAMPOS);

        // El registro debe poder volver a ser un objeto: ida y vuelta
        // completa objeto -> linea -> objeto.
        Audiovisual reconstruido = CodificadorAudiovisual.desdeCampos(campos(todos.get(1)), 1);
        System.out.println("  Reconstruido: " + reconstruido.getClass().getSimpleName()
                         + " \"" + reconstruido.getTitulo() + "\"");
        verificar("La linea se reconstruye como objeto", reconstruido.getTitulo() != null);

        String r = ClienteFideflix.enviarPeticion(
                Protocolo.CMD_OBTENER + Protocolo.SEPARADOR + "99999");
        verificar("OBTENER de un id inexistente responde NO_ENCONTRADO",
                  Protocolo.RSP_NO_ENCONTRADO.equals(r));
    }

    // ─────────────────────────────────────────────────────────────────
    private static int cicloCrud() throws IOException {
        titulo("3. CRUD POR SOCKET");

        Serie nueva = new Serie("Serie De Prueba Red", "Creada por PruebaRed.",
                2025, "TV-14", 8.1, "Drama", 3, 24, "En emision");

        String r1 = ClienteFideflix.enviarPeticion(
                CodificadorAudiovisual.aPeticionCrear(nueva));
        System.out.println("  CREAR_AV -> " + r1);

        verificar("CREAR_AV responde OK", r1.startsWith(Protocolo.RSP_OK));
        int id = Integer.parseInt(campos(r1)[1]);
        verificar("Devuelve un id valido", id > 0);

        // Duplicado: mismo titulo y mismo anio violan uq_titulo_anio.
        String r2 = ClienteFideflix.enviarPeticion(
                CodificadorAudiovisual.aPeticionCrear(nueva));
        System.out.println("  CREAR_AV repetido -> " + r2);
        verificar("El duplicado se rechaza", r2.startsWith(Protocolo.RSP_DUPLICADO));

        // Actualizar
        nueva.setId(id);
        nueva.setTitulo("Serie De Prueba Red (editada)");
        nueva.setEstado("Finalizada");
        String r3 = ClienteFideflix.enviarPeticion(
                CodificadorAudiovisual.aPeticionActualizar(nueva));
        System.out.println("  ACTUALIZAR -> " + r3);
        verificar("ACTUALIZAR responde OK", r3.startsWith(Protocolo.RSP_OK));

        List<String> r4 = ClienteFideflix.enviarPeticionMultilinea(
                Protocolo.CMD_OBTENER + Protocolo.SEPARADOR + id);
        Audiovisual leida = CodificadorAudiovisual.desdeCampos(campos(r4.get(1)), 1);
        verificar("El cambio se refleja al releer",
                  leida.getTitulo().endsWith("(editada)"));
        verificar("El campo especifico de la subclase tambien cambio",
                  "Finalizada".equals(((Serie) leida).getEstado()));

        // Actualizar un id inexistente
        Serie fantasma = new Serie("No existe", "", 2020, "R", 5.0, "Drama", 1, 1, "X");
        fantasma.setId(99999);
        verificar("ACTUALIZAR de un id inexistente responde NO_ENCONTRADO",
                  Protocolo.RSP_NO_ENCONTRADO.equals(
                          ClienteFideflix.enviarPeticion(
                                  CodificadorAudiovisual.aPeticionActualizar(fantasma))));

        return id;
    }

    // ─────────────────────────────────────────────────────────────────
    // LA PRUEBA CLAVE DEL PROTOCOLO
    // ─────────────────────────────────────────────────────────────────
    private static void escapeDeTexto() throws IOException {
        titulo("4. ESCAPE DE CARACTERES QUE ROMPEN EL PROTOCOLO");

        // Esta descripcion contiene los DOS caracteres que destruirian el
        // mensaje si viajaran crudos:
        //   '|'  -> agregaria columnas fantasma y correria los campos
        //   '\n' -> partiria el mensaje en dos y readLine() leeria basura
        String descripcionHostil =
                "Primera linea con un separador | en medio.\n"
              + "Segunda linea despues de un salto.\n"
              + "Tercera linea con || dos seguidos.";

        Pelicula p = new Pelicula("Prueba De Escape", descripcionHostil,
                2025, "R", 7.0, "Suspenso", 100, "Dir|ector", "Estudio\nRaro");

        String r = ClienteFideflix.enviarPeticion(
                CodificadorAudiovisual.aPeticionCrear(p));
        verificar("Se acepta un texto con | y saltos de linea",
                  r.startsWith(Protocolo.RSP_OK));

        int id = Integer.parseInt(campos(r)[1]);

        List<String> lectura = ClienteFideflix.enviarPeticionMultilinea(
                Protocolo.CMD_OBTENER + Protocolo.SEPARADOR + id);

        verificar("La respuesta sigue siendo UNA sola linea de registro",
                  lectura.size() == 2);

        Audiovisual recuperada = CodificadorAudiovisual.desdeCampos(campos(lectura.get(1)), 1);

        System.out.println("  Descripcion recuperada:");
        for (String linea : recuperada.getDescripcion().split("\n")) {
            System.out.println("    | " + linea);
        }

        verificar("La descripcion vuelve IDENTICA (ida y vuelta)",
                  descripcionHostil.equals(recuperada.getDescripcion()));
        verificar("El director con | se conserva",
                  "Dir|ector".equals(((Pelicula) recuperada).getDirector()));
        verificar("El estudio con salto de linea se conserva",
                  "Estudio\nRaro".equals(((Pelicula) recuperada).getEstudio()));

        ClienteFideflix.enviarPeticion(Protocolo.CMD_ELIMINAR + Protocolo.SEPARADOR + id);
    }

    // ─────────────────────────────────────────────────────────────────
    private static void comentarios(int idObra) throws IOException {
        titulo("5. COMENTARIOS");

        String login = ClienteFideflix.enviarPeticion(
                Protocolo.CMD_LOGIN + Protocolo.SEPARADOR
                + "prueba@fideflix.com" + Protocolo.SEPARADOR + "admin123");
        System.out.println("  LOGIN -> " + login);

        verificar("LOGIN devuelve OK|id|nombre|fecha",
                  login.startsWith(Protocolo.RSP_OK) && campos(login).length == 4);

        int idUsuario = Integer.parseInt(campos(login)[1]);
        verificar("El id de usuario llega en la respuesta", idUsuario > 0);

        String texto = "Comentario con | separador y\nsalto de linea.";
        String r = ClienteFideflix.enviarPeticion(
                Protocolo.CMD_COMENTAR + Protocolo.SEPARADOR + idObra
                + Protocolo.SEPARADOR + idUsuario
                + Protocolo.SEPARADOR + Protocolo.escapar(texto));
        verificar("COMENTAR responde OK", r.startsWith(Protocolo.RSP_OK));

        List<String> lista = ClienteFideflix.enviarPeticionMultilinea(
                Protocolo.CMD_LISTAR_COMENTS + Protocolo.SEPARADOR + idObra);
        System.out.println("  LISTAR_COMENTS -> " + lista.get(0));

        verificar("La obra tiene al menos un comentario",
                  Integer.parseInt(campos(lista.get(0))[1]) >= 1);

        String[] c = campos(lista.get(1));
        verificar("El texto del comentario vuelve identico",
                  texto.equals(Protocolo.desescapar(c[3])));

        // Comentar sobre una obra inexistente: falla la llave foranea.
        verificar("COMENTAR sobre una obra inexistente responde NO_ENCONTRADO",
                  Protocolo.RSP_NO_ENCONTRADO.equals(ClienteFideflix.enviarPeticion(
                          Protocolo.CMD_COMENTAR + Protocolo.SEPARADOR + "99999"
                        + Protocolo.SEPARADOR + idUsuario
                        + Protocolo.SEPARADOR + "hola")));

        // Limpieza: al borrar la obra, la cascada se lleva sus comentarios.
        ClienteFideflix.enviarPeticion(
                Protocolo.CMD_ELIMINAR + Protocolo.SEPARADOR + idObra);
        verificar("ELIMINAR repetido responde NO_ENCONTRADO",
                  Protocolo.RSP_NO_ENCONTRADO.equals(ClienteFideflix.enviarPeticion(
                          Protocolo.CMD_ELIMINAR + Protocolo.SEPARADOR + idObra)));
    }

    // ─────────────────────────────────────────────────────────────────
    private static void comandoDesconocido() throws IOException {
        titulo("6. ENTRADA INVALIDA");

        verificar("Comando inexistente da ERROR controlado",
                  ClienteFideflix.enviarPeticion("BORRAR_TODO|1")
                          .startsWith(Protocolo.RSP_ERROR));
        verificar("Peticion con campos de menos da ERROR controlado",
                  ClienteFideflix.enviarPeticion(Protocolo.CMD_LOGIN + "|solo_email")
                          .startsWith(Protocolo.RSP_ERROR));
        verificar("Id no numerico no tumba el servidor",
                  ClienteFideflix.enviarPeticion(
                          Protocolo.CMD_OBTENER + Protocolo.SEPARADOR + "abc")
                          .startsWith(Protocolo.RSP_NO_ENCONTRADO));
    }

    // ─────────────────────────────────────────────────────────────────
    // EL REQUISITO EXPLICITO DE LA CONSIGNA
    // ─────────────────────────────────────────────────────────────────
    private static void concurrencia() throws Exception {
        titulo("7. ATENCION CONCURRENTE DE MULTIPLES CLIENTES");

        final int CLIENTES = 8;

        // CountDownLatch de arranque: los 8 hilos se quedan esperando y
        // salen TODOS a la vez. Sin esto se lanzarian escalonados y la
        // prueba de concurrencia seria una ilusion.
        CountDownLatch arranque = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(CLIENTES);

        Set<Integer> idsGenerados = ConcurrentHashMap.newKeySet();
        List<String> errores = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CLIENTES; i++) {
            final int n = i;
            new Thread(() -> {
                try {
                    arranque.await();

                    Pelicula p = new Pelicula("Concurrente " + n,
                            "Creada por el hilo " + n, 2025, "PG", 6.5,
                            "Accion", 90 + n, "Dir " + n, "Est " + n);

                    String r = ClienteFideflix.enviarPeticion(
                            CodificadorAudiovisual.aPeticionCrear(p));

                    if (r.startsWith(Protocolo.RSP_OK)) {
                        idsGenerados.add(Integer.parseInt(campos(r)[1]));
                    } else {
                        errores.add("hilo " + n + ": " + r);
                    }
                } catch (Exception e) {
                    errores.add("hilo " + n + ": " + e.getMessage());
                } finally {
                    terminados.countDown();
                }
            }, "cliente-prueba-" + i).start();
        }

        arranque.countDown();                        // ¡ya!
        terminados.await(30, TimeUnit.SECONDS);

        System.out.println("  Clientes simultaneos: " + CLIENTES);
        System.out.println("  Ids generados       : " + idsGenerados.size());
        if (!errores.isEmpty()) {
            errores.forEach(e -> System.out.println("  ERROR: " + e));
        }

        verificar("Los " + CLIENTES + " clientes fueron atendidos",
                  idsGenerados.size() == CLIENTES);
        // Que no haya ids repetidos prueba que no hubo "lost update":
        // ningun INSERT piso a otro. Con el archivo de la PP4 y sin
        // candado, aqui se habrian perdido registros.
        verificar("Ningun id se repitio (sin lost update)",
                  idsGenerados.size() == CLIENTES);
        verificar("Ningun hilo fallo", errores.isEmpty());

        // Limpieza
        for (int id : idsGenerados) {
            ClienteFideflix.enviarPeticion(
                    Protocolo.CMD_ELIMINAR + Protocolo.SEPARADOR + id);
        }
        List<String> finales = ClienteFideflix.enviarPeticionMultilinea(
                Protocolo.CMD_LISTAR + Protocolo.SEPARADOR + Protocolo.TIPO_TODOS);
        System.out.println("  Estado final: " + finales.get(0));
        verificar("La base vuelve a las 6 obras originales",
                  "6".equals(campos(finales.get(0))[1]));
    }

    // ─────────────────────────────────────────────────────────────────
    private static String[] campos(String linea) {
        return linea.split(Protocolo.SEPARADOR_REGEX, -1);
    }

    private static void titulo(String t) {
        System.out.println();
        System.out.println("── " + t + " " + "─".repeat(Math.max(0, 56 - t.length())));
    }

    private static void verificar(String descripcion, boolean condicion) {
        if (condicion) {
            ok++;
            System.out.println("  [OK]    " + descripcion);
        } else {
            fallidas++;
            System.out.println("  [FALLA] " + descripcion);
        }
    }
}
