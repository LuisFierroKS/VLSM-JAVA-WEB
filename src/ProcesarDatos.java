import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static spark.Spark.*;

public class ProcesarDatos {
    public static void main(String[] args) {
        port(8080); // Configura el puerto del servidor

        // Configura la carpeta para archivos estáticos
        staticFiles.externalLocation("C:/Users/user_win10/Desktop/VLSM JAVA WEB/public");

        // Ruta para procesar el formulario
        post("/procesar", (req, res) -> {
            // Recoger los datos del formulario
            String ipAddress = req.queryParams("ipAddress");
            int subnetMask = Integer.parseInt(req.queryParams("subnetMask"));
            String[] hostsArray = req.queryParams("hostsPerSubnet").split(",");
            String[] namesArray = req.queryParams("names").split(",");

            // Convertir los hosts y nombres a una lista de objetos
            List<SubnetRequest> requests = new ArrayList<>();
            for (int i = 0; i < hostsArray.length; i++) {
                int hosts = Integer.parseInt(hostsArray[i].trim());
                String name = namesArray[i].trim();
                requests.add(new SubnetRequest(hosts, name));
            }

            // Generar los pasos detallados del cálculo VLSM
            StringBuilder steps = calculateVLSM(ipAddress, subnetMask, requests);

            // Configurar la respuesta como PDF
            res.type("application/pdf");
            res.header("Content-Disposition", "attachment; filename=resultados-vlsm.pdf");

            // Crear el PDF utilizando iText 5
            try (ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream()) {
                Document document = new Document();
                PdfWriter.getInstance(document, pdfOutput);

                document.open();
                document.add(new Paragraph("SUBNETTING VLSM"));
                document.add(new Paragraph("\n"));
                document.add(new Paragraph(steps.toString()));
                document.close();

                // Escribir el contenido del PDF en la respuesta
                try (OutputStream out = res.raw().getOutputStream()) {
                    out.write(pdfOutput.toByteArray());
                    out.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
                halt(500, "Error al generar el PDF.");
            }

            return res.raw();
        });
    }

    private static StringBuilder calculateVLSM(String baseIp, int baseMask, List<SubnetRequest> requests) {
        requests.sort(Comparator.comparingInt(SubnetRequest::getHosts).reversed());
        StringBuilder steps = new StringBuilder();
        StringBuilder remainingSubnets = new StringBuilder();
        StringBuilder finalResults = new StringBuilder("\nRESULTADOS FINALES DE SUBREDES\n");
        int currentIp = convertIpToInteger(baseIp);
        int baseTotalHosts = (int) Math.pow(2, 32 - baseMask);
        List<SubnetNode> assignedSubnets = new ArrayList<>();

        steps.append("Red Padre\n");
        steps.append(convertIntegerToBinaryIp(currentIp)).append(" | ").append(baseIp).append("/").append(baseMask).append("\n\n");

        steps.append("HOST POR LAN\n");
        for (SubnetRequest request : requests) {
            steps.append(request.getName()).append(" ").append(request.getHosts()).append("\n");
        }
        steps.append("\n");

        for (SubnetRequest request : requests) {
            int hostsNeeded = request.getHosts();

            // Calcular la máscara mínima requerida para esta subred
            int requiredBits = 0;
            while (Math.pow(2, requiredBits) - 2 < hostsNeeded) {
                requiredBits++;
            }

            int subnetMask = 32 - requiredBits; // Nueva máscara
            int totalHosts = (int) Math.pow(2, 32 - subnetMask);

            steps.append(" ".repeat(assignedSubnets.size() * 2))
                 .append(convertIntegerToBinaryIp(currentIp)).append(" | ").append(convertIntegerToIp(currentIp))
                 .append("/").append(baseMask).append("\n");

            steps.append(" ".repeat((assignedSubnets.size() + 1) * 2))
                 .append(convertIntegerToBinaryIp(currentIp)).append(" | ").append(convertIntegerToIp(currentIp))
                 .append("/").append(subnetMask).append(" <- ").append(request.getName()).append("\n");

            // Registrar el nodo asignado
            assignedSubnets.add(new SubnetNode(currentIp, subnetMask, assignedSubnets.size()));

            // Registrar resultados finales
            String networkIp = convertIntegerToIp(currentIp);
            String firstIp = convertIntegerToIp(currentIp + 1);
            String lastIp = convertIntegerToIp(currentIp + totalHosts - 2);
            String broadcastIp = convertIntegerToIp(currentIp + totalHosts - 1);
            String mask = convertMaskToDecimal(subnetMask);

            finalResults.append("Subred: ").append(request.getName()).append("\n")
                        .append("  IP de Red: ").append(networkIp).append("\n")
                        .append("  Primera IP usable: ").append(firstIp).append("\n")
                        .append("  Ultima IP usable: ").append(lastIp).append("\n")
                        .append("  Broadcast: ").append(broadcastIp).append("\n")
                        .append("  Mascara: /").append(subnetMask).append(" (").append(mask).append(")\n")
                        .append("--------------------------\n");

            currentIp += totalHosts;
        }

        // Calcular y generar subredes sobrantes
        int remainingHosts = baseTotalHosts - (currentIp - convertIpToInteger(baseIp));
        if (remainingHosts > 0) {
            remainingSubnets.append("\nSubredes sobrantes:\n");

            int remainingIp = currentIp;
            List<String> leftoverSubnets = new ArrayList<>();
            while (remainingHosts > 0) {
                int maxHosts = Integer.highestOneBit(remainingHosts);
                int remainingSubnetMask = 32 - Integer.numberOfTrailingZeros(maxHosts);

                leftoverSubnets.add(" ".repeat(assignedSubnets.size() * 2) +
                                    convertIntegerToBinaryIp(remainingIp) + " | " +
                                    convertIntegerToIp(remainingIp) + "/" + remainingSubnetMask + "\n");

                remainingIp += maxHosts;
                remainingHosts -= maxHosts;
            }

            // Agregar las subredes sobrantes en orden inverso
            Collections.reverse(leftoverSubnets);
            for (String subnet : leftoverSubnets) {
                remainingSubnets.append(subnet);
            }
        }

        steps.append(remainingSubnets);
        steps.append(finalResults);
        return steps;
    }

    private static int convertIpToInteger(String ipAddress) {
        String[] parts = ipAddress.split("\\.");
        return (Integer.parseInt(parts[0]) << 24) |
               (Integer.parseInt(parts[1]) << 16) |
               (Integer.parseInt(parts[2]) << 8) |
               Integer.parseInt(parts[3]);
    }

    private static String convertIntegerToIp(int ip) {
        return ((ip >> 24) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." +
               (ip & 0xFF);
    }

    private static String convertIntegerToBinaryIp(int ip) {
        return String.format("%8s", Integer.toBinaryString((ip >> 24) & 0xFF)).replace(' ', '0') + "." +
               String.format("%8s", Integer.toBinaryString((ip >> 16) & 0xFF)).replace(' ', '0') + "." +
               String.format("%8s", Integer.toBinaryString((ip >> 8) & 0xFF)).replace(' ', '0') + "." +
               String.format("%8s", Integer.toBinaryString(ip & 0xFF)).replace(' ', '0');
    }

    private static String convertMaskToDecimal(int subnetMask) {
        int mask = 0xffffffff << (32 - subnetMask);
        return ((mask >>> 24) & 0xFF) + "." +
               ((mask >>> 16) & 0xFF) + "." +
               ((mask >>> 8) & 0xFF) + "." +
               (mask & 0xFF);
    }

    static class SubnetNode {
        int ip;
        int mask;
        int level;

        public SubnetNode(int ip, int mask, int level) {
            this.ip = ip;
            this.mask = mask;
            this.level = level;
        }
    }

    static class SubnetRequest {
        private final int hosts;
        private final String name;

        public SubnetRequest(int hosts, String name) {
            this.hosts = hosts;
            this.name = name;
        }

        public int getHosts() {
            return hosts;
        }

        public String getName() {
            return name;
        }
    }
}
