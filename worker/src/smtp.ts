import { connect } from "cloudflare:sockets";

/***
 * Minimal SMTP client for Cloudflare Workers using the outbound TCP sockets API.
 * Supports STARTTLS (port 587) — e.g. Gmail's smtp.gmail.com with an App Password,
 * which works without owning a domain.
 */

interface SmtpConfig {
    host: string;
    port: number;
    username: string;
    password: string;
}

interface SmtpMessage {
    from: string; // "Name <email>" or bare email
    to: string;
    subject: string;
    html: string;
    text?: string;
}

class SmtpError extends Error {}

export async function sendSmtpMail(config: SmtpConfig, message: SmtpMessage): Promise<void> {
    const socket = connect(
        { hostname: config.host, port: config.port },
        { secureTransport: "starttls" },
    );
    let writer = socket.writable.getWriter();
    let reader = socket.readable.getReader();
    let secured = false;

    const enc = new TextEncoder();

    async function readReply(): Promise<string> {
        // Accumulate chunks until a terminator line: "250 ..." (space = last line of reply)
        let buffer = "";
        const deadline = Date.now() + 15_000;
        while (!(buffer.endsWith("\n") && /^\d{3} /m.test(buffer))) {
            if (Date.now() > deadline) {
                throw new SmtpError(`SMTP timeout waiting for reply (got: ${buffer.trim().slice(0, 80)})`);
            }
            const { value, done } = await reader.read();
            if (done) break;
            buffer += new TextDecoder().decode(value);
        }
        return buffer;
    }

    async function cmd(command?: string, expectCode = 250): Promise<string> {
        if (command !== undefined) await writer.write(enc.encode(command + "\r\n"));
        const reply = await readReply();
        const finalLine = reply.trim().split("\n").pop() ?? "";
        if (!finalLine.startsWith(String(expectCode))) {
            throw new SmtpError(`SMTP ${command?.split(" ")[0] ?? "reply"} failed: ${finalLine.slice(0, 120)}`);
        }
        return reply;
    }

    try {
        // greeting
        await readReply();

        await cmd("EHLO teramera.local", 250);

        // upgrade to TLS — locks MUST be released before startTls()
        await cmd("STARTTLS", 220);
        reader.releaseLock();
        writer.releaseLock();
        const securedSocket = socket.startTls();
        writer = securedSocket.writable.getWriter();
        reader = securedSocket.readable.getReader();
        secured = true;

        await cmd("EHLO teramera.local", 250);

        // AUTH PLAIN: base64("\0username\0password")
        const authPayload = btoa(`\u0000${config.username}\u0000${config.password}`);
        await cmd(`AUTH PLAIN ${authPayload}`, 235);

        const fromAddress = extractAddress(message.from);
        await cmd(`MAIL FROM:<${fromAddress}>`, 250);
        await cmd(`RCPT TO:<${message.to}>`, 250);
        await cmd("DATA", 354);

        const boundary = "teramera-" + crypto.randomUUID();
        const headers =
            `From: ${message.from}\r\n` +
            `To: <${message.to}>\r\n` +
            `Subject: =?UTF-8?B?${btoa(unescape(encodeURIComponent(message.subject)))}?=\r\n` +
            `MIME-Version: 1.0\r\n` +
            `Content-Type: multipart/alternative; boundary="${boundary}"\r\n` +
            `\r\n`;
        const textPart =
            `--${boundary}\r\nContent-Type: text/plain; charset="UTF-8"\r\n\r\n` +
            (message.text ?? stripHtml(message.html)) + "\r\n";
        const htmlPart =
            `--${boundary}\r\nContent-Type: text/html; charset="UTF-8"\r\n\r\n` +
            message.html + "\r\n";

        const body = headers + textPart + htmlPart + `--${boundary}--\r\n.`;
        await cmd(body, 250);
        await cmd("QUIT", 221);
    } finally {
        try {
            writer.releaseLock();
            reader.releaseLock();
            socket.close();
        } catch {
            // socket may already be closed
        }
        void secured;
    }
}

function extractAddress(fromHeader: string): string {
    const match = fromHeader.match(/<([^>]+)>/);
    return match ? match[1] : fromHeader;
}

function stripHtml(html: string): string {
    return html
        .replace(/<[^>]+>/g, "")
        .replace(/\s+/g, " ")
        .trim();
}
