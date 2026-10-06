package service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * SERVIDOR UDP - La parte que ESCUCHA y RESPONDE
 *
 * ¿QUÉ HACE?
 * 1. Abre un puerto UDP (ej: puerto 5000)
 * 2. Escucha paquetes que llegan
 * 3. Los procesa (llamando al TelemetryProcessor)
 * 4. Envía respuesta al cliente
 *
 * IMPORTANTE: Ejecuta en UN HILO separado
 *             Así NO bloquea el programa principal
 *
 * FLUJO:
 * start() → Inicia el servidor
 * listen() → Entra en loop infinito escuchando
 * handlePacket() → Procesa cada paquete
 * stop() → Detiene el servidor
 */
public class BaseStationServer {

    /**
     * PUERTO EN EL QUE ESCUCHA
     * Ejemplo: 5000
     * El cliente se conectará a este puerto
     */
    private final int port;

    /**
     * EL PROCESADOR DE TELEMETRÍA
     *
     * ¿QUÉ ES?
     * La clase que procesa cada mensaje
     * (la que explicamos en TelemetryProcessor)
     *
     * FLUJO:
     * 1. Servidor recibe paquete UDP
     * 2. Extrae el mensaje
     * 3. Llama: processor.process(mensaje)
     * 4. Obtiene respuesta
     * 5. Envía respuesta
     */
    private final TelemetryProcessor processor;

    /**
     * EL SOCKET UDP
     *
     * ¿QUÉ ES?
     * La herramienta para comunicarse por UDP
     *
     * INICIALMENTE: null
     * DESPUÉS DE start(): Activo y escuchando
     * DESPUÉS DE stop(): Cerrado
     */
    private DatagramSocket socket;

    /**
     * ¿EL SERVIDOR ESTÁ CORRIENDO?
     *
     * volatile = Visible entre hilos
     * Importante porque el hilo listener la verifica constantemente
     *
     * Flujo:
     * start() → running = true
     * listen() → while(running) { ... }
     * stop() → running = false → hilo sale del while
     */
    private volatile boolean running = false;

    /**
     * EL HILO QUE ESCUCHA
     *
     * ¿POR QUÉ SEPARADO?
     * Si escuchamos en el hilo principal:
     *   - socket.receive() BLOQUEA
     *   - El programa NO RESPONDE
     *
     * Con hilo separado:
     *   - El hilo escucha (bloqueado)
     *   - El programa principal sigue funcionando
     *   - Podemos hacer otras cosas
     */
    private Thread listenerThread;

    /**
     * CONSTRUCTOR
     *
     * @param port Puerto en el que escuchará (ej: 5000)
     * @param processor El TelemetryProcessor que procesa mensajes
     */
    public BaseStationServer(int port, TelemetryProcessor processor) {
        this.port = port;
        this.processor = processor;
    }

