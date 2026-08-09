package fideflix.interfaz;

import fideflix.logica.Audiovisual;
import fideflix.logica.Documental;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;
import fideflix.logica.Usuario;
import fideflix.red.ClienteFideflix;
import fideflix.red.CodificadorAudiovisual;
import fideflix.red.Protocolo;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/*
 * CATALOGO DE CONTENIDO: la vista principal del CRUD.
 *
 * Responsabilidades:
 *   - Listar las obras que devuelve el servidor.
 *   - Filtrar por tipo.
 *   - Lanzar las operaciones de alta, edicion, borrado y comentarios.
 *
 * ─── LO QUE ESTA VENTANA NO HACE ────────────────────────────────────
 * No abre conexiones a MySQL, no arma consultas SQL y no sabe que
 * existe una base de datos. Solo habla el protocolo con el servidor.
 * Esa separacion es la que permite que manana el servidor cambie de
 * MySQL a PostgreSQL sin tocar una linea de esta clase.
 *
 * ─── POR QUE ESTA ESCRITA A MANO Y NO CON EL DISENADOR ──────────────
 * Los archivos .form son XML generado, y cuando dos personas editan la
 * misma ventana el merge es practicamente irreconciliable. En un
 * proyecto grupal versionado, una ventana escrita a mano se fusiona;
 * una generada, no.
 */
public class VentanaCatalogo extends JFrame {

    /* Usuario autenticado: se necesita para atribuir los comentarios
     * (la tabla 'comentario' exige un usuario_id). */
    private final Usuario usuario;

    private final DefaultTableModel modelo;
    private final JTable tabla;
    private final JComboBox<String> cboFiltro;
    private final JLabel lblEstado;

    /*
     * Lista paralela a las filas de la tabla.
     *
     * Por que existe: el JTable guarda texto para mostrar, no objetos.
     * Cuando el usuario selecciona la fila 3 y pulsa Editar, hace falta
     * el OBJETO de esa fila, con su id y todos sus campos. Mantener esta
     * lista alineada con el modelo evita tener que reconsultar al
     * servidor solo para saber que selecciono.
     *
     * INVARIANTE: obras.get(i) corresponde SIEMPRE a la fila i. Todo
     * codigo que toque el modelo debe tocar tambien esta lista.
     */
    private final List<Audiovisual> obras = new ArrayList<>();

    private static final String[] COLUMNAS = {
        "ID", "Tipo", "Título", "Año", "IMDb", "Clasificación", "Género", "Detalle"
    };

