import { Hono } from "hono";
import { cors } from "hono/cors";
import {
    hashOtp,
    issueRefreshToken,
    sha256Hex,
    signAccessToken,
    verifyAccessToken,
} from "./crypto";
import { computeSplit, simplifyDebts, SplitType } from "./split";
import { sendMail } from "./mail";
import { hashOtp as pbkdf2Hash } from "./crypto";

interface Env {
    DB: D1Database;
    JWT_SECRET: string;
    GOOGLE_CLIENT_ID?: string;
    EXPOSE_DEV_OTP?: string;
    RESEND_API_KEY?: string;
    INVITE_FROM_EMAIL?: string;
    APP_BASE_URL?: string;
    APK_DOWNLOAD_URL?: string;
    SMTP_HOST?: string;
    SMTP_PORT?: string;
    SMTP_USER?: string;
    SMTP_PASS?: string;
    APP_VERSION_CODE?: string;
    APP_VERSION_NAME?: string;
    FCM_PROJECT_ID?: string;
    FCM_CLIENT_EMAIL?: string;
    FCM_PRIVATE_KEY?: string;
}

type AppEnv = { Bindings: Env; Variables: { userId: string } };

const app = new Hono<AppEnv>();

// ---------- constants ----------

const OTP_TTL_MILLIS = 5 * 60 * 1000;
const OTP_MAX_ATTEMPTS = 5;
const OTP_WINDOW_MILLIS = 10 * 60 * 1000;
const OTP_MAX_REQUESTS = 3;
const ACCESS_TTL_MINUTES = 15;

const METHODS = new Set(["UPI", "CASH", "BANK"]);

function jwtDeps(env: Env) {
    if (!env.JWT_SECRET) throw new HttpError(500, "Server misconfigured: JWT_SECRET not set");
    return {
        secret: env.JWT_SECRET,
        accessTtlMinutes: ACCESS_TTL_MINUTES,
        refreshTtlDays: 30,
    };
}

/** Cryptographically secure 6-digit code. */
function generateOtp(): string {
    const buf = new Uint32Array(1);
    crypto.getRandomValues(buf);
    return String(buf[0] % 1_000_000).padStart(6, "0");
}

type Row = Record<string, any>;

// ---------- auth middleware ----------

app.use("*", cors());

app.use(async (c, next) => {
    if (c.req.path.startsWith("/auth/") || c.req.path === "/" || c.req.path.startsWith("/invite/") || c.req.path === "/app/version") return next();
    const header = c.req.header("Authorization");
    if (!header?.startsWith("Bearer ")) {
        return c.json({ message: "unauthorized" }, 401);
    }
    const parsed = await verifyAccessToken(jwtDeps(c.env), header.slice(7));
    if (!parsed) return c.json({ message: "unauthorized" }, 401);
    c.set("userId", parsed.sub);
    await next();
});

app.get("/", (c) => c.json({ name: "teramera-api", status: "ok" }));

/** Current APK version — the app polls this on launch to offer in-app updates. */
app.get("/app/version", (c) => {
    const origin = c.env.APP_BASE_URL || `https://${c.req.header("host")}`;
    return c.json({
        versionCode: Number(c.env.APP_VERSION_CODE || 1),
        versionName: c.env.APP_VERSION_NAME || "1.0",
        apkUrl: `${origin}/teramera.apk`,
    });
});

// ---------- helpers ----------

async function rows<T = Row>(c: any, sql: string, ...params: unknown[]): Promise<T[]> {
    const { results } = await c.env.DB.prepare(sql).bind(...params).all();
    return (results ?? []) as T[];
}

async function run(c: any, sql: string, ...params: unknown[]) {
    await c.env.DB.prepare(sql).bind(...params).run();
}

function badRequest(message: string) {
    throw new HttpError(400, message);
}

class HttpError extends Error {
    constructor(public status: number, message: string) {
        super(message);
    }
}

app.onError((err, c) => {
    if (err instanceof HttpError) return c.json({ message: err.message }, err.status as any);
    console.error(err);
    return c.json({ message: "Internal server error" }, 500);
});

interface UserRow {
    id: string;
    phone: string | null;
    email: string | null;
    name: string | null;
    avatar_url: string | null;
    upi_id?: string | null;
    email_verified?: number;
}

async function issueTokens(c: any, user: UserRow) {
    const accessToken = await signAccessToken(jwtDeps(c.env), user.id);
    const refresh = await issueRefreshToken();
    await run(
        c,
        "INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked, created_at) VALUES (?, ?, ?, ?, 0, ?)",
        crypto.randomUUID(),
        user.id,
        refresh.hash,
        refresh.expiresAt,
        Date.now(),
    );
    return {
        accessToken,
        refreshToken: refresh.token,
        userId: user.id,
        phone: user.phone ?? "",
        email: user.email ?? "",
        name: user.name ?? "",
    };
}

// ---------- auth ----------

app.post("/auth/otp/request", async (c) => {
    const body = await c.req.json<{ phone: string }>().catch(() => null);
    const phone = normalizePhone(body?.phone ?? "");
    if (!phone) return jsonError(c, 400, "Phone must be in E.164 format, e.g. +919876543210");

    const now = Date.now();
    const recent = await rows<{ c: number }>(
        c,
        "SELECT COUNT(*) AS c FROM otp_requests WHERE phone = ? AND created_at > ?",
        phone, now - OTP_WINDOW_MILLIS,
    );
    if (recent[0].c >= OTP_MAX_REQUESTS) {
        return jsonError(c, 429, "Too many codes requested. Try again later.");
    }
    // invalidate previous unconsumed codes
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE phone = ? AND consumed = 0", phone);

    const code = generateOtp();
    const requestId = crypto.randomUUID();
    await run(
        c,
        "INSERT INTO otp_requests (id, phone, code_hash, expires_at, attempts, consumed, created_at) VALUES (?, ?, ?, ?, 0, 0, ?)",
        requestId, phone, await hashOtp(code), now + OTP_TTL_MILLIS, now,
    );

    // SMS gateway integration point — codes surface via devCode until a provider is wired.
    console.log(`[SMS to ${phone}] Your teramera code is ${code}`);

    const response: Record<string, unknown> = { requestId, expiresInSeconds: 300 };
    if (c.env.EXPOSE_DEV_OTP === "true") response.devCode = code;
    return c.json(response);
});

