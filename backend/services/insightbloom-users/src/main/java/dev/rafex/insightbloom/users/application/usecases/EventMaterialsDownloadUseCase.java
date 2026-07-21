package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;
import dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Construye el paquete de materiales del evento.
 *
 * <p>El paquete es deliberadamente una instantánea del material del moderador: incluye las
 * notas grupales, pero nunca las notas privadas derivadas de los asistentes.</p>
 */
public class EventMaterialsDownloadUseCase {
    private static final String DRAWIO = "DRAWIO";
    private static final String EXCALIDRAW = "EXCALIDRAW";
    private static final String ETHERPAD = "ETHERPAD";
    private static final String COLLABORATIVE = "COLLABORATIVE";

    private final ConferenceRepository conferenceRepository;
    private final EventCapabilityGuard capabilityGuard;
    private final GetEventDiagramUseCase getEventDiagramUseCase;
    private final GetEventWhiteboardUseCase getEventWhiteboardUseCase;
    private final EtherpadPort etherpadPort;
    private final JacksonJsonCodec jsonCodec = JacksonJsonCodec.defaultCodec();

    public EventMaterialsDownloadUseCase(final ConferenceRepository conferenceRepository,
                                         final EventCapabilityGuard capabilityGuard,
                                         final GetEventDiagramUseCase getEventDiagramUseCase,
                                         final GetEventWhiteboardUseCase getEventWhiteboardUseCase,
                                         final EtherpadPort etherpadPort) {
        this.conferenceRepository = conferenceRepository;
        this.capabilityGuard = capabilityGuard;
        this.getEventDiagramUseCase = getEventDiagramUseCase;
        this.getEventWhiteboardUseCase = getEventWhiteboardUseCase;
        this.etherpadPort = etherpadPort;
    }