    public VentanaCatalogo(Usuario usuario) {
        super("Fideflix - Catálogo  [" + usuario.getNombre() + "]");
        this.usuario = usuario;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // ── Barra superior: filtro ───────────────────────────────────
        JPanel superior = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        superior.add(new JLabel("Mostrar:"));

        cboFiltro = new JComboBox<>(new String[]{
            Protocolo.TIPO_TODOS, Protocolo.TIPO_PELICULA,
            Protocolo.TIPO_DOCUMENTAL, Protocolo.TIPO_SERIE
        });
        cboFiltro.addActionListener(e -> cargar());
        superior.add(cboFiltro);

        JButton btnRefrescar = new JButton("Actualizar");
        btnRefrescar.addActionListener(e -> cargar());
        superior.add(btnRefrescar);

        add(superior, BorderLayout.NORTH);

        // ── Tabla ────────────────────────────────────────────────────
        /* Se sobreescribe isCellEditable para devolver false: la tabla
         * es de SOLO LECTURA. Editar in-place parece comodo pero deja al
         * usuario cambiar datos sin validacion y sin enviar nada al
         * servidor; el resultado seria una pantalla que miente sobre el
         * estado real de la base. La edicion pasa por el formulario. */
        modelo = new DefaultTableModel(COLUMNAS, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(24);
        tabla.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tabla.getColumnModel().getColumn(0).setPreferredWidth(40);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(220);
        tabla.getColumnModel().getColumn(7).setPreferredWidth(260);

        add(new JScrollPane(tabla), BorderLayout.CENTER);

        // ── Barra inferior: acciones ─────────────────────────────────
        JPanel inferior = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton btnNuevo       = new JButton("Nuevo");
        JButton btnEditar      = new JButton("Editar");
        JButton btnEliminar    = new JButton("Eliminar");
        JButton btnComentarios = new JButton("Comentarios");
        JButton btnCerrar      = new JButton("Cerrar");

        btnNuevo.addActionListener(e -> abrirFormulario(null));
        btnEditar.addActionListener(e -> editarSeleccionado());
        btnEliminar.addActionListener(e -> eliminarSeleccionado());
        btnComentarios.addActionListener(e -> abrirComentarios());
        btnCerrar.addActionListener(e -> dispose());

        inferior.add(btnNuevo);
        inferior.add(btnEditar);
        inferior.add(btnEliminar);
        inferior.add(btnComentarios);
        inferior.add(btnCerrar);

        lblEstado = new JLabel(" ");
        lblEstado.setFont(new Font("Arial", Font.PLAIN, 12));

        JPanel panelSur = new JPanel(new BorderLayout());
        panelSur.add(inferior, BorderLayout.CENTER);
        panelSur.add(lblEstado, BorderLayout.SOUTH);
        add(panelSur, BorderLayout.SOUTH);

        cargar();
    }

    // ═════════════════════════════════════════════════════════════════
    // CARGA DE DATOS
    // ═════════════════════════════════════════════════════════════════

    /*
     * Pide el catalogo al servidor y repuebla la tabla.
     *
     * NOTA SOBRE EL HILO: esta llamada de red ocurre en el EDT (el hilo
     * de eventos de Swing). Con datos locales y pocas decenas de filas
     * es imperceptible. Si el servidor estuviera en otra maquina o el
     * catalogo creciera, la ventana se congelaria durante la espera y la
     * herramienta correcta seria SwingWorker: doInBackground() consulta,
     * done() actualiza la tabla. Limitacion consciente, declarada en el
     * README.
     */
    public final void cargar() {
        String tipo = String.valueOf(cboFiltro.getSelectedItem());
        try {
            List<String> respuesta = ClienteFideflix.enviarPeticionMultilinea(
                    Protocolo.CMD_LISTAR + Protocolo.SEPARADOR + tipo);

            String[] encabezado = respuesta.get(0)
                    .split(Protocolo.SEPARADOR_REGEX, -1);

            if (!Protocolo.RSP_OK.equals(encabezado[0])) {
                error("El servidor respondió: " + respuesta.get(0));
                return;
            }

            modelo.setRowCount(0);
            obras.clear();

            // Se recorre desde 1: el indice 0 es el encabezado OK|n.
            for (int i = 1; i < respuesta.size(); i++) {
                String[] campos = respuesta.get(i)
                        .split(Protocolo.SEPARADOR_REGEX, -1);

                Audiovisual av = CodificadorAudiovisual.desdeCampos(campos, 1);
                av.setId(Integer.parseInt(campos[0]));

                obras.add(av);
                modelo.addRow(new Object[]{
                    av.getId(),
                    campos[1],                       // tipo
                    av.getTitulo(),
                    av.getEstreno() > 0 ? av.getEstreno() : "",
                    av.getCalificacion_IMDb(),
                    av.getClasificacion(),
                    av.getGenero(),
                    detalle(av)
                });
            }
            lblEstado.setText("  " + obras.size() + " obra(s) — filtro: " + tipo);

        } catch (IOException ex) {
            sinConexion(ex);
        }
    }

    /* Resume en una columna los campos propios de cada subtipo. Evita
     * tener tres columnas casi siempre vacias. */
    private String detalle(Audiovisual av) {
        return switch (av) {
            case Pelicula p   -> p.getDuracion() + " min · " + p.getDirector()
                                 + " · " + p.getEstudio();
            case Documental d -> d.getDuracion() + " min · " + d.getDirector()
                                 + " · " + d.getTema();
            case Serie s      -> s.getNumTemporadas() + " temp · "
                                 + s.getNumEpisodios() + " eps · " + s.getEstado();
            default -> "";
        };
    }

    // ═════════════════════════════════════════════════════════════════
    // ACCIONES
    // ═════════════════════════════════════════════════════════════════

    /*
     * @param existente null para crear, o la obra a editar.
     * Se le pasa 'this::cargar' como callback: cuando el formulario
     * guarda con exito, refresca esta tabla. Asi el formulario no
     * necesita conocer a VentanaCatalogo, solo "algo que se ejecuta al
     * terminar" (bajo acoplamiento, mismo criterio que el Consumer de
     * bitacora del servidor).
     */
    private void abrirFormulario(Audiovisual existente) {
        new VentanaAudiovisualForm(existente, this::cargar).setVisible(true);
    }

    private void editarSeleccionado() {
        Audiovisual av = seleccionada();
        if (av != null) {
            abrirFormulario(av);
        }
    }

    private void abrirComentarios() {
        Audiovisual av = seleccionada();
        if (av != null) {
            new VentanaComentarios(av, usuario).setVisible(true);
        }
    }

    private void eliminarSeleccionado() {
        Audiovisual av = seleccionada();
        if (av == null) {
            return;
        }

        /* Confirmacion antes de una operacion destructiva, y se avisa
         * del efecto en cascada. No es cortesia: es consentimiento
         * informado. El usuario no tiene por que saber que existe un
         * ON DELETE CASCADE, pero si tiene derecho a saber que va a
         * perder los comentarios. */
        int r = JOptionPane.showConfirmDialog(this,
                "Se eliminará \"" + av.getTitulo() + "\"\n"
              + "y todos sus comentarios asociados.\n\n"
              + "Esta acción no se puede deshacer. ¿Continuar?",
                "Confirmar eliminación",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (r != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            String respuesta = ClienteFideflix.enviarPeticion(
                    Protocolo.CMD_ELIMINAR + Protocolo.SEPARADOR + av.getId());

            String codigo = respuesta.split(Protocolo.SEPARADOR_REGEX, -1)[0];

            switch (codigo) {
                case Protocolo.RSP_OK -> {
                    info("\"" + av.getTitulo() + "\" fue eliminada.");
                    cargar();
                }
                case Protocolo.RSP_NO_ENCONTRADO -> {
                    // Otro cliente la borro primero. No es un error del
                    // sistema: es el estado compartido haciendose visible.
                    info("La obra ya no existe. Otro usuario pudo haberla eliminado.");
                    cargar();
                }
                default -> error("El servidor respondió: " + respuesta);
            }
        } catch (IOException ex) {
            sinConexion(ex);
        }
    }

    /*
     * Devuelve la obra seleccionada, o null avisando al usuario.
     *
     * convertRowIndexToModel: si la tabla estuviera ordenada por una
     * columna, el indice VISUAL no coincide con el del modelo. Usar el
     * indice de la vista para indexar los datos es un bug clasico que
     * aparece recien cuando alguien hace clic en un encabezado.
     */
    private Audiovisual seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione primero una obra de la lista.",
                    "Sin selección", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return obras.get(tabla.convertRowIndexToModel(fila));
    }

    // ═════════════════════════════════════════════════════════════════
    // MENSAJES
    // ═════════════════════════════════════════════════════════════════

    private void info(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Fideflix",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    /* Un fallo de red se explica en terminos que el usuario entiende y
     * puede accionar. Volcarle un stack trace no lo ayuda a nada y
     * ademas expone detalles internos del sistema. */
    private void sinConexion(IOException ex) {
        lblEstado.setText("  Sin conexión con el servidor.");
        JOptionPane.showMessageDialog(this,
                "No se pudo conectar con el servidor.\n"
              + "Verifique que la aplicación servidor esté iniciada.\n\n"
              + "Detalle: " + ex.getMessage(),
                "Sin conexión", JOptionPane.ERROR_MESSAGE);
    }
}