app.post("/auth/otp/verify", async (c) => {
    const body = await c.req.json<{ requestId: string; code: string }>();
    if (!body?.requestId || !body?.code) return jsonError(c, 400, "requestId and code are required");

    const now = Date.now();
    const [row] = await rows<Row>(c, "SELECT * FROM otp_requests WHERE id = ?", body.requestId);
    if (!row) return jsonError(c, 400, "Code request not found. Request a new code.");
    if (row.consumed === 1) return jsonError(c, 400, "Code already used. Request a new one.");
    if ((row.expires_at as number) < now) return jsonError(c, 400, "Code expired. Request a new one.");
    if ((row.attempts as number) >= OTP_MAX_ATTEMPTS) {
        return jsonError(c, 400, "Too many wrong attempts. Request a new code.");
    }
    if ((await hashOtp(body.code)) !== row.code_hash) {
        await run(c, "UPDATE otp_requests SET attempts = ? WHERE id = ?", row.attempts + 1, body.requestId);
        return jsonError(c, 400, "Incorrect code.");
    }
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE id = ?", body.requestId);

    const phone = row.phone as string;
    let users = await rows<UserRow>(c, "SELECT * FROM users WHERE phone = ?", phone);
    if (users.length === 0) {
        const id = crypto.randomUUID();
        await run(c, "INSERT INTO users (id, phone, created_at) VALUES (?, ?, ?)", id, phone, now);
        users = [{ id, phone, email: null, name: null, avatar_url: null }];
    }
    return c.json(await issueTokens(c, users[0]));
});