    public byte[] execute(final String conferenceUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final Set<String> tools = enabledTools(conference);
        final List<String> files = new ArrayList<>();
        final Map<String, byte[]> binaryFiles = new LinkedHashMap<>();
        final Map<String, String> textFiles = new LinkedHashMap<>();

        if (tools.contains(DRAWIO)) {
            getEventDiagramUseCase.execute(conferenceUuid).ifPresent(diagram -> {
                addText(textFiles, files, "moderator/drawio/source.drawio", diagram.xml());
                addPublishedSvg(binaryFiles, files, "moderator/drawio/export.svg", "moderator/drawio/export.png",
                        diagram.publishedSvg());
            });
        }
        if (tools.contains(EXCALIDRAW)) {
            getEventWhiteboardUseCase.execute(conferenceUuid).ifPresent(whiteboard -> {
                addText(textFiles, files, "moderator/excalidraw/source.excalidraw", whiteboard.sceneJson());
                addPublishedSvg(binaryFiles, files, "moderator/excalidraw/export.svg", "moderator/excalidraw/export.png",
                        whiteboard.publishedSvg());
            });
        }
        if (tools.contains(ETHERPAD) && isGroupNotes(conference)) {
            try {
                final EtherpadPort.PadContent notes = etherpadPort.readPad(conferenceUuid);
                addText(textFiles, files, "moderator/etherpad/source.html", notes.html());
                addText(textFiles, files, "moderator/etherpad/export.html", notes.html());
                addText(textFiles, files, "moderator/etherpad/export.txt", notes.text());
            } catch (final RuntimeException ignored) {
                // El pad se crea de forma perezosa. Un evento sin notas todavía sigue teniendo
                // un ZIP válido con los demás materiales publicados.
            }
        }

        final Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", 1);
        manifest.put("conference", conference.getFriendlyId());
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("notes", !tools.contains(ETHERPAD) ? "NOT_ENABLED"
                : isGroupNotes(conference) ? "COLLABORATIVE" : "INDEPENDENT_NOT_INCLUDED");
        manifest.put("tools", tools);
        files.add("manifest.json");
        files.add("README.txt");
        manifest.put("files", files);
        textFiles.put("manifest.json", jsonCodec.toJson(manifest));
        textFiles.put("README.txt", readme(conference, isGroupNotes(conference)));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (final Map.Entry<String, String> entry : textFiles.entrySet()) {
                writeEntry(zip, entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
            for (final Map.Entry<String, byte[]> entry : binaryFiles.entrySet()) {
                writeEntry(zip, entry.getKey(), entry.getValue());
            }
            zip.finish();
            return output.toByteArray();
        } catch (final Exception e) {
            throw new IllegalStateException("materials_export_failed", e);
        }
    }

    private Set<String> enabledTools(final Conference conference) {
        final Set<String> tools = new LinkedHashSet<>();
        for (final CanvasConfig config : conference.getCanvasConfigs()) tools.add(config.tool());
        if (!tools.isEmpty()) return tools;
        if (conference.getCanvasTool() != null && !conference.getCanvasTool().isBlank()) {
            tools.add(conference.getCanvasTool());
            return tools;
        }
        if (capabilityGuard.hasCapability(conference, EventCapability.DIAGRAMMING)) tools.add(DRAWIO);
        if (capabilityGuard.hasCapability(conference, EventCapability.WHITEBOARD)) tools.add(EXCALIDRAW);
        if (capabilityGuard.hasCapability(conference, EventCapability.COLLAB_NOTES)) tools.add(ETHERPAD);
        return tools;
    }

    private static boolean isGroupNotes(final Conference conference) {
        return !GetOrCreateEventPadUseCase.isIndividual(conference)
                && (conference.getCanvasConfigs().stream().noneMatch(config -> ETHERPAD.equals(config.tool()))
                || conference.getCanvasConfigs().stream()
                .filter(config -> ETHERPAD.equals(config.tool()))
                .map(CanvasConfig::audienceMode)
                .anyMatch(COLLABORATIVE::equals));
    }

    private static void addText(final Map<String, String> textFiles, final List<String> files,
                                final String name, final String content) {
        if (content == null || content.isBlank()) return;
        textFiles.put(name, content);
        files.add(name);
    }

    private static void addPublishedSvg(final Map<String, byte[]> binaryFiles, final List<String> files,
                                        final String svgName, final String pngName, final String svg) {
        if (svg == null || svg.isBlank()) return;
        final String decoded = decodeSvg(svg);
        binaryFiles.put(svgName, decoded.getBytes(StandardCharsets.UTF_8));
        files.add(svgName);
        try {
            final PNGTranscoder transcoder = new PNGTranscoder();
            final ByteArrayOutputStream png = new ByteArrayOutputStream();
            transcoder.transcode(new TranscoderInput(new StringReader(decoded)), new TranscoderOutput(png));
            binaryFiles.put(pngName, png.toByteArray());
            files.add(pngName);
        } catch (final Exception e) {
            throw new IllegalStateException("published_svg_png_export_failed", e);
        }
    }

    private static String decodeSvg(final String svg) {
        if (!svg.startsWith("data:")) return svg;
        final int comma = svg.indexOf(',');
        if (comma < 0) return svg;
        final String metadata = svg.substring(0, comma);
        final String payload = svg.substring(comma + 1);
        if (metadata.contains(";base64")) {
            return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
        }
        return java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8);
    }

    private static void writeEntry(final ZipOutputStream zip, final String name, final byte[] bytes)
            throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String readme(final Conference conference, final boolean groupNotes) {
        return "Materiales del evento: " + conference.getName() + "\n\n"
                + "Este ZIP contiene las fuentes y exportaciones publicadas por el moderador.\n"
                + (groupNotes
                ? "Las notas de Etherpad son grupales y se incluyen cuando ya tienen contenido.\n"
                : "Las notas individuales no se incluyen; cada asistente debe exportarlas desde Notas.\n")
                + "Las notas individuales se purgan después de vencer el evento.\n";
    }
}
