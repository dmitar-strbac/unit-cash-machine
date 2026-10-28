package rs.unit.backend.printing.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

@Component
public class ReceiptPdfRenderer {

    private static final float WIDTH = 226.77f;
    private static final float MARGIN = 14;
    private static final float FONT_SIZE = 9;
    private static final float LEADING = 12;
    private static final int PAGE_LINES = 55;

    public byte[] render(String content) {
        try (PDDocument document = new PDDocument();
                var fontStream = openFont();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontStream == null) {
                throw new IllegalStateException("Receipt font is missing.");
            }

            PDType0Font font = PDType0Font.load(document, fontStream);
            List<String> lines = wrap(content, font);

            for (int offset = 0; offset < lines.size(); offset += PAGE_LINES) {
                int end = Math.min(offset + PAGE_LINES, lines.size());
                int count = end - offset;

                float height = Math.max(120, count * LEADING + MARGIN * 2);
                PDPage page = new PDPage(new PDRectangle(WIDTH, height));
                document.addPage(page);

                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, FONT_SIZE);
                    stream.setLeading(LEADING);
                    stream.newLineAtOffset(MARGIN, height - MARGIN - FONT_SIZE);

                    for (int index = offset; index < end; index++) {
                        stream.showText(lines.get(index));
                        stream.newLine();
                    }

                    stream.endText();
                }
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate receipt PDF.", exception);
        }
    }

    private List<String> wrap(String content, PDType0Font font) throws IOException {
        List<String> lines = new ArrayList<>();

        for (String source : content.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();

            for (int codePoint : source.codePoints().toArray()) {
                if (Character.isISOControl(codePoint)) {
                    continue;
                }

                String character = new String(Character.toChars(codePoint));

                try {
                    font.encode(character);
                } catch (IllegalArgumentException exception) {
                    character = "?";
                }

                String candidate = line + character;

                if (!line.isEmpty()
                        && font.getStringWidth(candidate) / 1000 * FONT_SIZE > WIDTH - MARGIN * 2) {
                    lines.add(line.toString());
                    line.setLength(0);
                }

                line.append(character);
            }

            lines.add(line.toString());
        }

        return lines;
    }

    InputStream openFont() {
        return PDDocument.class.getResourceAsStream(
                "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
    }
}
