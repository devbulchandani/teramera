package com.teramera.backend;

import com.teramera.backend.auth.OtpService;
import com.teramera.backend.auth.OtpService.OtpException;
import com.teramera.backend.db.LocalSqliteExecutor;
import com.teramera.backend.db.SchemaInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OtpFlowTest {

    private LocalSqliteExecutor db;
    private OtpService otp;
    private String requestId;
    private String code;

    @BeforeAll
    void setUp() {
        // pooled connections close per statement, so a temp FILE db (not memory)
        String path = System.getProperty("java.io.tmpdir") + "/otp-test-" + UUID.randomUUID() + ".db";
        db = new LocalSqliteExecutor(path);
        new SchemaInitializer().runSchema(db);
        otp = new OtpService(db);
    }

    @Test
    void issueThenVerifySucceedsAndConsumesCode() {
        var issued = otp.issue("+919876543210");
        assertTrue(issued.code().matches("\\d{6}"));
        otp.verify(issued.requestId(), issued.code());

        // second verify of same code fails (consumed)
        assertThrows(OtpException.class, () -> otp.verify(issued.requestId(), issued.code()));
    }

    @Test
    void wrongCodeThrowsButAllowsRetry() {
        var issued = otp.issue("+919876543211");
        assertThrows(OtpException.class, () -> otp.verify(issued.requestId(), "000000"));
        otp.verify(issued.requestId(), issued.code()); // still valid after one wrong attempt
    }

    @Test
    void reissueInvalidatesPreviousCode() {
        var first = otp.issue("+919876543212");
        var second = otp.issue("+919876543212");
        assertThrows(OtpException.class, () -> otp.verify(first.requestId(), first.code()));
        otp.verify(second.requestId(), second.code());
    }

    @Test
    void rateLimitBlocksAfterThreeRequests() {
        String phone = "+919876543213";
        otp.issue(phone);
        otp.issue(phone);
        otp.issue(phone);
        assertThrows(OtpException.class, () -> otp.issue(phone));
    }

    @Test
    void unknownRequestIdIsRejected() {
        assertThrows(OtpException.class, () -> otp.verify(UUID.randomUUID().toString(), "123456"));
    }
}
