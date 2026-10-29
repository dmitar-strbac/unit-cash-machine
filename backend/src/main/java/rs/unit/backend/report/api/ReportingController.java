package rs.unit.backend.report.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.unit.backend.authentication.application.AuthenticatedUser;
import rs.unit.backend.printing.application.ReceiptView;
import rs.unit.backend.printing.infrastructure.ReceiptPdfRenderer;
import rs.unit.backend.report.application.ReportType;
import rs.unit.backend.report.application.ReportView;
import rs.unit.backend.report.application.ReportingService;

@RestController
public class ReportingController {

    private final ReportingService service;
    private final ReceiptPdfRenderer pdf;

    public ReportingController(ReportingService service, ReceiptPdfRenderer pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    @GetMapping("/api/reports")
    public ReportingService.Overview overview(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.overview(user);
    }

    @GetMapping("/api/reports/{type}")
    public ReportView report(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable ReportType type) {
        return service.report(user, type);
    }

    @PostMapping("/api/reports/{type}/print")
    public ReceiptView print(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable ReportType type,
            @Valid @RequestBody PrintRequest request) {
        return service.printReport(user, type, request.requestId());
    }

    @GetMapping("/api/receipts")
    public List<ReceiptView> receipts(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page) {
        return service.listReceipts(user, page);
    }

    @GetMapping("/api/receipts/{id}")
    public ReceiptView receipt(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.getReceipt(user, id);
    }

    @GetMapping("/api/receipts/{id}/pdf")
    public ResponseEntity<byte[]> pdf(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return download(service.getReceipt(user, id));
    }

    @PostMapping("/api/receipts/{id}/print")
    public ReportingService.PrintResult reprint(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody PrintRequest request) {
        return service.reprint(user, id, request.requestId());
    }

    @PostMapping("/api/deposits/current/receipt-access")
    public ReportingService.ReceiptAccess issueAccess(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.issueReceiptAccess(user);
    }

    @GetMapping("/api/receipt-access")
    public ReceiptView access(
            @RequestHeader(name = "X-Receipt-Token", required = false) String token) {
        return service.receiptByAccess(token);
    }

    @GetMapping("/api/receipt-access/pdf")
    public ResponseEntity<byte[]> accessPdf(
            @RequestHeader(name = "X-Receipt-Token", required = false) String token) {
        return download(service.receiptByAccess(token));
    }

    private ResponseEntity<byte[]> download(ReceiptView receipt) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("receipt-" + receipt.id() + ".pdf")
                                .build()
                                .toString())
                .body(pdf.render(receipt.content()));
    }

    public record PrintRequest(@NotNull UUID requestId) {}
}
