package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Cliente UDP que simula un dispositivo/sensor IoT (PeerA).
 * Envía lecturas de telemetría y consultas de estado a la Estación Base (PeerB),
 * esperando una respuesta dentro de una ventana de tiempo (timeout).
 */
public class SensorClient {

    private final String serverHost;
    private final int serverPort;
    private final int timeoutMs;

    public SensorClient(String serverHost, int serverPort, int timeoutMs) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Envía una lectura de telemetría al servidor y espera la confirmación o alerta.
     * 
     * @param deviceId Identificador único del dispositivo (ej. "sensor-01").
     * @param sensorType Tipo de sensor ("TEMP", "HUMIDITY", "BATTERY").
     * @param value Valor numérico de la lectura.
     * @return Respuesta recibida de la estación base.
     * @throws IOException Si ocurre un error de red o timeout.
     */
    public String sendTelemetry(String deviceId, String sensorType, double value) throws IOException {
        String message = deviceId + ";" + sensorType + ";" + value;
        return sendAndReceive(message);
    }

    /**
     * Consulta el último estado registrado de un dispositivo en la estación base.
     * 
     * @param deviceId Identificador del dispositivo a consultar.
     * @return Respuesta de estado recibida del servidor.
     * @throws IOException Si ocurre un error de red o timeout.
     */
    public String queryStatus(String deviceId) throws IOException {
        String message = "STATUS;" + deviceId;
        return sendAndReceive(message);
    }

    /**
     * Envía un mensaje en texto plano a través de UDP y espera la respuesta del servidor.
     * 
     * @param message Cadena de texto a transmitir.
     * @return Cadena de texto recibida en la respuesta.
     * @throws SocketTimeoutException Si transcurre el tiempo límite sin recibir respuesta.
     * @throws IOException Si ocurre un error en el socket o resolución de red.
     */
    public String sendAndReceive(String message) throws IOException {
        // TODO Paso 3.1: Crear un DatagramSocket (se recomienda usar bloque try-with-resources).
        try(DatagramSocket socketcito = new DatagramSocket()) {

            // TODO Paso 3.2: Configurar el tiempo de espera máximo mediante socket.setSoTimeout(this.timeoutMs).
            socketcito.setSoTimeout(this.timeoutMs);

            // TODO Paso 3.3: Convertir 'message' a bytes en UTF-8 y construir el DatagramPacket
            // con destino InetAddress.getByName(this.serverHost) y this.serverPort.
            byte[] enviarDatos = message.getBytes(StandardCharsets.UTF_8);

            // TODO Paso 3.4: Enviar el paquete con socket.send(packet).
            DatagramPacket datagramito = new DatagramPacket(enviarDatos, enviarDatos.length, InetAddress.getByName(this.serverHost), this.serverPort);
            socketcito.send(datagramito);

            // TODO Paso 3.5: Crear un buffer receptor (byte[1024]) y un DatagramPacket para la respuesta.
            byte[] responsebuffer = new byte[1024]; //buffer para recibir la respuesta del servidor
            // TODO Paso 3.6: Recibir la respuesta con socket.receive(responsePacket).
            DatagramPacket respuestaDatagramito = new DatagramPacket(responsebuffer, responsebuffer.length);
            socketcito.receive(respuestaDatagramito);
            String respuesta = new String(respuestaDatagramito.getData(), respuestaDatagramito.getOffset(), respuestaDatagramito.getLength(), StandardCharsets.UTF_8);

            // TODO Paso 3.7: Convertir los bytes recibidos a String UTF-8 usando offset y length,
            return respuesta.trim(); // aplicar trim() y retornar la cadena resultante.


        }

    }

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
