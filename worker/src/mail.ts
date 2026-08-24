/**
 * Transactional email via whichever provider is configured:
 * 1. SMTP (Gmail app password — no domain needed)
 * 2. Resend API
 * 3. Log-only fallback
 */

interface MailEnv {
    RESEND_API_KEY?: string;
    INVITE_FROM_EMAIL?: string;
    SMTP_HOST?: string;
    SMTP_PORT?: string;
    SMTP_USER?: string;
    SMTP_PASS?: string;
}

export async function sendMail(
    env: MailEnv,
    to: string,
    subject: string,
    html: string,
    text?: string,
): Promise<"sent" | "logged"> {
    if (env.SMTP_USER && env.SMTP_PASS) {
        const { sendSmtpMail } = await import("./smtp");
        await sendSmtpMail(
            {
                host: env.SMTP_HOST || "smtp.gmail.com",
                port: Number(env.SMTP_PORT || 587),
                username: env.SMTP_USER,
                password: env.SMTP_PASS,
            },
            {
                from: `teramera <${env.SMTP_USER}>`,
                to,
                subject,
                html,
                text,
            },
        );
        return "sent";
    }
    if (env.RESEND_API_KEY) {
        const resp = await fetch("https://api.resend.com/emails", {
            method: "POST",
            headers: {
                Authorization: `Bearer ${env.RESEND_API_KEY}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                from: env.INVITE_FROM_EMAIL || "teramera <onboarding@resend.dev>",
                to,
                subject,
                html,
            }),
        });
        if (!resp.ok) throw new Error(`Resend failed: ${await resp.text()}`);
        return "sent";
    }
    console.log(`[EMAIL to ${to}] ${subject}\n${text ?? html}`);
    return "logged";
}
