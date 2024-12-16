package main.java;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static spark.Spark.*;

public class ProcesarDatos {
    public static void main(String[] args) {
        port(8080); // Configura el puerto del servidor

        // Configura la carpeta para archivos estáticos
        staticFiles.location("/public"); // Sirve archivos desde resources/public

        get("/", (req, res) -> {
            res.redirect("/html/inicio.html");
            return null; // No necesita contenido adicional
        });

        // Ruta para el favicon
        get("/favicon.ico", (req, res) -> {
            res.type("image/x-icon");
            return ""; // Devuelve un favicon vacío para evitar el error
        });

        // Ruta para procesar el formulario
        post("/procesar", (req, res) -> {
            // Recoger los datos del formulario
            String direccionIP = req.queryParams("ipAddress");
            int mascaraSubred = Integer.parseInt(req.queryParams("subnetMask"));
            String[] arregloHosts = req.queryParams("hostsPerSubnet").split(",");
            String[] arregloNombres = req.queryParams("names").split(",");

            // Convertir los hosts y nombres a una lista de objetos
            List<SolicitudSubred> solicitudes = new ArrayList<>();
            for (int i = 0; i < arregloHosts.length; i++) {
                int hosts = Integer.parseInt(arregloHosts[i].trim());
                String nombre = arregloNombres[i].trim();
                solicitudes.add(new SolicitudSubred(hosts, nombre));
            }

            // Generar los pasos detallados del cálculo VLSM
            StringBuilder pasos = calcularVLSM(direccionIP, mascaraSubred, solicitudes);

            // Configurar la respuesta HTML
            res.type("text/html");

            // Crear el PDF utilizando iText 5
            try (ByteArrayOutputStream salidaPDF = new ByteArrayOutputStream()) {
                Document documento = new Document();
                PdfWriter.getInstance(documento, salidaPDF);

                documento.open();
                documento.add(new Paragraph("SUBNETTING VLSM"));
                documento.add(new Paragraph("\n"));
                documento.add(new Paragraph(pasos.toString()));
                documento.close();

                // Convertir el PDF en base64
                String pdfBase64 = Base64.getEncoder().encodeToString(salidaPDF.toByteArray());

                // Generar la respuesta HTML con el botón "Regresar"
                return "<html>" +
                        "<head>" +
                        "<title>Resultados VLSM</title>" +
                        "<style>" +
                        "body {\n" +
                        "    font-family: Arial, sans-serif;\n" +
                        "    background-color: #e3f2fd;\n" +
                        "    color: #0d47a1;\n" +
                        "}\n" +
                        "h1 {\n" +
                        "    background-color: #1565c0;\n" +
                        "    color: white;\n" +
                        "    padding: 20px;\n" +
                        "    text-align: center;\n" +
                        "}\n" +
                        "pre {\n" +
                        "    background-color: #ffffff;\n" +
                        "    padding: 20px;\n" +
                        "    border: 1px solid #bbdefb;\n" +
                        "    border-radius: 5px;\n" +
                        "}\n" +
                        "a {\n" +
                        "    background-color: #0d47a1;\n" +
                        "    color: white;\n" +
                        "    padding: 10px 20px;\n" +
                        "    text-decoration: none;\n" +
                        "    border-radius: 5px;\n" +
                        "}\n" +
                        "a:hover {\n" +
                        "    background-color: #1565c0;\n" +
                        "}\n" +
                        "button {\n" +
                        "    background-color: #42a5f5;\n" +
                        "    color: white;\n" +
                        "    padding: 10px 20px;\n" +
                        "    border: none;\n" +
                        "    border-radius: 5px;\n" +
                        "}\n" +
                        "button:hover {\n" +
                        "    background-color: #1e88e5;\n" +
                        "}\n" +
                        "</style>" +
                        "</head>" +
                        "<body>" +
                        "<h1>Resultados del Subnetting VLSM</h1>" +
                        "<pre>" + pasos.toString() + "</pre>" +
                        "<a href='data:application/pdf;base64," + pdfBase64 + "' download='resultados-vlsm.pdf'>Descargar PDF</a>" +
                        "<button onclick='window.location.href=\"/html/inicio.html\"'>Regresar</button>" +
                        "</body>" +
                        "</html>";

            } catch (Exception e) {
                e.printStackTrace();
                halt(500, "Error al generar el PDF.");
                return null;
            }
        });
    }

