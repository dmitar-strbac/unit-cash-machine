package rs.unit.backend.printing.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import rs.unit.backend.printing.application.ReceiptView;

@Repository
public class ReceiptRepository {

    private static final String RECEIPT_SELECT =
            """
            SELECT receipt.*,
                   COALESCE(attempt.status, 'NOT_PRINTED') AS print_status,
                   attempt.error_code AS print_error
            FROM cash_machine.receipt receipt
            LEFT JOIN LATERAL (
                SELECT status, error_code
                FROM cash_machine.print_attempt
                WHERE receipt_id = receipt.id
                ORDER BY requested_at DESC, id DESC
                LIMIT 1
            ) attempt ON TRUE
            """;

    private final JdbcTemplate jdbc;

    public ReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(
            UUID id,
            String kind,
            UUID depositId,
            UUID requestedBy,
            UUID requestId,
            Instant now,
            long totalCents,
            String content) {
        jdbc.update(
                """
                INSERT INTO cash_machine.receipt (
                    id, kind, deposit_id, requested_by, request_id,
                    created_at, total_cents, content
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                kind,
                depositId,
                requestedBy,
                requestId,
                Timestamp.from(now),
                totalCents,
                content);
    }

    public Optional<ReceiptView> findById(UUID id) {
        return read(RECEIPT_SELECT + " WHERE receipt.id = ?", id);
    }

    public Optional<ReceiptView> findByRequestId(UUID requestId) {
        return read(RECEIPT_SELECT + " WHERE receipt.request_id = ?", requestId);
    }

    public Optional<ReceiptView> findByDepositId(UUID depositId) {
        return read(RECEIPT_SELECT + " WHERE receipt.deposit_id = ?", depositId);
    }

    public List<ReceiptView> list(int page) {
        return jdbc.query(
                RECEIPT_SELECT
                        + """
                         ORDER BY receipt.created_at DESC, receipt.id DESC
                         LIMIT 50 OFFSET ?
                        """,
                this::map,
                Math.multiplyExact(page, 50));
    }

    public Optional<Attempt> findAttempt(UUID requestId) {
        return jdbc
                .query(
                        """
                        SELECT receipt_id, status, error_code
                        FROM cash_machine.print_attempt
                        WHERE id = ?
                        """,
                        (rs, row) ->
                                new Attempt(
                                        rs.getObject("receipt_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getString("error_code")),
                        requestId)
                .stream()
                .findFirst();
    }

    public void recordAttempt(
            UUID requestId, UUID receiptId, Instant now, String status, String errorCode) {
        jdbc.update(
                """
                INSERT INTO cash_machine.print_attempt (
                    id, receipt_id, requested_at, status, error_code
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                requestId,
                receiptId,
                Timestamp.from(now),
                status,
                errorCode);
    }

    public List<UUID> pendingDeposits() {
        return jdbc.query(
                """
                SELECT deposit.id
                FROM cash_machine.deposit deposit
                WHERE deposit.status <> 'ACTIVE'
                    AND deposit.receipt_required = TRUE
                    AND NOT EXISTS (
                        SELECT 1
                        FROM cash_machine.receipt receipt
                        WHERE receipt.deposit_id = deposit.id
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM cash_machine.deposit_banknote_operation operation
                        WHERE operation.deposit_id = deposit.id
                            AND operation.outcome = 'ACCEPTED'
                    )
                ORDER BY deposit.finished_at, deposit.id
                LIMIT 100
                """,
                (rs, row) -> rs.getObject("id", UUID.class));
    }

    public Optional<DepositReceipt> depositReceipt(UUID depositId) {
        return jdbc
                .query(
                        """
                        SELECT deposit.id,
                               deposit.user_id,
                               deposit.transaction_number,
                               deposit.finished_at,
                               account.display_name,
                               COUNT(*) AS banknote_count,
                               SUM(operation.denomination::bigint * 100) AS total_cents
                        FROM cash_machine.deposit deposit
                        JOIN cash_machine.user_account account
                            ON account.id = deposit.user_id
                        JOIN cash_machine.deposit_banknote_operation operation
                            ON operation.deposit_id = deposit.id
                            AND operation.outcome = 'ACCEPTED'
                        WHERE deposit.id = ?
                            AND deposit.status <> 'ACTIVE'
                            AND deposit.receipt_required = TRUE
                        GROUP BY deposit.id, account.display_name
                        """,
                        (rs, row) ->
                                new DepositReceipt(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("user_id", UUID.class),
                                        rs.getLong("transaction_number"),
                                        rs.getTimestamp("finished_at").toInstant(),
                                        rs.getString("display_name"),
                                        rs.getLong("banknote_count"),
                                        rs.getLong("total_cents")),
                        depositId)
                .stream()
                .findFirst();
    }

    public boolean issueAccess(UUID loginId, String tokenHash, Instant expiresAt) {
        return jdbc.update(
                        """
                UPDATE cash_machine.deposit
                SET receipt_access_hash = ?,
                    receipt_access_expires_at = ?
                WHERE login_id = ?
                    AND status = 'ACTIVE'
                """,
                        tokenHash,
                        Timestamp.from(expiresAt),
                        loginId)
                == 1;
    }

    public Optional<ReceiptView> findByAccess(String tokenHash, Instant now) {
        return read(
                RECEIPT_SELECT
                        + """
                         JOIN cash_machine.deposit deposit
                            ON deposit.id = receipt.deposit_id
                         WHERE deposit.receipt_access_hash = ?
                            AND deposit.receipt_access_expires_at > ?
                        """,
                tokenHash,
                Timestamp.from(now));
    }

    private Optional<ReceiptView> read(String sql, Object... arguments) {
        return jdbc.query(sql, this::map, arguments).stream().findFirst();
    }

    private ReceiptView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ReceiptView(
                rs.getObject("id", UUID.class),
                rs.getString("kind"),
                rs.getObject("requested_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("total_cents"),
                rs.getString("content"),
                rs.getString("print_status"),
                rs.getString("print_error"));
    }

    public record Attempt(UUID receiptId, String status, String errorCode) {}

    public record DepositReceipt(
            UUID depositId,
            UUID userId,
            long transactionNumber,
            Instant finishedAt,
            String displayName,
            long banknoteCount,
            long totalCents) {}
}