    /**
     * INICIAR EL SERVIDOR
     *
     * ¿QUÉ PASA AQUÍ?
     * 1. Crear el socket UDP
     * 2. Marcar como "running"
     * 3. Crear el hilo que escucha
     * 4. Iniciar el hilo
     *
     * DESPUÉS DE ESTO:
     * El servidor está escuchando en puerto X
     * Listo para recibir paquetes
     *
     * @throws SocketException Si el puerto está ocupado o hay error
     */
    public synchronized void start() throws SocketException {
        // ¿YA ESTÁ CORRIENDO?
        if (running) {
            return;  // Ya se inició, no hacer nada
        }

        /**
         * CREAR EL SOCKET UDP
         *
         * new DatagramSocket(5000)
         *
         * Crea un socket que escucha en puerto 5000
         *
         * SI EL PUERTO ESTÁ OCUPADO:
         * → SocketException
         *
         * ¿QUÉ HACER?
         * - Cambiar el puerto
         * - Cerrar la aplicación anterior
         * - Usar puerto 0 (automático)
         */
        this.socket = new DatagramSocket(this.port);

        // Marcar que está corriendo
        this.running = true;

        /**
         * CREAR EL HILO QUE ESCUCHA
         *
         * new Thread(() -> { ... })
         *
         * Crea un nuevo hilo que ejecuta el código dentro de las llaves
         *
         * "BaseStationServer-Thread" = nombre del hilo (para debug)
         */
        this.listenerThread = new Thread(() -> {
            /**
             * ESTE CÓDIGO CORRE EN UN HILO SEPARADO
             *
             * El hilo principal puede seguir haciendo otras cosas
             * Mientras este hilo escucha UDP
             */

            // Buffer para recibir datos
            // Tamaño: 1024 bytes (máximo tamaño de mensaje UDP)
            byte[] buffer = new byte[1024];

            // LOOP INFINITO: Escucha mientras el servidor esté activo
            while (running && !socket.isClosed()) {
                try {
                    /**
                     * CREAR PAQUETE VACÍO PARA RECIBIR
                     *
                     * new DatagramPacket(buffer, buffer.length)
                     *
                     * Esto crea un "contenedor" vacío listo para recibir datos
                     *
                     * CUANDO LLEGA UN PAQUETE:
                     * Los datos llenan el buffer
                     * También se guarda la IP y puerto del cliente
                     */
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                    /**
                     * ESPERAR PAQUETE
                     *
                     * socket.receive(packet)
                     *
                     * ¡¡ BLOQUEA AQUÍ !!
                     * El hilo espera hasta que llegue un paquete
                     *
                     * CUANDO LLEGA:
                     * - Los datos se guardan en 'packet'
                     * - Se desbloquea
                     * - Continúa al siguiente paso
                     */
                    socket.receive(packet);

                    // Procesar el paquete que llegó
                    handlePacket(socket, packet);

                } catch (SocketException e) {
                    /**
                     * SOCKET CERRADO INTENCIONALMENTE
                     *
                     * ¿CUÁNDO PASA?
                     * El programa principal llamó a stop()
                     * stop() → running = false → sale del while
                     *
                     * running = false
                     * → while(running) es false
                     * → Sale del loop
                     * → Hilo termina
                     */
                    if (!running) {
                        break;  // Sale del while
                    }
                } catch (IOException e) {
                    // Error de I/O (problema en la red)
                    if (running) {
                        System.err.println("Error procesando datagrama: " + e.getMessage());
                    }
                }
            }
        }, "BaseStationServer-Thread");

        // INICIAR EL HILO
        // Ahora el hilo está escuchando en segundo plano
        this.listenerThread.start();
    }