    private static StringBuilder calcularVLSM(String direccionBase, int mascaraBase, List<SolicitudSubred> solicitudes) {
        solicitudes.sort(Comparator.comparingInt(SolicitudSubred::obtenerHosts).reversed());
        StringBuilder pasos = new StringBuilder();
        StringBuilder subredesRestantes = new StringBuilder();
        StringBuilder resultadosFinales = new StringBuilder("\nRESULTADOS FINALES DE SUBREDES\n");
        int ipActual = convertirIpAEntero(direccionBase);
        int totalHostsBase = (int) Math.pow(2, 32 - mascaraBase);
        List<NodoSubred> subredesAsignadas = new ArrayList<>();

        pasos.append("Red Padre\n");
        pasos.append(convertirEnteroABinarioIp(ipActual)).append(" | ").append(direccionBase).append("/").append(mascaraBase).append("\n\n");

        pasos.append("HOST POR LAN\n");
        for (SolicitudSubred solicitud : solicitudes) {
            pasos.append(solicitud.obtenerNombre()).append(" ").append(solicitud.obtenerHosts()).append("\n");
        }
        pasos.append("\n");

        for (SolicitudSubred solicitud : solicitudes) {
            int hostsRequeridos = solicitud.obtenerHosts();

            // Calcular la máscara mínima requerida para esta subred
            int bitsRequeridos = 0;
            while (Math.pow(2, bitsRequeridos) - 2 < hostsRequeridos) {
                bitsRequeridos++;
            }

            int mascaraSubred = 32 - bitsRequeridos; // Nueva máscara
            int totalHosts = (int) Math.pow(2, 32 - mascaraSubred);

            pasos.append(" ".repeat(subredesAsignadas.size() * 2))
                    .append(convertirEnteroABinarioIp(ipActual)).append(" | ").append(convertirEnteroAIp(ipActual))
                    .append("/").append(mascaraBase).append("\n");

            pasos.append(" ".repeat((subredesAsignadas.size() + 1) * 2))
                    .append(convertirEnteroABinarioIp(ipActual)).append(" | ").append(convertirEnteroAIp(ipActual))
                    .append("/").append(mascaraSubred).append(" <- ").append(solicitud.obtenerNombre()).append("\n");

            // Registrar el nodo asignado
            subredesAsignadas.add(new NodoSubred(ipActual, mascaraSubred, subredesAsignadas.size()));

            // Registrar resultados finales
            String ipRed = convertirEnteroAIp(ipActual);
            String primeraIp = convertirEnteroAIp(ipActual + 1);
            String ultimaIp = convertirEnteroAIp(ipActual + totalHosts - 2);
            String ipBroadcast = convertirEnteroAIp(ipActual + totalHosts - 1);
            String mascara = convertirMascaraADecimal(mascaraSubred);

            resultadosFinales.append("Subred: ").append(solicitud.obtenerNombre()).append("\n")
                    .append("  IP de Red: ").append(ipRed).append("\n")
                    .append("  Primera IP usable: ").append(primeraIp).append("\n")
                    .append("  Ultima IP usable: ").append(ultimaIp).append("\n")
                    .append("  Broadcast: ").append(ipBroadcast).append("\n")
                    .append("  Mascara: /").append(mascaraSubred).append(" (").append(mascara).append(")\n")
                    .append("--------------------------\n");

            ipActual += totalHosts;
        }

        // Calcular y generar subredes sobrantes
        int hostsRestantes = totalHostsBase - (ipActual - convertirIpAEntero(direccionBase));
        if (hostsRestantes > 0) {
            subredesRestantes.append("\nSubredes sobrantes:\n");

            int ipRestante = ipActual;
            List<String> subredesSobrantes = new ArrayList<>();
            while (hostsRestantes > 0) {
                int maxHosts = Integer.highestOneBit(hostsRestantes);
                int mascaraSubredRestante = 32 - Integer.numberOfTrailingZeros(maxHosts);

                subredesSobrantes.add(" ".repeat(subredesAsignadas.size() * 2) +
                        convertirEnteroABinarioIp(ipRestante) + " | " +
                        convertirEnteroAIp(ipRestante) + "/" + mascaraSubredRestante + "\n");

                ipRestante += maxHosts;
                hostsRestantes -= maxHosts;
            }

            for (String subred : subredesSobrantes) {
                subredesRestantes.append(subred);
            }
        }

        pasos.append(subredesRestantes);
        pasos.append(resultadosFinales);
        return pasos;
    }

    private static int convertirIpAEntero(String direccionIP) {
        String[] partes = direccionIP.split("\\.");
        return (Integer.parseInt(partes[0]) << 24) |
                (Integer.parseInt(partes[1]) << 16) |
                (Integer.parseInt(partes[2]) << 8) |
                Integer.parseInt(partes[3]);
    }

    private static String convertirEnteroAIp(int ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
    }

    private static String convertirEnteroABinarioIp(int ip) {
        return String.format("%8s", Integer.toBinaryString((ip >> 24) & 0xFF)).replace(' ', '0') + "." +
                String.format("%8s", Integer.toBinaryString((ip >> 16) & 0xFF)).replace(' ', '0') + "." +
                String.format("%8s", Integer.toBinaryString((ip >> 8) & 0xFF)).replace(' ', '0') + "." +
                String.format("%8s", Integer.toBinaryString(ip & 0xFF)).replace(' ', '0');
    }

    private static String convertirMascaraADecimal(int mascaraSubred) {
        int mascara = 0xffffffff << (32 - mascaraSubred);
        return ((mascara >>> 24) & 0xFF) + "." +
                ((mascara >>> 16) & 0xFF) + "." +
                ((mascara >>> 8) & 0xFF) + "." +
                (mascara & 0xFF);
    }

    static class NodoSubred {
        int ip;
        int mascara;
        int nivel;

        public NodoSubred(int ip, int mascara, int nivel) {
            this.ip = ip;
            this.mascara = mascara;
            this.nivel = nivel;
        }
    }

    static class SolicitudSubred {
        private final int hosts;
        private final String nombre;

        public SolicitudSubred(int hosts, String nombre) {
            this.hosts = hosts;
            this.nombre = nombre;
        }

        public int obtenerHosts() {
            return hosts;
        }

        public String obtenerNombre() {
            return nombre;
        }
    }
}