app.post("/auth/google", async (c) => {
    const body = await c.req.json<{ idToken: string }>();
    if (!body?.idToken) return jsonError(c, 400, "idToken is required");

    const claims = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${body.idToken}`)
        .then((r) => (r.ok ? r.json<any>() : null))
        .catch(() => null);
    if (!claims || claims.email_verified !== "true") {
        return jsonError(c, 401, "Invalid Google ID token");
    }
    if (!c.env.GOOGLE_CLIENT_ID || c.env.GOOGLE_CLIENT_ID !== claims.aud) {
        // without an exact audience match any Google-issued token for any app would work
        return jsonError(c, 401, "Invalid Google ID token audience");
    }
    if (Number(claims.exp) < Date.now() / 1000) return jsonError(c, 401, "Google ID token expired");

    let users = await rows<UserRow>(c, "SELECT * FROM users WHERE email = ?", claims.email);
    if (users.length === 0) {
        const id = crypto.randomUUID();
        await run(
            c,
            "INSERT INTO users (id, email, name, avatar_url, created_at) VALUES (?, ?, ?, ?, ?)",
            id, claims.email, claims.name ?? null, claims.picture ?? null, Date.now(),
        );
        users = [{ id, phone: null, email: claims.email, name: claims.name ?? null, avatar_url: claims.picture ?? null }];
    }
    return c.json(await issueTokens(c, users[0]));
});

// ---------- email + password auth ----------

function validEmail(email: string): boolean {
    return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);
}

app.post("/auth/email/check", async (c) => {
    const body = await c.req.json<{ email?: string }>();
    const email = (body.email ?? "").trim().toLowerCase();
    if (!validEmail(email)) return jsonError(c, 400, "Valid email required");
    const [user] = await rows<Row>(c, "SELECT email_verified FROM users WHERE email = ?", email);
    return c.json({ exists: !!user, verified: !!user && user.email_verified === 1 });
});

app.post("/auth/email/register", async (c) => {
    const body = await c.req.json<{ email?: string; password?: string }>();
    const email = (body.email ?? "").trim().toLowerCase();
    const password = body.password ?? "";
    if (!validEmail(email)) return jsonError(c, 400, "Valid email required");
    if (password.length < 8) return jsonError(c, 400, "Password must be at least 8 characters");

    let users = await rows<UserRow>(c, "SELECT * FROM users WHERE email = ?", email);
    if (users.length > 0 && users[0].email_verified === 1) {
        return jsonError(c, 400, "Account already exists — sign in instead");
    }

    const passwordHash = await pbkdf2Hash(password);
    if (users.length === 0) {
        const id = crypto.randomUUID();
        await run(
            c,
            "INSERT INTO users (id, email, password_hash, email_verified, created_at) VALUES (?, ?, ?, 0, ?)",
            id, email, passwordHash, Date.now(),
        );
        users = [{ id, phone: null, email, name: null, avatar_url: null }];
    } else {
        // unverified account re-registering: update their password
        await run(c, "UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, users[0].id);
    }

    // email verification OTP — throttled like phone codes
    const now = Date.now();
    const recentReg = await rows<{ c: number }>(
        c,
        "SELECT COUNT(*) AS c FROM otp_requests WHERE phone = ? AND created_at > ?",
        email, now - OTP_WINDOW_MILLIS,
    );
    if (recentReg[0].c >= OTP_MAX_REQUESTS) {
        return jsonError(c, 429, "Too many codes requested. Try again later.");
    }
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE phone = ? AND consumed = 0", email);
    const code = generateOtp();
    const requestId = crypto.randomUUID();
    await run(
        c,
        "INSERT INTO otp_requests (id, phone, code_hash, expires_at, attempts, consumed, created_at) VALUES (?, ?, ?, ?, 0, 0, ?)",
        requestId, email, await hashOtp(code), now + OTP_TTL_MILLIS, now,
    );
    const status = await sendMail(
        c.env, email,
        "Your teramera verification code",
        `<p>Your teramera code is <b style="font-size:24px;letter-spacing:4px">${code}</b>. It expires in 5 minutes.</p>`,
        `Your teramera code is ${code}. It expires in 5 minutes.`,
    );

    // codes are NEVER returned to the client unless the operator explicitly
    // opts in via EXPOSE_DEV_OTP — mail-send failure must not fail auth open
    const response: Record<string, unknown> = { requestId };
    if (status !== "sent" && c.env.EXPOSE_DEV_OTP !== "true") {
        console.error(`mail delivery failed (${status}) for sign-in code`);
    }
    if (c.env.EXPOSE_DEV_OTP === "true") response.devCode = code;
    return c.json(response);
});

app.post("/auth/email/login", async (c) => {
    const body = await c.req.json<{ email?: string; password?: string }>();
    const email = (body.email ?? "").trim().toLowerCase();
    const [user] = await rows<Row>(
        c,
        "SELECT * FROM users WHERE email = ? AND password_hash IS NOT NULL",
        email,
    );
    if (!user || (await pbkdf2Hash(body.password ?? "")) !== user.password_hash) {
        return jsonError(c, 401, "Incorrect email or password");
    }
    if (user.email_verified !== 1) {
        return c.json({ message: "Email not verified yet", code: "UNVERIFIED" }, 403);
    }
    return c.json(await issueTokens(c, user as UserRow));
});

/** Passwordless login OR re-verification via emailed code. */
app.post("/auth/email/otp", async (c) => {
    const body = await c.req.json<{ email?: string }>();
    const email = (body.email ?? "").trim().toLowerCase();
    if (!validEmail(email)) return jsonError(c, 400, "Valid email required");

    const [user] = await rows<Row>(c, "SELECT * FROM users WHERE email = ?", email);
    if (!user) return jsonError(c, 404, "No teramera account with that email");

    const now = Date.now();
    const recentOtp = await rows<{ c: number }>(
        c,
        "SELECT COUNT(*) AS c FROM otp_requests WHERE phone = ? AND created_at > ?",
        email, now - OTP_WINDOW_MILLIS,
    );
    if (recentOtp[0].c >= OTP_MAX_REQUESTS) {
        return jsonError(c, 429, "Too many codes requested. Try again later.");
    }
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE phone = ? AND consumed = 0", email);
    const code = generateOtp();
    const requestId = crypto.randomUUID();
    await run(
        c,
        "INSERT INTO otp_requests (id, phone, code_hash, expires_at, attempts, consumed, created_at) VALUES (?, ?, ?, ?, 0, 0, ?)",
        requestId, email, await hashOtp(code), now + OTP_TTL_MILLIS, now,
    );
    const status = await sendMail(
        c.env, email,
        "Your teramera sign-in code",
        `<p>Your teramera code is <b style="font-size:24px;letter-spacing:4px">${code}</b>. It expires in 5 minutes.</p>`,
        `Your teramera code is ${code}. It expires in 5 minutes.`,
    );

    // codes are NEVER returned to the client unless the operator explicitly
    // opts in via EXPOSE_DEV_OTP — mail-send failure must not fail auth open
    const response: Record<string, unknown> = { requestId };
    if (status !== "sent" && c.env.EXPOSE_DEV_OTP !== "true") {
        console.error(`mail delivery failed (${status}) for sign-in code`);
    }
    if (c.env.EXPOSE_DEV_OTP === "true") response.devCode = code;
    return c.json(response);
});

app.post("/auth/email/verify", async (c) => {
    const body = await c.req.json<{ requestId?: string; code?: string }>();
    if (!body.requestId || !body.code) return jsonError(c, 400, "requestId and code are required");
    const now = Date.now();
    const [row] = await rows<Row>(c, "SELECT * FROM otp_requests WHERE id = ?", body.requestId);
    if (!row || row.consumed === 1) return jsonError(c, 400, "Code already used. Request a new one.");
    if ((row.expires_at as number) < now) return jsonError(c, 400, "Code expired. Request a new one.");
    if ((row.attempts as number) >= OTP_MAX_ATTEMPTS) {
        return jsonError(c, 400, "Too many wrong attempts. Request a new code.");
    }
    if ((await hashOtp(body.code)) !== row.code_hash) {
        await run(c, "UPDATE otp_requests SET attempts = ? WHERE id = ?", row.attempts + 1, body.requestId);
        return jsonError(c, 400, "Incorrect code.");
    }
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE id = ?", body.requestId);

    const email = row.phone as string;
    await run(c, "UPDATE users SET email_verified = 1 WHERE email = ?", email);
    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE email = ?", email);
    return c.json(await issueTokens(c, user));
});

app.post("/auth/refresh", async (c) => {
    const body = await c.req.json<{ refreshToken: string }>();
    if (!body?.refreshToken) return jsonError(c, 400, "refreshToken is required");
    const hash = await sha256Hex(body.refreshToken);
    const [row] = await rows<Row>(
        c,
        "SELECT * FROM refresh_tokens WHERE token_hash = ? AND revoked = 0",
        hash,
    );
    if (!row || (row.expires_at as number) < Date.now()) {
        return jsonError(c, 401, "Invalid or expired refresh token");
    }
    await run(c, "UPDATE refresh_tokens SET revoked = 1 WHERE id = ?", row.id);

    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", row.user_id);
    if (!user) return jsonError(c, 401, "User no longer exists");
    return c.json(await issueTokens(c, user));
});

app.post("/auth/logout", async (c) => {
    const body = await c.req.json<{ refreshToken?: string }>();
    if (body?.refreshToken) {
        const hash = await sha256Hex(body.refreshToken);
        await run(c, "UPDATE refresh_tokens SET revoked = 1 WHERE token_hash = ?", hash);
    }
    return c.body(null, 204);
});

// ---------- me ----------

app.get("/me", async (c) => {
    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", c.get("userId"));
    if (!user) return jsonError(c, 404, "User not found");
    return c.json({
        id: user.id,
        phone: user.phone ?? "",
        email: user.email ?? "",
        name: user.name ?? "",
        upiId: user.upi_id ?? "",
    });
});

function validUpiId(upi: string): boolean {
    return /^[\w.\-]{2,64}@[a-zA-Z]{2,32}$/.test(upi);
}

app.patch("/me", async (c) => {
    const body = await c.req.json<{ name?: string; upiId?: string }>();
    const updates: string[] = [];
    const params: unknown[] = [];

    if (body.name !== undefined) {
        const name = body.name.trim();
        if (name.length < 1 || name.length > 60) return jsonError(c, 400, "Name must be 1-60 characters");
        updates.push("name = ?");
        params.push(name);
    }
    if (body.upiId !== undefined) {
        const upi = body.upiId.trim();
        if (upi.length > 0 && !validUpiId(upi)) return jsonError(c, 400, "UPI ID must look like name@bank");
        updates.push("upi_id = ?");
        params.push(upi || null);
    }
    if (updates.length === 0) return jsonError(c, 400, "Nothing to update");

    params.push(c.get("userId"));
    await run(c, `UPDATE users SET ${updates.join(", ")} WHERE id = ?`, ...params);
    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", c.get("userId"));
    return c.json({ id: user.id, phone: user.phone ?? "", email: user.email ?? "", name: user.name ?? "", upiId: user.upi_id ?? "" });
});

// ---------- groups ----------

interface GroupRow {
    id: string;
    name: string;
    currency: string;
    created_by: string;
    created_at: number;
}

async function requireMember(c: any, groupId: string, userId: string) {
    const m = await rows(c, "SELECT 1 AS m FROM memberships WHERE group_id = ? AND user_id = ?", groupId, userId);
    if (m.length === 0) throw new HttpError(403, "Not a member of this group");
}

app.post("/groups", async (c) => {
    const body = await c.req.json<{ name?: string; currency?: string; memberUserIds?: string[] }>();
    if (!body.name?.trim()) return jsonError(c, 400, "name is required");

    const id = crypto.randomUUID();
    await run(
        c,
        "INSERT INTO groups (id, name, currency, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
        id, body.name.trim(), body.currency ?? "INR", c.get("userId"), Date.now(),
    );
    await run(c, "INSERT INTO memberships (group_id, user_id, role) VALUES (?, ?, 'member')", id, c.get("userId"));

    for (const memberId of body.memberUserIds ?? []) {
        const exists = await rows(c, "SELECT 1 AS m FROM users WHERE id = ?", memberId);
        if (exists.length === 0) return jsonError(c, 400, `Unknown member: ${memberId}`);
        await run(
            c,
            "INSERT OR IGNORE INTO memberships (group_id, user_id, role) VALUES (?, ?, 'member')",
            id, memberId,
        );
    }
    return c.json({ id, name: body.name.trim(), currency: body.currency ?? "INR" });
});

/** Find a teramera user by phone — how friends discover each other. */
app.get("/users/find", async (c) => {
    const phone = c.req.query("phone");
    const email = c.req.query("email");
    let user: UserRow | undefined;
    if (email) {
        [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE email = ?", email.trim().toLowerCase());
        if (!user) return jsonError(c, 404, "No teramera user with that email yet");
    } else {
        const normalized = normalizePhone(phone ?? "");
        if (!normalized) return jsonError(c, 400, "Provide a valid +E.164 phone or an email");
        [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE phone = ?", normalized);
        if (!user) return jsonError(c, 404, "No teramera user with that number yet");
    }
    return c.json({
        id: user.id,
        name: user.name ?? "",
        phone: user.phone ?? "",
        email: user.email ?? "",
    });
});

/** Caller joins a group themselves (invite links). */
app.post("/groups/:groupId/join", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    const [group] = await rows<GroupRow>(c, "SELECT id FROM groups WHERE id = ?", groupId);
    if (!group) return jsonError(c, 404, "Group not found");
    await run(
        c,
        "INSERT OR IGNORE INTO memberships (group_id, user_id, role) VALUES (?, ?, 'member')",
        groupId, userId,
    );
    return c.json({ status: "joined" });
});

/**
 * Email invite. Sends via Resend when RESEND_API_KEY is set; otherwise logs.
 * The invite link opens /invite/:id — a landing page that deep-links into the
 * app and offers the APK download as fallback.
 */
app.post("/groups/:groupId/invite-email", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const body = await c.req.json<{ email?: string }>();
    if (!body.email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(body.email)) {
        return jsonError(c, 400, "Valid email required");
    }
    const [inviter] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", userId);
    const [group] = await rows<GroupRow>(c, "SELECT name FROM groups WHERE id = ?", groupId);
    const link = `${c.env.APP_BASE_URL ?? `https://${c.req.header("host")}`}/invite/${groupId}`;
    const inviterName = inviter?.name ?? "A friend";
    const html = `
      <p>${inviterName} added you to <b>${group.name}</b> on teramera — split expenses with friends.</p>
      <p><a href="${link}">Open your invite</a> to join the group${body.email.includes("@") ? "" : ""}.</p>
      <p>If you don't have the app yet, that page will get you set up first.</p>`;

    const subject = `${inviterName} invited you to ${group.name} on teramera`;
    try {
        await sendMail(c.env, body.email, subject, html,
            `${inviterName} invited you to ${group.name} on teramera. Open your invite link: ${link}`);
        return c.json({ status: "sent" });
    } catch (err) {
        console.error("Invite email failed:", err);
        return jsonError(c, 502, "Invite email failed to send");
    }
});

/** Public landing for invite links. */
app.get("/invite/:groupId", async (c) => {
    const groupId = c.req.param("groupId");
    const origin = c.env.APP_BASE_URL || `https://${c.req.header("host")}`;
    const apkUrl = c.env.APK_DOWNLOAD_URL || `${origin}/teramera.apk`;
    const html = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>teramera — group invite</title>
<style>
 *{box-sizing:border-box}
 body{font-family:-apple-system,system-ui,'Segoe UI',sans-serif;background:#FBF9F4;color:#251C15;margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
 .card{max-width:380px;width:100%;background:#F5F1E8;border-radius:24px;padding:36px 28px;text-align:center;box-shadow:0 6px 24px rgba(37,28,21,.08)}
 .logo{font-size:40px;font-weight:800;letter-spacing:-2px}
 .tere{color:#825EA9}.mera{color:#00848B}
 h1{font-size:22px;margin:18px 0 8px;font-weight:700}
 p{font-size:15px;color:#5B5046;line-height:1.5;margin:0 0 24px}
 .btn{display:block;padding:16px;border-radius:16px;text-decoration:none;font-weight:700;font-size:15.5px;margin-bottom:12px}
 .btn-primary{background:#00848B;color:#fff}
 .btn-secondary{background:#ECE7DB;color:#251C15}
 small{display:block;margin-top:16px;font-size:12px;color:#877F75;line-height:1.5}
 .badge{display:inline-block;background:#D2EEF0;color:#00565b;font-size:11.5px;font-weight:700;padding:4px 12px;border-radius:999px;margin-bottom:14px}
</style></head>
<body><div class="card">
 <div class="badge">GROUP INVITE</div>
 <div class="logo"><span class="tere">tere</span><span class="mera">mera</span></div>
 <h1>You've been invited to a group</h1>
 <p>Split expenses with friends fairly — add expenses your way, settle up in the fewest payments possible.</p>
 <a class="btn btn-primary" href="intent://invite/${groupId}#Intent;scheme=teramera;package=com.example.teramera;S.browser_fallback_url=${encodeURIComponent(apkUrl)};end">Open in app &amp; join</a>
 <a class="btn btn-secondary" href="${apkUrl}">Download the app first</a>
 <small>Opening the link above joins this group automatically after you sign in.</small>
</div></body></html>`;
    return c.html(html);
});

/** Add an existing user to a group. */
app.post("/groups/:groupId/members", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const body = await c.req.json<{ userId?: string }>();
    if (!body.userId) return jsonError(c, 400, "userId is required");
    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", body.userId);
    if (!user) return jsonError(c, 404, "User not found");

    await run(
        c,
        "INSERT OR IGNORE INTO memberships (group_id, user_id, role) VALUES (?, ?, 'member')",
        groupId, body.userId,
    );
    return c.json({ status: "added", name: user.name ?? "" });
});

app.get("/groups", async (c) => {
    const userId = c.get("userId");
    const list = await rows<GroupRow>(
        c,
        `SELECT g.* FROM groups g JOIN memberships m ON m.group_id = g.id
         WHERE m.user_id = ? ORDER BY g.created_at DESC`,
        userId,
    );
    if (list.length === 0) return c.json([]);

    // batched: one query each for expenses, shares and settlements across all my groups
    const groupIds = list.map((g) => g.id);
    const expenses = await rows<Row>(
        c,
        `SELECT group_id, id, paid_by_user_id, amount_minor FROM expenses
         WHERE group_id IN (${groupIds.map(() => "?").join(",")})`,
        ...groupIds,
    );
    const shares = expenses.length > 0
        ? await rows<Row>(
              c,
              `SELECT s.expense_id, s.user_id, s.share_amount_minor, e.group_id FROM expense_shares s
               JOIN expenses e ON e.id = s.expense_id
               WHERE e.group_id IN (${groupIds.map(() => "?").join(",")})`,
              ...groupIds,
          )
        : [];
    const settlements = await rows<Row>(
        c,
        `SELECT group_id, payer_user_id, paid_to_user_id, amount_minor FROM settlements
         WHERE group_id IN (${groupIds.map(() => "?").join(",")})`,
        ...groupIds,
    );

    const spentByGroup = new Map<string, number>();
    const netByGroup = new Map<string, Map<string, number>>();
    const expenseById = new Map(expenses.map((e) => [e.id, e]));
    for (const e of expenses) {
        spentByGroup.set(e.group_id, (spentByGroup.get(e.group_id) ?? 0) + e.amount_minor);
        const net = netByGroup.get(e.group_id) ?? new Map<string, number>();
        net.set(e.paid_by_user_id, (net.get(e.paid_by_user_id) ?? 0) + e.amount_minor);
        netByGroup.set(e.group_id, net);
    }
    for (const s of shares) {
        const e = expenseById.get(s.expense_id);
        if (!e) continue;
        const net = netByGroup.get(e.group_id);
        net?.set(s.user_id, (net.get(s.user_id) ?? 0) - s.share_amount_minor);
    }
    for (const s of settlements) {
        const net = netByGroup.get(s.group_id);
        if (!net) continue;
        net.set(s.payer_user_id, (net.get(s.payer_user_id) ?? 0) + s.amount_minor);
        net.set(s.paid_to_user_id, (net.get(s.paid_to_user_id) ?? 0) - s.amount_minor);
    }

    return c.json(list.map((group) => ({
        id: group.id,
        name: group.name,
        currency: group.currency,
        totalSpentMinor: spentByGroup.get(group.id) ?? 0,
        netForMeMinor: netByGroup.get(group.id)?.get(userId) ?? 0,
    })));
});

/** Per-member overpaid-net inside one group (positive = is owed). */
async function groupBalances(c: any, groupId: string): Promise<{ userId: string; netMinor: number }[]> {
    const net = new Map<string, number>();
    const expenses = await rows<Row>(c, "SELECT id, paid_by_user_id, amount_minor FROM expenses WHERE group_id = ?", groupId);
    if (expenses.length > 0) {
        const shares = await rows<Row>(
            c,
            `SELECT expense_id, user_id, share_amount_minor FROM expense_shares
             WHERE expense_id IN (${expenses.map(() => "?").join(",")})`,
            ...expenses.map((e) => e.id),
        );
        const paidBy = new Map(expenses.map((e) => [e.id, e]));
        for (const share of shares) {
            const expense = paidBy.get(share.expense_id);
            if (!expense) continue;
            net.set(expense.paid_by_user_id, (net.get(expense.paid_by_user_id) ?? 0) + expense.amount_minor);
            net.set(share.user_id, (net.get(share.user_id) ?? 0) - share.share_amount_minor);
        }
    }
    const settlements = await rows<Row>(c, "SELECT payer_user_id, paid_to_user_id, amount_minor FROM settlements WHERE group_id = ?", groupId);
    for (const s of settlements) {
        net.set(s.payer_user_id, (net.get(s.payer_user_id) ?? 0) + s.amount_minor);
        net.set(s.paid_to_user_id, (net.get(s.paid_to_user_id) ?? 0) - s.amount_minor);
    }
    return [...net.entries()].map(([userId, netMinor]) => ({ userId, netMinor }));
}

app.get("/groups/:groupId/detail", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const [group] = await rows<GroupRow>(c, "SELECT * FROM groups WHERE id = ?", groupId);
    if (!group) return jsonError(c, 404, "Group not found");

    // batched: members+users in one join, all shares in one IN query
    const memberRows = await rows<Row>(
        c,
        `SELECT u.id, u.name, u.upi_id FROM memberships m
         JOIN users u ON u.id = m.user_id
         WHERE m.group_id = ?`,
        groupId,
    );
    const members = memberRows.map((u) => ({
        id: u.id,
        name: u.name ?? "?",
        isSelf: u.id === userId,
        upiId: u.upi_id ?? "",
    }));
    const nameById = new Map(memberRows.map((u) => [u.id, u.name ?? "?"]));

    const expenses = await rows<Row>(
        c,
        "SELECT * FROM expenses WHERE group_id = ? ORDER BY created_at DESC",
        groupId,
    );
    const shares = expenses.length > 0
        ? await rows<Row>(
              c,
              `SELECT expense_id, user_id, share_amount_minor FROM expense_shares
               WHERE expense_id IN (${expenses.map(() => "?").join(",")})`,
              ...expenses.map((e) => e.id),
          )
        : [];
    const sharesByExpense = new Map<string, Row[]>();
    for (const s of shares) {
        const list = sharesByExpense.get(s.expense_id) ?? [];
        list.push(s);
        sharesByExpense.set(s.expense_id, list);
    }

    const expenseList = expenses.map((expense) => {
        const expenseShares = sharesByExpense.get(expense.id) ?? [];
        return {
            id: expense.id,
            title: expense.title,
            paidByUserId: expense.paid_by_user_id,
            amountMinor: expense.amount_minor,
            myShareMinor: expenseShares.find((s) => s.user_id === userId)?.share_amount_minor ?? 0,
            participantCount: expenseShares.length,
            createdAt: expense.created_at,
        };
    });

    const balances = await groupBalances(c, groupId);
    const simplified = simplifyDebts(new Map(balances.map((b) => [b.userId, b.netMinor])));
    const namedDebts = simplified.transfers.map((t) => ({
        fromUserId: t.fromUserId,
        fromName: nameById.get(t.fromUserId) ?? "?",
        toUserId: t.toUserId,
        toName: nameById.get(t.toUserId) ?? "?",
        amountMinor: t.amountMinor,
    }));
    const namedBalances = balances.map((b) => ({
        userId: b.userId,
        name: nameById.get(b.userId) ?? "?",
        netMinor: b.netMinor,
        upiId: memberRows.find((u) => u.id === b.userId)?.upi_id ?? "",
    }));

    return c.json({
        id: group.id,
        name: group.name,
        currency: group.currency,
        totalSpentMinor: expenses.reduce((acc, e) => acc + e.amount_minor, 0),
        members,
        expenses: expenseList,
        balances: namedBalances,
        simplifiedDebts: namedDebts,
    });
});

app.get("/groups/:groupId/expenses", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);
    const expenses = await rows<Row>(
        c,
        "SELECT * FROM expenses WHERE group_id = ? ORDER BY created_at DESC",
        groupId,
    );
    const result = [];
    for (const expense of expenses) {
        const shares = await rows<Row>(c, "SELECT * FROM expense_shares WHERE expense_id = ?", expense.id);
        result.push({
            id: expense.id,
            title: expense.title,
            paidByUserId: expense.paid_by_user_id,
            amountMinor: expense.amount_minor,
            myShareMinor: shares.find((s) => s.user_id === userId)?.share_amount_minor ?? 0,
            createdAt: expense.created_at,
        });
    }
    return c.json(result);
});

// ---------- expenses ----------

app.post("/expenses", async (c) => {
    const userId = c.get("userId");
    const body = await c.req.json<{
        groupId?: string | null;
        title?: string;
        amountMinor?: number;
        paidByUserId?: string;
        splitType?: SplitType;
        participants?: Record<string, number> | string[];
        payments?: { userId: string; amountMinor: number }[];
        currency?: string;
        fxRateToGroup?: number;
    }>();

    if (!body.title?.trim()) return jsonError(c, 400, "title is required");
    if (!body.amountMinor || body.amountMinor <= 0) return jsonError(c, 400, "Amount must be positive");
    const ALLOWED_TYPES: SplitType[] = ["EQUAL", "EXACT", "PERCENT", "SHARES"];
    if (!body.splitType || !ALLOWED_TYPES.includes(body.splitType)) {
        return jsonError(c, 400, "splitType must be EQUAL, EXACT, PERCENT or SHARES");
    }
    if (body.groupId) await requireMember(c, body.groupId, userId);

    // ---- payers: payments[] wins over legacy single paidByUserId ----
    let payments = (body.payments ?? []).filter((p) => p.userId && p.amountMinor > 0);
    if (payments.length === 0) {
        if (!body.paidByUserId) return jsonError(c, 400, "paidByUserId or payments is required");
        payments = [{ userId: body.paidByUserId, amountMinor: body.amountMinor }];
    }
    const paymentTotal = payments.reduce((acc, p) => acc + p.amountMinor, 0);
    if (paymentTotal !== body.amountMinor) {
        return jsonError(c, 400, "Payer amounts do not sum to the total");
    }

    // ---- participants: array of ids | legacy map | default all group members ----
    let participantIds: string[];
    if (Array.isArray(body.participants)) {
        participantIds = body.participants.filter(Boolean);
    } else if (body.participants && Object.keys(body.participants).length > 0) {
        participantIds = [...Object.keys(body.participants), ...payments.map((p) => p.userId)];
    } else if (body.groupId) {
        participantIds = (
            await rows<{ user_id: string }>(c, "SELECT user_id FROM memberships WHERE group_id = ?", body.groupId)
        ).map((r) => r.user_id);
    } else {
        return jsonError(c, 400, "At least two people required");
    }
    participantIds = [...new Set(participantIds)];
    if (participantIds.length < 2) return jsonError(c, 400, "At least two people required");

    // everyone involved must exist
    for (const pid of [...new Set([...participantIds, ...payments.map((p) => p.userId)])]) {
        const exists = await rows(c, "SELECT 1 AS m FROM users WHERE id = ?", pid);
        if (exists.length === 0) return jsonError(c, 400, `Unknown user: ${pid}`);
    }
    // all payers of a group expense must be members
    if (body.groupId) {
        const memberRows = await rows<{ user_id: string }>(
            c, "SELECT user_id FROM memberships WHERE group_id = ?", body.groupId,
        );
        const memberSet = new Set(memberRows.map((r) => r.user_id));
        for (const p of payments) {
            if (!memberSet.has(p.userId)) return jsonError(c, 403, `Payer is not a group member: ${p.userId}`);
        }
    }

    // ---- total share per participant, then allocate each payment pro-rata ----
    const rawValues: Record<string, number> =
        !Array.isArray(body.participants) && body.participants ? body.participants : {};
    const totalSplit = computeSplit({
        type: body.splitType,
        totalMinor: body.amountMinor,
        participants: participantIds,
        rawValues,
    });
    if (!totalSplit.ok) return jsonError(c, 400, totalSplit.reason);
    const totalShares = totalSplit.shares;

    function largestRemainderAllocate(bucket: number): Map<string, number> {
        const base = new Map<string, number>();
        let allocated = 0;
        for (const share of totalShares) {
            const v = Math.floor((bucket * share.amountMinor) / body.amountMinor!);
            base.set(share.userId, v);
            allocated += v;
        }
        let remainder = bucket - allocated;
        const order = [...totalShares].sort((a, b) =>
            ((bucket * b.amountMinor) % body.amountMinor!) - ((bucket * a.amountMinor) % body.amountMinor!),
        );
        for (const share of order) {
            if (remainder <= 0) break;
            base.set(share.userId, (base.get(share.userId) ?? 0) + 1);
            remainder--;
        }
        return base;
    }

    const createdAt = Date.now();
    // one shared parent id ties the per-payment rows of a logical expense together
    const parentId = crypto.randomUUID();
    const createdIds: string[] = [];
    for (const payment of payments) {
        const id = crypto.randomUUID();
        const allocation = payments.length === 1
            ? new Map(totalShares.map((s) => [s.userId, s.amountMinor]))
            : largestRemainderAllocate(payment.amountMinor);
        await run(
            c,
            `INSERT INTO expenses (id, group_id, paid_by_user_id, title, amount_minor, split_type, currency, fx_rate_to_group, created_at, parent_id)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            id, body.groupId ?? null, payment.userId, body.title.trim(),
            payment.amountMinor, body.splitType, body.currency ?? "INR",
            body.fxRateToGroup ?? 1.0, createdAt, parentId,
        );
        for (const [uid, minor] of allocation.entries()) {
            if (minor > 0) {
                await run(
                    c,
                    "INSERT INTO expense_shares (expense_id, user_id, share_amount_minor) VALUES (?, ?, ?)",
                    id, uid, minor,
                );
            }
        }
        createdIds.push(id);
    }
    // notify participants (except the payers, who did the adding) — best effort
    const actorIds = new Set(payments.map((p) => p.userId));
    if (c.env.FCM_PROJECT_ID && c.env.FCM_CLIENT_EMAIL && c.env.FCM_PRIVATE_KEY) {
        c.executionCtx.waitUntil((async () => {
            const [actor] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", payments[0].userId);
            let groupName: string | null = null;
            if (body.groupId) {
                const [g] = await rows<{ name: string }>(c, "SELECT name FROM groups WHERE id = ?", body.groupId);
                groupName = g?.name ?? null;
            }
            const title = body.title?.trim() ?? "";
            await pushToUsers(
                c,
                participantIds.filter((uid) => !actorIds.has(uid)),
                `${actor?.name ?? "Someone"} added an expense`,
                `“${title}”${groupName ? ` · ${groupName}` : ""} · ₹${((body.amountMinor ?? 0) / 100).toFixed(0)}`,
            );
        })());
    }
    return c.json({ id: createdIds[0], amountMinor: body.amountMinor, shareCount: participantIds.length });
});

// ---------- edit / delete expense ----------

app.patch("/expenses/:expenseId", async (c) => {
    const userId = c.get("userId");
    const expenseId = c.req.param("expenseId");
    const [expense] = await rows<Row>(c, "SELECT * FROM expenses WHERE id = ?", expenseId);
    if (!expense) return jsonError(c, 404, "Expense not found");

    if (expense.parent_id == null) return jsonError(c, 409, "Expense is missing its parent link and cannot be edited");
    if (expense.group_id) await requireMember(c, expense.group_id, userId);
    // all rows of a logical expense share an immutable parent_id
    const siblings = await rows<Row>(c, "SELECT * FROM expenses WHERE parent_id = ?", expense.parent_id);
    const [first] = await rows<Row>(
        c,
        "SELECT paid_by_user_id FROM expenses WHERE parent_id = ? ORDER BY rowid LIMIT 1",
        expense.parent_id,
    );
    if (first?.paid_by_user_id !== userId) return jsonError(c, 403, "Only the person who added this expense can edit it");

    const body = await c.req.json<{ title?: string; amountMinor?: number; participantIds?: string[] }>();
    const title = body.title?.trim() ?? expense.title;
    if (!title) return jsonError(c, 400, "title is required");
    const totalMinor = body.amountMinor ?? siblingTotal(siblings.length, expense.amount_minor);
    if (totalMinor <= 0) return jsonError(c, 400, "Amount must be positive");

    let participantIds: string[] | null = null;
    if (body.participantIds && body.participantIds.length > 0) {
        participantIds = [...new Set(body.participantIds.filter(Boolean))];
        for (const pid of participantIds) {
            const exists = await rows(c, "SELECT 1 AS m FROM users WHERE id = ?", pid);
            if (exists.length === 0) return jsonError(c, 400, `Unknown user: ${pid}`);
        }
    }

    // re-split EQUAL across participants (or keep existing shares when unchanged)
    let shares: { user_id: string; share_amount_minor: number }[];
    if (participantIds && participantIds.length >= 2) {
        const split = computeSplit({ type: "EQUAL", totalMinor, participants: participantIds, rawValues: {} });
        if (!split.ok) return jsonError(c, 400, split.reason);
        shares = split.shares.map((s) => ({ user_id: s.userId, share_amount_minor: s.amountMinor }));
    } else {
        const existing = await rows<Row>(c, "SELECT user_id, share_amount_minor FROM expense_shares WHERE expense_id = ?", expenseId);
        const oldTotal = existing.reduce((a, r) => a + r.share_amount_minor, 0);
        shares = existing.map((r) => ({ user_id: r.user_id, share_amount_minor: Math.floor((r.share_amount_minor * totalMinor) / oldTotal) }));
        // distribute rounding remainder to the first participant
        const diff = totalMinor - shares.reduce((a, s) => a + s.share_amount_minor, 0);
        if (shares.length > 0 && diff !== 0) shares[0].share_amount_minor += diff;
    }
    if (shares.length < 2) return jsonError(c, 400, "At least two people required");

    const perPayer = Math.floor(totalMinor / siblings.length);
    let remainder = totalMinor - perPayer * siblings.length;
    for (const sib of siblings) {
        const amount = perPayer + (remainder-- > 0 ? 1 : 0);
        await run(
            c,
            "UPDATE expenses SET title = ?, amount_minor = ? WHERE id = ?",
            title, amount, sib.id,
        );
        await run(c, "DELETE FROM expense_shares WHERE expense_id = ?", sib.id);
        for (const share of shares) {
            if (share.share_amount_minor > 0) {
                await run(
                    c,
                    "INSERT INTO expense_shares (expense_id, user_id, share_amount_minor) VALUES (?, ?, ?)",
                    sib.id, share.user_id, share.share_amount_minor,
                );
            }
        }
    }
    return c.json({ status: "updated", id: expenseId, amountMinor: totalMinor });
});

function siblingTotal(count: number, singleAmount: number): number {
    return count > 0 ? singleAmount * count : singleAmount;
}

app.delete("/expenses/:expenseId", async (c) => {
    const userId = c.get("userId");
    const expenseId = c.req.param("expenseId");
    const [expense] = await rows<Row>(c, "SELECT * FROM expenses WHERE id = ?", expenseId);
    if (!expense) return jsonError(c, 404, "Expense not found");

    if (expense.parent_id == null) return jsonError(c, 409, "Expense is missing its parent link and cannot be deleted");
    if (expense.group_id) await requireMember(c, expense.group_id, userId);
    const [creator] = await rows<Row>(
        c,
        "SELECT paid_by_user_id FROM expenses WHERE parent_id = ? ORDER BY rowid LIMIT 1",
        expense.parent_id,
    );
    if (creator?.paid_by_user_id !== userId) return jsonError(c, 403, "Only the person who added this expense can delete it");
    const siblings = await rows<Row>(c, "SELECT id FROM expenses WHERE parent_id = ?", expense.parent_id);

    for (const sib of siblings) {
        await run(c, "DELETE FROM expense_shares WHERE expense_id = ?", sib.id);
        await run(c, "DELETE FROM expenses WHERE id = ?", sib.id);
    }
    return c.json({ status: "deleted", id: expenseId });
});

// ---------- settlements ----------

app.post("/settlements", async (c) => {
    const userId = c.get("userId");
    const body = await c.req.json<{
        groupId?: string | null;
        payerUserId?: string;
        paidToUserId?: string;
        amountMinor?: number;
        method?: string;
    }>();

    if (!body.paidToUserId) return jsonError(c, 400, "paidToUserId is required");
    if (!body.amountMinor || body.amountMinor <= 0) return jsonError(c, 400, "Amount must be positive");
    if (!body.method || !METHODS.has(body.method.toUpperCase())) {
        return jsonError(c, 400, "Method must be UPI, CASH or BANK");
    }
    const payer = body.payerUserId || userId;
    if (payer !== userId && body.paidToUserId !== userId) {
        return jsonError(c, 403, "You must be part of the settlement");
    }
    if (body.groupId) await requireMember(c, body.groupId, userId);

    await run(
        c,
        `INSERT INTO settlements (id, group_id, payer_user_id, paid_to_user_id, amount_minor, method, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        crypto.randomUUID(), body.groupId ?? null, payer, body.paidToUserId,
        body.amountMinor, body.method.toUpperCase(), Date.now(),
    );
    return c.json({ status: "recorded", amountMinor: body.amountMinor });
});

app.get("/settlements/groups/:groupId", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);
    return c.json(await rows(c, "SELECT * FROM settlements WHERE group_id = ? ORDER BY created_at DESC", groupId));
});

// ---------- balances ----------

/** Friend-level nets across everything involving the caller. */
app.get("/balances", async (c) => {
    const selfId = c.get("userId");
    const net = new Map<string, number>();

    const expenses = await rows<Row>(
        c,
        `SELECT DISTINCT e.* FROM expenses e
         LEFT JOIN expense_shares s ON s.expense_id = e.id
         WHERE e.paid_by_user_id = ? OR s.user_id = ?`,
        selfId, selfId,
    );
    for (const expense of expenses) {
        const shares = await rows<Row>(c, "SELECT * FROM expense_shares WHERE expense_id = ?", expense.id);
        for (const share of shares) {
            if (expense.paid_by_user_id === selfId && share.user_id !== selfId) {
                net.set(share.user_id, (net.get(share.user_id) ?? 0) + share.share_amount_minor);
            } else if (expense.paid_by_user_id !== selfId && share.user_id === selfId) {
                net.set(expense.paid_by_user_id, (net.get(expense.paid_by_user_id) ?? 0) - share.share_amount_minor);
            }
        }
    }

    const settlements = await rows<Row>(
        c,
        "SELECT * FROM settlements WHERE payer_user_id = ? OR paid_to_user_id = ?",
        selfId, selfId,
    );
    for (const s of settlements) {
        if (s.payer_user_id !== selfId && s.paid_to_user_id === selfId) {
            net.set(s.payer_user_id, (net.get(s.payer_user_id) ?? 0) - s.amount_minor);
        } else if (s.payer_user_id === selfId && s.paid_to_user_id !== selfId) {
            net.set(s.paid_to_user_id, (net.get(s.paid_to_user_id) ?? 0) + s.amount_minor);
        }
    }

    // batched user lookup
    const uids = [...net.keys()];
    const userRows = uids.length > 0
        ? await rows<UserRow>(
              c,
              `SELECT id, name, upi_id FROM users WHERE id IN (${uids.map(() => "?").join(",")})`,
              ...uids,
          )
        : [];
    const userById = new Map(userRows.map((u) => [u.id, u]));
    return c.json(uids.map((uid) => ({
        userId: uid,
        name: userById.get(uid)?.name ?? "?",
        netMinor: net.get(uid)!,
        upiId: userById.get(uid)?.upi_id ?? "",
    })));
});

// ---------- activity feed ----------

/** Recent expenses + settlements across everything involving the caller. */
app.get("/activity", async (c) => {
    const selfId = c.get("userId");
    const limit = Math.min(Number(c.req.query("limit") ?? 50), 100);

    const expenses = await rows<Row>(
        c,
        `SELECT DISTINCT e.* FROM expenses e
         LEFT JOIN expense_shares s ON s.expense_id = e.id
         WHERE e.paid_by_user_id = ? OR s.user_id = ?
         ORDER BY e.created_at DESC LIMIT ?`,
        selfId, selfId, limit,
    );
    const expenseEvents = [];
    for (const expense of expenses) {
        const shares = await rows<Row>(c, "SELECT user_id, share_amount_minor FROM expense_shares WHERE expense_id = ?", expense.id);
        const [payer] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", expense.paid_by_user_id);
        let groupName: string | null = null;
        if (expense.group_id) {
            const [g] = await rows<{ name: string }>(c, "SELECT name FROM groups WHERE id = ?", expense.group_id);
            groupName = g?.name ?? null;
        }
        // multi-payer rows share one logical expense; show it once with its full amount
        const parentId = expense.parent_id ?? expense.id;
        const [agg] = await rows<{ n: number; t: number }>(
            c,
            "SELECT COUNT(*) AS n, SUM(amount_minor) AS t FROM expenses WHERE parent_id = ?",
            parentId,
        );
        const rowCount = agg?.n ?? 1;
        if (rowCount > 1) {
            const [first] = await rows<{ id: string }>(
                c,
                "SELECT id FROM expenses WHERE parent_id = ? ORDER BY rowid LIMIT 1",
                parentId,
            );
            if (first?.id !== expense.id) continue;
        }
        const logicalTotal = rowCount > 1 ? agg?.t ?? expense.amount_minor : expense.amount_minor;
        expenseEvents.push({
            type: "expense" as const,
            id: expense.id,
            title: expense.title,
            payerName: payer?.name ?? "?",
            paidBySelf: expense.paid_by_user_id === selfId,
            amountMinor: logicalTotal,
            myShareMinor: shares.find((s) => s.user_id === selfId)?.share_amount_minor ?? 0,
            groupName,
            participantCount: shares.length,
            createdAt: expense.created_at,
        });
    }

    const settlements = await rows<Row>(
        c,
        "SELECT * FROM settlements WHERE payer_user_id = ? OR paid_to_user_id = ? ORDER BY created_at DESC LIMIT ?",
        selfId, selfId, limit,
    );
    const settlementEvents = [];
    for (const s of settlements) {
        const [payer] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", s.payer_user_id);
        const [payee] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", s.paid_to_user_id);
        settlementEvents.push({
            type: "settlement" as const,
            id: s.id,
            payerName: payer?.name ?? "?",
            payeeName: payee?.name ?? "?",
            involvedSelf: true,
            amountMinor: s.amount_minor,
            methodLabel: String(s.method).toUpperCase(),
            createdAt: s.created_at,
        });
    }

    return c.json(
        [...expenseEvents, ...settlementEvents].sort((a, b) => b.createdAt - a.createdAt).slice(0, limit),
    );
});

// ---------- push notification devices ----------

app.post("/devices", async (c) => {
    const userId = c.get("userId");
    const body = await c.req.json<{ token?: string }>();
    if (!body.token) return jsonError(c, 400, "token is required");
    await run(
        c,
        // on conflict keep the original owner — FCM tokens are bearer credentials
        `INSERT INTO device_tokens (user_id, token, updated_at) VALUES (?, ?, ?)
         ON CONFLICT(token) DO UPDATE SET updated_at = excluded.updated_at`,
        userId, body.token, Date.now(),
    );
    return c.json({ status: "registered" });
});

app.delete("/devices", async (c) => {
    const userId = c.get("userId");
    const body = await c.req.json<{ token?: string }>().catch(() => ({ token: undefined }));
    await run(c, "DELETE FROM device_tokens WHERE token = ? AND user_id = ?", body.token ?? "", userId);
    return c.json({ status: "removed" });
});

// ---------- fcm (http v1) ----------

function pemToBytes(pem: string): Uint8Array {
    const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "")
        .replace(/-----END PRIVATE KEY-----/, "")
        .replace(/\s+/g, "");
    const binary = atob(b64);
    return Uint8Array.from(binary, (ch) => ch.charCodeAt(0));
}

async function fcmAccessToken(env: any): Promise<string | null> {
    try {
        if (!env.FCM_PROJECT_ID || !env.FCM_CLIENT_EMAIL || !env.FCM_PRIVATE_KEY) return null;
        const now = Math.floor(Date.now() / 1000);
        const enc = (o: unknown) =>
            btoa(JSON.stringify(o)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
        const input = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({
            iss: env.FCM_CLIENT_EMAIL,
            scope: "https://www.googleapis.com/auth/firebase.messaging",
            aud: "https://oauth2.googleapis.com/token",
            iat: now,
            exp: now + 3600,
        })}`;
        const key = await crypto.subtle.importKey(
            "pkcs8",
            pemToBytes(env.FCM_PRIVATE_KEY),
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
            false,
            ["sign"],
        );
        const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(input));
        const jwt = `${input}.${btoa(String.fromCharCode(...new Uint8Array(sig)))
            .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")}`;
        const res = await fetch("https://oauth2.googleapis.com/token", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
        });
        if (!res.ok) return null;
        return (await res.json<any>()).access_token ?? null;
    } catch (e) {
        console.error("fcm auth failed", e);
        return null;
    }
}

/** Best-effort push to every device of the given users; never throws. */
async function pushToUsers(c: any, userIds: string[], title: string, bodyText: string) {
    try {
        if (userIds.length === 0) return;
        const token = await fcmAccessToken(c.env);
        if (!token) return;
        const unique = [...new Set(userIds)];
        for (const uid of unique) {
            const devices = await rows<{ token: string }>(
                c,
                "SELECT token FROM device_tokens WHERE user_id = ? AND updated_at > ?",
                uid, Date.now() - 90 * 24 * 60 * 60 * 1000,
            );
            for (const d of devices) {
                try {
                    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${c.env.FCM_PROJECT_ID}/messages:send`, {
                        method: "POST",
                        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
                        body: JSON.stringify({
                            message: {
                                token: d.token,
                                notification: { title, body: bodyText },
                            },
                        }),
                    });
                    if (!res.ok) {
                        console.error(`fcm send ${res.status} for token ${d.token.slice(0, 12)}…: ${await res.text()}`);
                    } else {
                        console.log(`push sent to token ${d.token.slice(0, 12)}…`);
                    }
                } catch (e) {
                    console.error("fcm fetch threw", e);
                }
            }
        }
    } catch (e) {
        console.error("push failed", e);
    }
}

function normalizePhone(raw: string): string | null {
    let cleaned = raw.replace(/[\s()\-]/g, "");
    if (!cleaned.startsWith("+")) return null;
    cleaned = "+" + cleaned.slice(1).replace(/\D/g, "");
    return /^\+\d{8,15}$/.test(cleaned) ? cleaned : null;
}

function jsonError(c: any, status: any, message: string) {
    return c.json({ message }, status);
}

export default app;