    /**
     * PROCESAR UN PAQUETE RECIBIDO
     *
     * Este método se llama CADA VEZ que llega un paquete UDP
     *
     * FLUJO:
     * 1. Convertir bytes → String
     * 2. Procesar el mensaje
     * 3. Convertir respuesta → bytes
     * 4. Enviar respuesta al cliente
     *
     * @param socket El socket UDP activo
     * @param packet El paquete que llegó (contiene datos, IP, puerto del cliente)
     * @throws IOException Si hay error enviando la respuesta
     */
    public void handlePacket(DatagramSocket socket, DatagramPacket packet) throws IOException {

        // ═══════════════════════════════════════════════════════════════
        // PASO 1: CONVERTIR BYTES → STRING
        // ═══════════════════════════════════════════════════════════════

        /**
         * ¿POR QUÉ TRES PARÁMETROS?
         *
         * packet.getData()        → Array de bytes (tamaño 1024)
         * packet.getOffset()      → Dónde comienzan los datos válidos
         * packet.getLength()      → Cuántos bytes son válidos
         *
         * EJEMPLO:
         * Buffer: [115, 101, 110, 115, 111, 114, 0, 0, 0, ...]
         *         ↑                              ↑
         *         ↑ offset = 0                   ↑
         *         length = 6
         *
         * Resultado: "sensor"
         *
         * ¿POR QUÉ NECESITAMOS ESTO?
         * El buffer tiene 1024 bytes, pero solo ~10 son datos reales
         * Sin offset/length, leerías todos los 1024 (basura incluida)
         */
        String message = new String(packet.getData(), packet.getOffset(),
                packet.getLength(), StandardCharsets.UTF_8);

        // Ahora 'message' es una String limpia
        // Ejemplo: "sensor-01;TEMP;25.5"

        // ═══════════════════════════════════════════════════════════════
        // PASO 2: PROCESAR EL MENSAJE
        // ═══════════════════════════════════════════════════════════════

        /**
         * LLAMAR AL TELEMETRY PROCESSOR
         *
         * processor.process(message)
         *
         * Entrada: "sensor-01;TEMP;25.5"
         * Salida: "OK;TEMP_RECORDED;25.5" (o un ERROR)
         *
         * TODO el trabajo de validación/procesamiento ocurre aquí
         */
        String response = this.processor.process(message);

        // ═══════════════════════════════════════════════════════════════
        // PASO 3: CONVERTIR RESPUESTA → BYTES
        // ═══════════════════════════════════════════════════════════════

        /**
         * CONVERTIR STRING A BYTES
         *
         * Para enviar por UDP, necesitamos BYTES, no strings
         *
         * response = "OK;TEMP_RECORDED;25.5"
         *
         * Después de getBytes():
         * bytes = [79, 75, 59, 84, 69, 77, 80, ...]
         */
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        // ═══════════════════════════════════════════════════════════════
        // PASO 4: CREAR PAQUETE DE RESPUESTA
        // ═══════════════════════════════════════════════════════════════

        /**
         * CREAR PAQUETE CON DIRECCIÓN DEL CLIENTE
         *
         * ¿CÓMO SABE DÓNDE RESPONDER?
         *
         * packet.getAddress()  → IP del cliente que envió
         *                        Ejemplo: 192.168.1.5
         *
         * packet.getPort()     → Puerto del cliente que envió
         *                        Ejemplo: 54321
         *
         * FLUJO:
         * 1. Cliente envía desde IP:192.168.1.5, PUERTO:54321
         * 2. Tú respondes a IP:192.168.1.5, PUERTO:54321
         * 3. Cliente recibe la respuesta
         *
         * ES COMO:
         * - Cliente te escribió en un papel con su dirección
         * - Tú respondes enviando el papel a esa dirección
         */
        DatagramPacket responsePacket = new DatagramPacket(bytes, bytes.length, packet.getAddress(), packet.getPort());

        // ═══════════════════════════════════════════════════════════════
        // PASO 5: ENVIAR PAQUETE
        // ═══════════════════════════════════════════════════════════════

        /**
         * socket.send(responsePacket)
         *
         * ENVÍA el paquete al cliente
         *
         * El cliente que estaba esperando:
         * socket.receive() se desbloquea
         * Recibe la respuesta
         */
        socket.send(responsePacket);
    }

    /**
     * DETENER EL SERVIDOR
     *
     * ¿QUÉ PASA?
     * 1. running = false
     *    → El hilo listener sale del while
     * 2. Cerrar el socket
     *    → No se pueden recibir más paquetes
     * 3. Esperar a que el hilo termine
     *    → join() espera a que termine
     *
     * DESPUÉS DE ESTO:
     * El servidor no está escuchando
     * Los paquetes que lleguen se pierden
     */
    public synchronized void stop() {
        // Señal al hilo: "Termina"
        this.running = false;

        // Cerrar el socket (si está abierto)
        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }

