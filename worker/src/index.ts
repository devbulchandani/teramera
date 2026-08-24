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
    return {
        // override with: npx wrangler secret put JWT_SECRET
        secret: env.JWT_SECRET || "dev-only-secret-change-me-0123456789abcdef",
        accessTtlMinutes: ACCESS_TTL_MINUTES,
        refreshTtlDays: 30,
    };
}

type Row = Record<string, any>;

// ---------- auth middleware ----------

app.use("*", cors());

app.use(async (c, next) => {
    if (c.req.path.startsWith("/auth/") || c.req.path === "/" || c.req.path.startsWith("/invite/")) return next();
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

    const code = String(Math.floor(100000 + Math.random() * 900000));
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
    if (c.env.GOOGLE_CLIENT_ID && c.env.GOOGLE_CLIENT_ID !== claims.aud) {
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

    // email verification OTP
    const now = Date.now();
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE phone = ? AND consumed = 0", email);
    const code = String(Math.floor(100000 + Math.random() * 900000));
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

    const response: Record<string, unknown> = { requestId };
    if (status === "logged") response.devCode = code;
    if (c.env.EXPOSE_DEV_OTP === "true" && !response.devCode) response.devCode = code;
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
    await run(c, "UPDATE otp_requests SET consumed = 1 WHERE phone = ? AND consumed = 0", email);
    const code = String(Math.floor(100000 + Math.random() * 900000));
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

    const response: Record<string, unknown> = { requestId };
    if (status === "logged") response.devCode = code;
    if (c.env.EXPOSE_DEV_OTP === "true" && !response.devCode) response.devCode = code;
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
    });
});

app.patch("/me", async (c) => {
    const body = await c.req.json<{ name?: string }>();
    const name = (body.name ?? "").trim();
    if (name.length < 1 || name.length > 60) return jsonError(c, 400, "Name must be 1-60 characters");
    await run(c, "UPDATE users SET name = ? WHERE id = ?", name, c.get("userId"));
    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", c.get("userId"));
    return c.json({ id: user.id, phone: user.phone ?? "", email: user.email ?? "", name: user.name ?? "" });
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
    const result = [];
    for (const group of list) {
        const spent = await rows<{ s: number }>(c, "SELECT COALESCE(SUM(amount_minor), 0) AS s FROM expenses WHERE group_id = ?", group.id);
        const balances = await groupBalances(c, group.id);
        const netForMe = balances.find((b) => b.userId === userId)?.netMinor ?? 0;
        result.push({
            id: group.id,
            name: group.name,
            currency: group.currency,
            totalSpentMinor: spent[0].s,
            netForMeMinor: netForMe,
        });
    }
    return c.json(result);
});

/** Per-member overpaid-net inside one group (positive = is owed). */
async function groupBalances(c: any, groupId: string): Promise<{ userId: string; netMinor: number }[]> {
    const net = new Map<string, number>();
    const expenses = await rows<Row>(c, "SELECT * FROM expenses WHERE group_id = ?", groupId);
    for (const expense of expenses) {
        net.set(expense.paid_by_user_id, (net.get(expense.paid_by_user_id) ?? 0) + expense.amount_minor);
        const shares = await rows<Row>(c, "SELECT * FROM expense_shares WHERE expense_id = ?", expense.id);
        for (const share of shares) {
            net.set(share.user_id, (net.get(share.user_id) ?? 0) - share.share_amount_minor);
        }
    }
    const settlements = await rows<Row>(c, "SELECT * FROM settlements WHERE group_id = ?", groupId);
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

    const memberIds = (await rows<{ user_id: string }>(c, "SELECT user_id FROM memberships WHERE group_id = ?", groupId))
        .map((r) => r.user_id);
    const members = [];
    for (const memberId of memberIds) {
        const [u] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", memberId);
        members.push({ id: memberId, name: u?.name ?? "?", isSelf: memberId === userId });
    }

    const expenses = await rows<Row>(
        c,
        "SELECT * FROM expenses WHERE group_id = ? ORDER BY created_at DESC",
        groupId,
    );
    const expenseList = [];
    for (const expense of expenses) {
        const shares = await rows<Row>(c, "SELECT * FROM expense_shares WHERE expense_id = ?", expense.id);
        expenseList.push({
            id: expense.id,
            title: expense.title,
            paidByUserId: expense.paid_by_user_id,
            amountMinor: expense.amount_minor,
            myShareMinor: shares.find((s) => s.user_id === userId)?.share_amount_minor ?? 0,
            participantCount: shares.length,
            createdAt: expense.created_at,
        });
    }

    const balances = await groupBalances(c, groupId);
    const simplified = simplifyDebts(new Map(balances.map((b) => [b.userId, b.netMinor])));
    const nameOf = async (uid: string) => {
        const [u] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", uid);
        return u?.name ?? "?";
    };
    const namedDebts = [];
    for (const t of simplified.transfers) {
        namedDebts.push({
            fromUserId: t.fromUserId,
            fromName: await nameOf(t.fromUserId),
            toUserId: t.toUserId,
            toName: await nameOf(t.toUserId),
            amountMinor: t.amountMinor,
        });
    }
    const namedBalances = [];
    for (const b of balances) {
        namedBalances.push({ userId: b.userId, name: await nameOf(b.userId), netMinor: b.netMinor });
    }

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
    const createdIds: string[] = [];
    for (const payment of payments) {
        const id = crypto.randomUUID();
        const allocation = payments.length === 1
            ? new Map(totalShares.map((s) => [s.userId, s.amountMinor]))
            : largestRemainderAllocate(payment.amountMinor);
        await run(
            c,
            `INSERT INTO expenses (id, group_id, paid_by_user_id, title, amount_minor, split_type, currency, fx_rate_to_group, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            id, body.groupId ?? null, payment.userId, body.title.trim(),
            payment.amountMinor, body.splitType, body.currency ?? "INR",
            body.fxRateToGroup ?? 1.0, createdAt,
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
    return c.json({ id: createdIds[0], count: createdIds.length, amountMinor: body.amountMinor });
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

    const result = [];
    for (const [uid, value] of net.entries()) {
        const [user] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", uid);
        result.push({ userId: uid, name: user?.name ?? "?", netMinor: value });
    }
    return c.json(result);
});

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

