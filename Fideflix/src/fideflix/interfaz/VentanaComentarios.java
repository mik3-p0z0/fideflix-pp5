package fideflix.interfaz;

import fideflix.logica.Audiovisual;
import fideflix.logica.Usuario;
import fideflix.red.ClienteFideflix;
import fideflix.red.Protocolo;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/*
 * COMENTARIOS DE UNA OBRA.
 *
 * Es la ventana que hace visible la correccion del bug mas interesante
 * de la practica: en la PP4, Audiovisual guardaba los comentarios en un
 * ArrayList<String> declarado 'static'. Al ser estatico, la lista
 * pertenecia a la CLASE y no a cada objeto, de modo que todas las obras
 * compartian los mismos comentarios: uno sobre "Interstellar" aparecia
 * tambien en "Breaking Bad".
 *
 * Ahora cada comentario es una fila de la tabla 'comentario' con dos
 * llaves foraneas que declaran de que obra habla y quien lo escribio.
 * El modelo relacional hace ese error imposible: al obligar a declarar
 * la relacion, expone lo que Java dejaba pasar en silencio.
 *
 * PRUEBA VISUAL: abri esta ventana en dos obras distintas. Cada una
 * muestra solo lo suyo.
 */
public class VentanaComentarios extends JFrame {

    private final Audiovisual obra;
    private final Usuario usuario;

    private final JTextArea areaComentarios = new JTextArea();
    private final JTextArea txtNuevo = new JTextArea(3, 40);
    private final JLabel lblContador = new JLabel(" ");

    public VentanaComentarios(Audiovisual obra, Usuario usuario) {
        super("Comentarios — " + obra.getTitulo());
        this.obra = obra;
        this.usuario = usuario;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(620, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // ── Encabezado ───────────────────────────────────────────────
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setBorder(BorderFactory.createEmptyBorder(12, 15, 6, 15));

        JLabel titulo = new JLabel(obra.getTitulo());
        titulo.setFont(new Font("Arial", Font.BOLD, 18));
        cabecera.add(titulo, BorderLayout.NORTH);
        cabecera.add(lblContador, BorderLayout.SOUTH);
        add(cabecera, BorderLayout.NORTH);

        // ── Lista de comentarios (solo lectura) ──────────────────────
        areaComentarios.setEditable(false);
        areaComentarios.setLineWrap(true);
        areaComentarios.setWrapStyleWord(true);
        areaComentarios.setFont(new Font("Monospaced", Font.PLAIN, 13));
        areaComentarios.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(new JScrollPane(areaComentarios), BorderLayout.CENTER);

        // ── Caja para escribir ───────────────────────────────────────
        JPanel inferior = new JPanel(new BorderLayout(8, 8));
        inferior.setBorder(BorderFactory.createEmptyBorder(6, 15, 12, 15));

        txtNuevo.setLineWrap(true);
        txtNuevo.setWrapStyleWord(true);
        JScrollPane scrollNuevo = new JScrollPane(txtNuevo);
        scrollNuevo.setPreferredSize(new Dimension(500, 70));
        scrollNuevo.setBorder(BorderFactory.createTitledBorder(
                "Comentar como " + usuario.getNombre()));
        inferior.add(scrollNuevo, BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnPublicar = new JButton("Publicar");
        JButton btnRefrescar = new JButton("Actualizar");
        JButton btnCerrar = new JButton("Cerrar");

        btnPublicar.addActionListener(e -> publicar());
        btnRefrescar.addActionListener(e -> cargar());
        btnCerrar.addActionListener(e -> dispose());

        botones.add(btnRefrescar);
        botones.add(btnPublicar);
        botones.add(btnCerrar);
        inferior.add(botones, BorderLayout.SOUTH);

        add(inferior, BorderLayout.SOUTH);

        cargar();
    }

    // ═════════════════════════════════════════════════════════════════

    /*
     * Trae los comentarios DE ESTA OBRA (filtrados por su id en el
     * servidor, no en el cliente).
     *
     * Filtrar en el servidor y no aqui no es un detalle de estilo: si
     * el cliente pidiera todos los comentarios del sistema para quedarse
     * con unos pocos, transferiria datos que no le corresponden y el
     * costo crece con la base entera en lugar de con lo que se muestra.
     */
    public final void cargar() {
        try {
            List<String> respuesta = ClienteFideflix.enviarPeticionMultilinea(
                    Protocolo.CMD_LISTAR_COMENTS + Protocolo.SEPARADOR + obra.getId());

            String[] encabezado = respuesta.get(0)
                    .split(Protocolo.SEPARADOR_REGEX, -1);

            if (!Protocolo.RSP_OK.equals(encabezado[0])) {
                areaComentarios.setText("No se pudieron cargar los comentarios.");
                return;
            }

            int total = Integer.parseInt(encabezado[1]);
            lblContador.setText(total == 0
                    ? "Todavía no hay comentarios. Sé el primero."
                    : total + " comentario(s)");

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < respuesta.size(); i++) {
                // Formato de linea: id|autor|fecha|texto
                String[] c = respuesta.get(i).split(Protocolo.SEPARADOR_REGEX, -1);

                sb.append(Protocolo.desescapar(c[1]))       // autor
                  .append("   ·   ").append(c[2])           // fecha
                  .append('\n')
                  .append(Protocolo.desescapar(c[3]))       // texto
                  .append("\n")
                  .append("─".repeat(60))
                  .append("\n\n");
            }
            areaComentarios.setText(sb.toString());
            areaComentarios.setCaretPosition(0);   // volver arriba

        } catch (IOException ex) {
            sinConexion(ex);
        }
    }

    private void publicar() {
        String texto = txtNuevo.getText().trim();

        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Escriba un comentario antes de publicar.",
                    "Comentario vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            /* escapar() es OBLIGATORIO aqui: un comentario es texto libre
             * y la gente escribe saltos de linea sin pensarlo. Sin
             * escape, un enter partiria el mensaje en dos y el servidor
             * leeria media peticion, envenenando ademas la siguiente. */
            String peticion = Protocolo.CMD_COMENTAR
                    + Protocolo.SEPARADOR + obra.getId()
                    + Protocolo.SEPARADOR + usuario.getId()
                    + Protocolo.SEPARADOR + Protocolo.escapar(texto);

            String respuesta = ClienteFideflix.enviarPeticion(peticion);
            String codigo = respuesta.split(Protocolo.SEPARADOR_REGEX, -1)[0];

            switch (codigo) {
                case Protocolo.RSP_OK -> {
                    txtNuevo.setText("");
                    cargar();
                }
                case Protocolo.RSP_NO_ENCONTRADO ->
                    // Fallo una llave foranea: la obra ya no existe.
                    // Otro cliente la elimino mientras esta ventana
                    // estaba abierta.
                    JOptionPane.showMessageDialog(this,
                            "La obra ya no existe.\n"
                          + "Otro usuario pudo haberla eliminado.",
                            "No disponible", JOptionPane.WARNING_MESSAGE);

                default ->
                    JOptionPane.showMessageDialog(this,
                            "El servidor reportó un error:\n" + respuesta,
                            "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException ex) {
            sinConexion(ex);
        }
    }

    private void sinConexion(IOException ex) {
        JOptionPane.showMessageDialog(this,
                "No se pudo conectar con el servidor.\n\nDetalle: " + ex.getMessage(),
                "Sin conexión", JOptionPane.ERROR_MESSAGE);
    }
}