        // Esperar a que el hilo listener termine
        if (this.listenerThread != null) {
            try {
                /**
                 * join(1000) = "Espera a que el hilo termine, máximo 1 segundo"
                 *
                 * ¿POR QUÉ 1 SEGUNDO?
                 * El hilo podría estar esperando en socket.receive()
                 * En cuanto se cierre el socket, sale
                 * No debería tardar más de 1 segundo
                 */
                this.listenerThread.join(1000);
            } catch (InterruptedException ignored) {
                // Si algo interrumpe este thread, marcar como interrumpido
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * GETTER: ¿EL SERVIDOR ESTÁ CORRIENDO?
     *
     * @return true si está activo, false si está apagado
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * GETTER: ¿EN QUÉ PUERTO ESCUCHA?
     *
     * @return El puerto local (útil si usaste puerto 0 y Java eligió uno)
     */
    public int getPort() {
        return (socket != null && !socket.isClosed()) ? socket.getLocalPort() : port;
    }

    /**
     * GETTER: EL PROCESADOR
     *
     * @return El TelemetryProcessor (para acceder a datos, limpiar, etc)
     */
    public TelemetryProcessor getProcessor() {
        return processor;
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * POSIBLES VARIACIONES EN EL PARCIAL
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. CAMBIAR PUERTO
 *    En lugar de puerto 5000, podría ser 6000 o 8000
 *    ¿CÓMO ADAPTARTE?
 *    - Cambiar el número en new DatagramSocket(puerto)
 *    - TODO LO DEMÁS igual
 *
 * 2. PROCESAR DIFERENTE
 *    En lugar de llamar processor.process()
 *    Podrías implementar la lógica aquí mismo
 *    ¿CÓMO ADAPTARTE?
 *    - Reemplazar processor.process(message) con tu lógica
 *    - Resto del handlePacket NO cambia
 *
 * 3. GUARDAR HISTORIAL DE PAQUETES
 *    "Registra cada paquete recibido"
 *    ¿CÓMO ADAPTARTE?
 *    - En handlePacket(), después de recibir
 *    - Añadir: historial.add(message)
 *    - Luego seguir procesando normalmente
 *
 * 4. VALIDAR ANTES DE PROCESAR
 *    "Solo procesa si el cliente es conocido"
 *    ¿CÓMO ADAPTARTE?
 *    - Obtener IP del cliente: packet.getAddress()
 *    - Verificar si está en lista de clientes válidos
 *    - Si NO, responder ERROR
 *    - Si SÍ, procesar normalmente
 *
 * 5. LOGGING DE COMUNICACIÓN
 *    "Registra cada mensaje enviado/recibido"
 *    ¿CÓMO ADAPTARTE?
 *    - Después de recibir: System.out.println("Recibido: " + message)
 *    - Después de enviar: System.out.println("Enviado: " + response)
 *    - TODO LO DEMÁS igual
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * ERRORES COMUNES QUE NO COMETER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ❌ OLVIDAR offset/length EN LA CONVERSIÓN
 * String msg = new String(packet.getData(), UTF_8);
 * → Lee todos los 1024 bytes (basura)
 * ✅ USAR OFFSET Y LENGTH
 * String msg = new String(packet.getData(), packet.getOffset(),
 *                         packet.getLength(), UTF_8);
 *
 * ❌ NO RESPONDER AL MISMO CLIENTE
 * packet.getAddress()  // Ignoras esto
 * packet.getPort()     // E ignoras esto también
 * → Cliente nunca recibe respuesta
 * ✅ USAR DIRECCIÓN DEL CLIENTE
 * new DatagramPacket(bytes, bytes.length, packet.getAddress(), packet.getPort())
 *
 * ❌ NO CERRAR EL SOCKET
 * El servidor nunca se apaga completamente
 * → Puertos quedan ocupados
 * ✅ LLAMAR A stop()
 * server.stop();
 *
 * ❌ OLVIDAR MANEJAR EXCEPCIONES
 * try-catch vacío
 * → Errores se ocultan
 * ✅ LOGUEAR ERRORES
 * System.err.println("Error: " + e.getMessage());
 *
 * ❌ NO USAR SYNCHRONIZED
 * Múltiples hilos llamando start()/stop() simultáneamente
 * → Condiciones de carrera (race conditions)
 * ✅ USAR synchronized
 * public synchronized void start()
 * public synchronized void stop()
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */