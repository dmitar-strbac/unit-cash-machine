package rs.unit.backend.printing.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ReceiptPdfRendererTest {

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer();

    @Test
    void generatesNarrowReceiptWithReadableUnicodeText() throws Exception {
        byte[] bytes =
                renderer.render(
                        """
                        TRANSACTION RECEIPT
                        User: Dmitar Štrbac
                        Total EUR: 20.00
                        """);

        try (var document = Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(226.77f, document.getPage(0).getMediaBox().getWidth(), 0.01f);

            String text = new PDFTextStripper().getText(document);

            assertTrue(text.contains("Dmitar Štrbac"));
            assertTrue(text.contains("20.00"));
        }
    }

    @Test
    void longReceiptContinuesOnAdditionalPages() throws Exception {
        byte[] bytes = renderer.render("EUR 20.00 x 1\n".repeat(120));

        try (var document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() >= 3);
        }
    }

    @Test
    void missingFontProducesClearFailure() {
        ReceiptPdfRenderer renderer = spy(new ReceiptPdfRenderer());
        doReturn(null).when(renderer).openFont();

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> renderer.render("Receipt"));

        assertEquals("Receipt font is missing.", exception.getMessage());
    }

    @Test
    void invalidFontProducesWrappedIoFailure() {
        ReceiptPdfRenderer renderer = spy(new ReceiptPdfRenderer());

        doReturn(new ByteArrayInputStream(new byte[] {0, 1, 2, 3})).when(renderer).openFont();

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> renderer.render("Receipt"));

        assertEquals("Unable to generate receipt PDF.", exception.getMessage());
        assertTrue(exception.getCause() instanceof IOException);
    }

    @Test
    void controlCharactersAreIgnoredAndUnsupportedGlyphsAreReplaced() throws Exception {
        byte[] bytes = renderer.render("Before\u0001After \uDBFF\uDFFF");

        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);

            assertTrue(text.contains("BeforeAfter ?"));
        }
    }

    @Test
    void longLinesAreWrappedWithoutLosingText() throws Exception {
        String content = "W".repeat(200);

        byte[] bytes = renderer.render(content);

        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);

            assertEquals(content, text.replaceAll("\\s", ""));
        }
    }
}
