import { Hono } from "hono";
import { cors } from "hono/cors";
import {
    issueRefreshToken,
    sha256Hex,
    signAccessToken,
    verifyAccessToken,
} from "./crypto";
import { computeSplit, simplifyDebts, SplitType } from "./split";
import { sendMail } from "./mail";

interface Env {
    DB: D1Database;
    JWT_SECRET: string;
    GOOGLE_CLIENT_ID?: string;
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

type Row = Record<string, any>;

// ---------- auth middleware ----------

app.use("*", cors());

app.use(async (c, next) => {
    if (c.req.path.startsWith("/auth/") || c.req.path === "/" || c.req.path === "/health" || c.req.path.startsWith("/invite/") || c.req.path === "/app/version") return next();
    const header = c.req.header("Authorization");
    if (!header?.startsWith("Bearer ")) {
        return c.json({ message: "unauthorized" }, 401);
    }
    const parsed = await verifyAccessToken(jwtDeps(c.env), header.slice(7));
    if (!parsed) return c.json({ message: "unauthorized" }, 401);
    c.set("userId", parsed.sub);
    await next();
});

/** Public download page — the app's "website" and the link shared with new people. */
app.get("/", (c) => {
    const origin = originOf(c);
    const apkUrl = c.env.APK_DOWNLOAD_URL || `${origin}/teramera.apk`;
    const versionName = c.env.APP_VERSION_NAME || "1.0";
    const versionCode = Number(c.env.APP_VERSION_CODE || 1);
    const html = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>teramera — split expenses with friends</title>
<meta name="description" content="Split expenses with friends, keep every balance fair, and settle up in the fewest payments possible.">
<style>
 *{box-sizing:border-box}
 body{font-family:-apple-system,system-ui,'Segoe UI',sans-serif;background:#FBF9F4;color:#251C15;margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
 .card{max-width:400px;width:100%;background:#F5F1E8;border-radius:24px;padding:36px 28px;text-align:center;box-shadow:0 6px 24px rgba(37,28,21,.08)}
 .logo{font-size:40px;font-weight:800;letter-spacing:-2px}
 .tere{color:#825EA9}.mera{color:#00848B}
 h1{font-size:22px;margin:18px 0 8px;font-weight:700}
 p{font-size:15px;color:#5B5046;line-height:1.5;margin:0 0 24px}
 .btn{display:block;padding:16px;border-radius:16px;text-decoration:none;font-weight:700;font-size:15.5px;margin-bottom:12px}
 .btn-primary{background:#00848B;color:#fff}
 ul{list-style:none;padding:0;margin:26px 0 0;text-align:left}
 li{font-size:14px;color:#5B5046;line-height:1.5;margin-bottom:12px;padding-left:26px;position:relative}
 li b{color:#251C15}
 li:before{content:"✓";position:absolute;left:0;color:#00848B;font-weight:700}
 small{display:block;margin-top:20px;font-size:12px;color:#877F75;line-height:1.5}
 .badge{display:inline-block;background:#D2EEF0;color:#00565b;font-size:11.5px;font-weight:700;padding:4px 12px;border-radius:999px;margin-bottom:14px}
</style></head>
<body><div class="card">
 <div class="badge">ANDROID APP</div>
 <div class="logo"><span class="tere">tere</span><span class="mera">mera</span></div>
 <h1>Split expenses, skip the awkwardness</h1>
 <p>Add an expense however you like — equal, exact, shares or percent, even paid by several people. teramera keeps every balance fair and settles up in the fewest payments possible.</p>
 <a class="btn btn-primary" href="${apkUrl}">Download teramera</a>
 <ul>
  <li><b>Works offline.</b> Log expenses on a trip and sync when you're back.</li>
  <li><b>Fair to the paisa.</b> Splits always add up exactly.</li>
  <li><b>Pay by UPI.</b> Settle straight from the app.</li>
 </ul>
 <small>Version ${versionName} (build ${versionCode}) · Android 8.0+<br>
 Already installed? teramera updates itself — no app store needed.</small>
</div></body></html>`;
    return c.html(html);
});

/** Lightweight health probe for uptime checks. */
app.get("/health", (c) => c.json({ name: "teramera-api", status: "ok" }));

/** Current APK version — the app polls this on launch to offer in-app updates. */
app.get("/app/version", (c) => {
    const origin = originOf(c);
    return c.json({
        versionCode: Number(c.env.APP_VERSION_CODE || 1),
        versionName: c.env.APP_VERSION_NAME || "1.0",
        apkUrl: c.env.APK_DOWNLOAD_URL || `${origin}/teramera.apk`,
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

/** D1 allows at most 100 bound parameters per statement. */
const D1_PARAM_CAP = 90;

function placeholders(count: number): string {
    return Array(count).fill("?").join(",");
}

function chunks<T>(items: T[], size = D1_PARAM_CAP): T[][] {
    const out: T[][] = [];
    for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
    return out;
}

/** Runs an IN (...) query in chunks so large groups/feeds never hit the cap. */
async function rowsByIds<T = Row>(
    c: any,
    sqlFor: (count: number) => string,
    ids: string[],
    leading: unknown[] = [],
): Promise<T[]> {
    const out: T[] = [];
    for (const part of chunks([...new Set(ids)])) {
        out.push(...await rows<T>(c, sqlFor(part.length), ...leading, ...part));
    }
    return out;
}

function badRequest(message: string) {
    throw new HttpError(400, message);
}

function isPositiveInteger(value: unknown): value is number {
    return typeof value === "number" && Number.isInteger(value) && value > 0;
}

/** Public origin for links; follows the request scheme so local dev works over http. */
function escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, (ch) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
    }[ch] as string));
}

function originOf(c: any): string {
    return c.env.APP_BASE_URL || new URL(c.req.url).origin;
}

/**
 * Split an amount across weighted entries, giving the leftover paise to the
 * largest fractional remainders so the parts always sum to `bucket` exactly.
 */
function allocateByWeight(
    entries: { id: string; weight: number }[],
    bucket: number,
    totalWeight: number,
): Map<string, number> {
    const result = new Map<string, number>();
    if (entries.length === 0 || totalWeight <= 0) return result;
    let allocated = 0;
    const remainders: { id: string; remainder: number }[] = [];
    for (const entry of entries) {
        const exact = bucket * entry.weight;
        const value = Math.floor(exact / totalWeight);
        result.set(entry.id, value);
        allocated += value;
        remainders.push({ id: entry.id, remainder: exact % totalWeight });
    }
    let leftover = bucket - allocated;
    remainders.sort((a, b) => b.remainder - a.remainder);
    for (const item of remainders) {
        if (leftover <= 0) break;
        result.set(item.id, (result.get(item.id) ?? 0) + 1);
        leftover -= 1;
    }
    return result;
}

/** One logical expense per parent_id; multi-payer rows are stored as siblings. */
function groupExpenseRows(expenses: Row[]): Map<string, Row[]> {
    const grouped = new Map<string, Row[]>();
    for (const expense of expenses) {
        const key = String(expense.parent_id ?? expense.id);
        const list = grouped.get(key) ?? [];
        list.push(expense);
        grouped.set(key, list);
    }
    return grouped;
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
//
// v0.3.4: Google Sign-In is the only way in. Phone OTP and email+password
// auth were removed because (a) no SMS provider is wired (codes only logged),
// and (b) Google gives us a verified email + avatar for free.

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

// ---------- refresh / logout ----------

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

/** Rename a group. Only the creator can manage group settings. */
app.patch("/groups/:groupId", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const [group] = await rows<GroupRow>(c, "SELECT * FROM groups WHERE id = ?", groupId);
    if (!group) return jsonError(c, 404, "Group not found");
    if (group.created_by !== userId) return jsonError(c, 403, "Only the group creator can rename this group");

    const body = await c.req.json<{ name?: string }>();
    const name = body.name?.trim();
    if (!name) return jsonError(c, 400, "name is required");
    if (name.length > 80) return jsonError(c, 400, "Name must be 80 characters or fewer");

    await run(c, "UPDATE groups SET name = ? WHERE id = ?", name, groupId);
    return c.json({ id: groupId, name, currency: group.currency });
});

/** Delete a group and everything that belongs to it (creator only). */
app.delete("/groups/:groupId", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const [group] = await rows<GroupRow>(c, "SELECT * FROM groups WHERE id = ?", groupId);
    if (!group) return jsonError(c, 404, "Group not found");
    if (group.created_by !== userId) return jsonError(c, 403, "Only the group creator can delete this group");

    await c.env.DB.batch([
        c.env.DB.prepare(
            "DELETE FROM expense_shares WHERE expense_id IN (SELECT id FROM expenses WHERE group_id = ?)",
        ).bind(groupId),
        c.env.DB.prepare("DELETE FROM expenses WHERE group_id = ?").bind(groupId),
        c.env.DB.prepare("DELETE FROM settlements WHERE group_id = ?").bind(groupId),
        c.env.DB.prepare("DELETE FROM memberships WHERE group_id = ?").bind(groupId),
        c.env.DB.prepare("DELETE FROM groups WHERE id = ?").bind(groupId),
    ]);
    return c.json({ status: "deleted", id: groupId });
});

/** Find a teramera user by email — how friends discover each other. */
app.get("/users/find", async (c) => {
    const email = (c.req.query("email") ?? "").trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
        return jsonError(c, 400, "Provide a valid email");
    }
    const [user] = await rows<UserRow>(c, "SELECT * FROM users WHERE email = ?", email);
    if (!user) return jsonError(c, 404, "No teramera user with that email yet");
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
    const link = `${originOf(c)}/invite/${groupId}`;
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
    const origin = originOf(c);
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

/** Placeholder member for someone who hasn't signed up yet. */
app.post("/groups/:groupId/dummies", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const body = await c.req.json<{ name?: string }>();
    const name = body.name?.trim();
    if (!name) return jsonError(c, 400, "name is required");
    if (name.length > 60) return jsonError(c, 400, "Name must be 60 characters or fewer");

    const id = `dummy:${crypto.randomUUID()}`;
    await run(
        c,
        "INSERT INTO users (id, phone, email, name, created_at) VALUES (?, NULL, NULL, ?, ?)",
        id, name, Date.now(),
    );
    await run(
        c,
        "INSERT INTO memberships (group_id, user_id, role) VALUES (?, ?, 'member')",
        groupId, id,
    );
    return c.json({ id, name, isSelf: false, isDummy: true, upiId: "" });
});

/**
 * A real member claims a placeholder: all expenses, shares and settlements
 * move from the dummy id to the signed-in user, then the dummy is removed.
 */
app.post("/groups/:groupId/claim-dummy", async (c) => {
    const groupId = c.req.param("groupId");
    const userId = c.get("userId");
    await requireMember(c, groupId, userId);

    const body = await c.req.json<{ dummyUserId?: string }>();
    const dummyId = body.dummyUserId ?? "";
    if (!dummyId.startsWith("dummy:")) return jsonError(c, 400, "Not a placeholder member");

    const [dummy] = await rows<Row>(
        c,
        `SELECT u.id, u.name FROM users u
         JOIN memberships m ON m.user_id = u.id
         WHERE u.id = ? AND m.group_id = ?`,
        dummyId, groupId,
    );
    if (!dummy) return jsonError(c, 404, "Placeholder member not found in this group");
    if (dummyId === userId) return jsonError(c, 400, "You cannot claim yourself");

    // Once a real user has activity here, claiming another placeholder would
    // merge two people's ledgers together.
    const [existing] = await rows<Row>(
        c,
        `SELECT
           (SELECT COUNT(*) FROM expense_shares s
              JOIN expenses e ON e.id = s.expense_id
             WHERE e.group_id = ? AND s.user_id = ?) AS shares,
           (SELECT COUNT(*) FROM expenses e
             WHERE e.group_id = ? AND e.paid_by_user_id = ?) AS paid`,
        groupId, userId, groupId, userId,
    );
    if (Number(existing?.shares ?? 0) > 0 || Number(existing?.paid ?? 0) > 0) {
        return jsonError(c, 409, "You already have expenses in this group");
    }

    await c.env.DB.batch([
        c.env.DB.prepare("DELETE FROM memberships WHERE group_id = ? AND user_id = ?").bind(groupId, dummyId),
        c.env.DB.prepare("UPDATE expenses SET paid_by_user_id = ? WHERE group_id = ? AND paid_by_user_id = ?").bind(userId, groupId, dummyId),
        c.env.DB.prepare(
            "UPDATE expense_shares SET user_id = ? WHERE user_id = ? AND expense_id IN (SELECT id FROM expenses WHERE group_id = ?)",
        ).bind(userId, dummyId, groupId),
        c.env.DB.prepare("UPDATE settlements SET payer_user_id = ? WHERE group_id = ? AND payer_user_id = ?").bind(userId, groupId, dummyId),
        c.env.DB.prepare("UPDATE settlements SET paid_to_user_id = ? WHERE group_id = ? AND paid_to_user_id = ?").bind(userId, groupId, dummyId),
        c.env.DB.prepare("DELETE FROM users WHERE id = ?").bind(dummyId),
    ]);
    return c.json({ status: "claimed", dummyUserId: dummyId, userId });
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
        // Count each payment exactly once — adding it inside the share loop would
        // multiply a payer's credit by their number of participants.
        for (const expense of expenses) {
            net.set(expense.paid_by_user_id, (net.get(expense.paid_by_user_id) ?? 0) + expense.amount_minor);
        }
        const knownExpense = new Set(expenses.map((e) => e.id));
        for (const share of shares) {
            if (!knownExpense.has(share.expense_id)) continue;
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

    const [group] = await rows<GroupRow>(c, "SELECT * FROM groups WHERE id = ?", groupId);
    if (!group) return jsonError(c, 404, "Group not found");
    await requireMember(c, groupId, userId);

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
        isDummy: typeof u.id === "string" && u.id.startsWith("dummy:"),
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

    // Multi-payer expenses are stored as sibling rows sharing parent_id. The
    // screen must show one row per *logical* expense, not one per payer.
    const expenseList = [...groupExpenseRows(expenses).entries()].map(([parentId, logicalRows]) => {
        const first = logicalRows[0];
        const logicalShares = logicalRows.flatMap((row) => sharesByExpense.get(row.id) ?? []);
        const participants = new Set(logicalShares.map((s) => s.user_id));
        const payers = new Set(logicalRows.map((row) => row.paid_by_user_id));
        return {
            id: first.id,
            parentId,
            title: first.title,
            paidByUserId: first.paid_by_user_id,
            payerCount: payers.size,
            splitType: first.split_type,
            amountMinor: logicalRows.reduce((acc, row) => acc + row.amount_minor, 0),
            myShareMinor: logicalShares
                .filter((s) => s.user_id === userId)
                .reduce((acc, s) => acc + s.share_amount_minor, 0),
            participantCount: participants.size,
            createdAt: first.created_at,
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
        createdBy: group.created_by,
        totalSpentMinor: expenses.reduce((acc, e) => acc + e.amount_minor, 0),
        members,
        expenses: expenseList,
        balances: namedBalances,
        simplifiedDebts: namedDebts,
        worstCasePayments: simplified.worstCaseCount,
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
    if (expenses.length === 0) return c.json([]);
    const shares = await rows<Row>(
        c,
        `SELECT expense_id, user_id, share_amount_minor FROM expense_shares
         WHERE expense_id IN (${expenses.map(() => "?").join(",")})`,
        ...expenses.map((e) => e.id),
    );
    const sharesByExpense = new Map<string, Row[]>();
    for (const s of shares) {
        const list = sharesByExpense.get(s.expense_id) ?? [];
        list.push(s);
        sharesByExpense.set(s.expense_id, list);
    }
    return c.json([...groupExpenseRows(expenses).entries()].map(([parentId, logicalRows]) => {
        const first = logicalRows[0];
        const logicalShares = logicalRows.flatMap((row) => sharesByExpense.get(row.id) ?? []);
        return {
            id: first.id,
            parentId,
            title: first.title,
            paidByUserId: first.paid_by_user_id,
            payerCount: new Set(logicalRows.map((row) => row.paid_by_user_id)).size,
            amountMinor: logicalRows.reduce((acc, row) => acc + row.amount_minor, 0),
            myShareMinor: logicalShares
                .filter((s) => s.user_id === userId)
                .reduce((acc, s) => acc + s.share_amount_minor, 0),
            participantCount: new Set(logicalShares.map((s) => s.user_id)).size,
            createdAt: first.created_at,
        };
    }));
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
        /** raw values keyed by user — EXACT (paise), PERCENT, SHARES */
        participants?: Record<string, number>;
        /** explicit participant list, used by EQUAL splits */
        participantIds?: string[];
        payments?: { userId: string; amountMinor: number }[];
        currency?: string;
        fxRateToGroup?: number;
    }>();

    if (!body.title?.trim()) return jsonError(c, 400, "title is required");
    if (!isPositiveInteger(body.amountMinor)) return jsonError(c, 400, "Amount must be a positive whole number of paise");
    const ALLOWED_TYPES: SplitType[] = ["EQUAL", "EXACT", "PERCENT", "SHARES"];
    if (!body.splitType || !ALLOWED_TYPES.includes(body.splitType)) {
        return jsonError(c, 400, "splitType must be EQUAL, EXACT, PERCENT or SHARES");
    }
    if (body.groupId) await requireMember(c, body.groupId, userId);

    // ---- payers: payments[] wins over legacy single paidByUserId ----
    for (const payment of body.payments ?? []) {
        if (!payment?.userId || !isPositiveInteger(payment.amountMinor)) {
            return jsonError(c, 400, "Each payer needs a userId and a positive amount");
        }
    }
    let payments = body.payments ?? [];
    if (payments.length === 0) {
        if (!body.paidByUserId) return jsonError(c, 400, "paidByUserId or payments is required");
        payments = [{ userId: body.paidByUserId, amountMinor: body.amountMinor }];
    }
    const paymentTotal = payments.reduce((acc, p) => acc + p.amountMinor, 0);
    if (paymentTotal !== body.amountMinor) {
        return jsonError(c, 400, "Payer amounts do not sum to the total");
    }

    // ---- participants: explicit ids | raw-value map | default all group members ----
    let participantIds: string[];
    if (Array.isArray(body.participantIds)) {
        participantIds = body.participantIds.filter(Boolean);
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
    // everyone involved in a group expense must be a member of that group
    if (body.groupId) {
        const memberRows = await rows<{ user_id: string }>(
            c, "SELECT user_id FROM memberships WHERE group_id = ?", body.groupId,
        );
        const memberSet = new Set(memberRows.map((r) => r.user_id));
        for (const pid of participantIds) {
            if (!memberSet.has(pid)) return jsonError(c, 403, `Participant is not a group member: ${pid}`);
        }
        for (const p of payments) {
            if (!memberSet.has(p.userId)) return jsonError(c, 403, `Payer is not a group member: ${p.userId}`);
        }
    }

    // ---- total share per participant, then allocate each payment pro-rata ----
    const rawValues: Record<string, number> = body.participants ?? {};
    const totalSplit = computeSplit({
        type: body.splitType,
        totalMinor: body.amountMinor,
        participants: participantIds,
        rawValues,
    });
    if (!totalSplit.ok) return jsonError(c, 400, totalSplit.reason);
    const totalShares = totalSplit.shares;

    const allocatePayment = (bucket: number) =>
        allocateByWeight(
            totalShares.map((share) => ({ id: share.userId, weight: share.amountMinor })),
            bucket,
            body.amountMinor!,
        );

    const createdAt = Date.now();
    // one shared parent id ties the per-payment rows of a logical expense together
    const parentId = crypto.randomUUID();
    const createdIds: string[] = [];
    for (const payment of payments) {
        const id = crypto.randomUUID();
        const allocation = payments.length === 1
            ? new Map(totalShares.map((s) => [s.userId, s.amountMinor]))
            : allocatePayment(payment.amountMinor);
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

    const body = await c.req.json<{
        title?: string;
        amountMinor?: number;
        participantIds?: string[];
        splitType?: SplitType;
        participants?: Record<string, number>;
    }>();
    const title = body.title?.trim() ?? siblings[0].title;
    if (!title) return jsonError(c, 400, "title is required");
    const totalMinor = body.amountMinor ?? siblings.reduce((acc, row) => acc + row.amount_minor, 0);
    if (!isPositiveInteger(totalMinor)) return jsonError(c, 400, "Amount must be a positive whole number of paise");

    const ALLOWED_TYPES: SplitType[] = ["EQUAL", "EXACT", "PERCENT", "SHARES"];
    // A plain title/amount edit keeps each person's slice of the pie; only an
    // explicit split change recomputes the shares from scratch.
    const wantsResplit =
        body.splitType !== undefined ||
        body.participants !== undefined ||
        (body.participantIds?.length ?? 0) > 0;

    // Participants: an explicit list or raw-value map wins, else keep whoever was split.
    const siblingIds = siblings.map((s) => s.id);
    const existingShares = await rows<Row>(
        c,
        `SELECT user_id, share_amount_minor FROM expense_shares WHERE expense_id IN (${siblingIds.map(() => "?").join(",")})`,
        ...siblingIds,
    );
    let participantIds: string[] = (body.participantIds ?? []).filter(Boolean);
    if (participantIds.length === 0 && body.participants) {
        participantIds = Object.keys(body.participants);
    }
    if (participantIds.length === 0) {
        participantIds = existingShares.map((s) => s.user_id as string);
    }
    participantIds = [...new Set(participantIds)];
    if (participantIds.length < 2) return jsonError(c, 400, "At least two people required");

    for (const pid of participantIds) {
        const exists = await rows(c, "SELECT 1 AS m FROM users WHERE id = ?", pid);
        if (exists.length === 0) return jsonError(c, 400, `Unknown user: ${pid}`);
    }
    if (expense.group_id) {
        const memberRows = await rows<{ user_id: string }>(
            c, "SELECT user_id FROM memberships WHERE group_id = ?", expense.group_id,
        );
        const memberSet = new Set(memberRows.map((r) => r.user_id));
        for (const pid of participantIds) {
            if (!memberSet.has(pid)) return jsonError(c, 403, `Participant is not a group member: ${pid}`);
        }
    }

    let targetShares: { userId: string; amountMinor: number }[];
    if (wantsResplit) {
        const splitType: SplitType = body.splitType ?? (siblings[0].split_type as SplitType) ?? "EQUAL";
        if (!ALLOWED_TYPES.includes(splitType)) return jsonError(c, 400, "Invalid split type");
        const split = computeSplit({
            type: splitType,
            totalMinor,
            participants: participantIds,
            rawValues: body.participants ?? {},
        });
        if (!split.ok) return jsonError(c, 400, split.reason);
        targetShares = split.shares;
    } else {
        const perUser = new Map<string, number>();
        for (const share of existingShares) {
            const uid = share.user_id as string;
            perUser.set(uid, (perUser.get(uid) ?? 0) + (share.share_amount_minor as number));
        }
        const oldShareTotal = [...perUser.values()].reduce((acc, value) => acc + value, 0);
        if (oldShareTotal > 0) {
            const scaled = allocateByWeight(
                [...perUser.entries()].map(([id, weight]) => ({ id, weight })),
                totalMinor,
                oldShareTotal,
            );
            targetShares = [...scaled.entries()].map(([userId, amountMinor]) => ({ userId, amountMinor }));
        } else {
            const split = computeSplit({ type: "EQUAL", totalMinor, participants: participantIds, rawValues: {} });
            if (!split.ok) return jsonError(c, 400, split.reason);
            targetShares = split.shares;
        }
    }

    // Keep each payer's share of the total proportional to what they originally
    // fronted, then re-allocate the new split across those payer rows.
    const oldTotal = siblings.reduce((acc, row) => acc + row.amount_minor, 0);
    const payerAmounts = allocateByWeight(
        siblings.map((row) => ({ id: row.id, weight: row.amount_minor })),
        totalMinor,
        oldTotal,
    );
    for (const sib of siblings) {
        const payerAmount = payerAmounts.get(sib.id) ?? 0;
        await run(c, "UPDATE expenses SET title = ?, amount_minor = ? WHERE id = ?", title, payerAmount, sib.id);
        await run(c, "DELETE FROM expense_shares WHERE expense_id = ?", sib.id);
        const allocation = siblings.length === 1
            ? new Map(targetShares.map((s) => [s.userId, s.amountMinor]))
            : allocateByWeight(
                targetShares.map((s) => ({ id: s.userId, weight: s.amountMinor })),
                payerAmount,
                totalMinor,
            );
        for (const [uid, minor] of allocation.entries()) {
            if (minor > 0) {
                await run(
                    c,
                    "INSERT INTO expense_shares (expense_id, user_id, share_amount_minor) VALUES (?, ?, ?)",
                    sib.id, uid, minor,
                );
            }
        }
    }
    return c.json({ status: "updated", id: expenseId, amountMinor: totalMinor });
});

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

// ---------- settle requests ----------

/**
 * Emails the person who owes money a UPI intent they can tap to pay.
 * Placeholders have no email, and the requester must have a UPI ID so the
 * intent can actually route the payment.
 */
app.post("/settlements/request", async (c) => {
    const userId = c.get("userId");
    const body = await c.req.json<{ toUserId?: string; amountMinor?: number }>();
    if (!body.toUserId) return jsonError(c, 400, "toUserId is required");
    if (!isPositiveInteger(body.amountMinor)) return jsonError(c, 400, "Amount must be a positive whole number of paise");
    if (body.toUserId === userId) return jsonError(c, 400, "You cannot request a payment from yourself");
    if (body.toUserId.startsWith("dummy:")) {
        return jsonError(c, 409, "This member is a placeholder and has no email yet");
    }

    const [requester] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", userId);
    const [recipient] = await rows<UserRow>(c, "SELECT * FROM users WHERE id = ?", body.toUserId);
    if (!requester) return jsonError(c, 404, "Your account was not found");
    if (!recipient) return jsonError(c, 404, "That person is not on teramera");
    if (!recipient.email) return jsonError(c, 409, "That person doesn't have an email address yet");
    if (!requester.upi_id) {
        return jsonError(c, 400, "Add your UPI ID in Profile before requesting a payment");
    }

    const amountMinor = body.amountMinor!;
    const amount = (amountMinor / 100).toFixed(2);
    const requesterName = requester.name?.trim() || "A friend";
    const recipientName = recipient.name?.trim() || "there";
    const origin = originOf(c);
    const params = new URLSearchParams({
        pa: requester.upi_id,
        pn: requesterName,
        am: amount,
        cu: "INR",
        tn: "teramera settle",
    });
    const upiIntent = `upi://pay?${params.toString()}`;
    const safeRequester = escapeHtml(requesterName);
    const safeRecipient = escapeHtml(recipientName);
    const safeUpi = escapeHtml(requester.upi_id);
    const rupees = `₹${Number(amount).toLocaleString("en-IN")}`;

    const subject = `${requesterName} requested ${rupees} on teramera`;
    const html = `
      <div style="font-family:-apple-system,Segoe UI,sans-serif;max-width:520px;margin:0 auto;padding:28px;color:#121220">
        <div style="font-size:30px;font-weight:800;letter-spacing:-1px;margin-bottom:18px">
          <span style="color:#F1AB88">tere</span><span style="color:#78C1B7">mera</span>
        </div>
        <p style="font-size:16px;line-height:1.55;margin:0 0 10px">Hi ${safeRecipient},</p>
        <p style="font-size:16px;line-height:1.55;margin:0 0 18px">
          <b>${safeRequester}</b> has requested <b>${rupees}</b> to settle up on teramera.
        </p>
        <a href="${upiIntent}"
           style="display:block;text-align:center;background:#F1AB88;color:#121220;text-decoration:none;
                  font-weight:700;font-size:16px;padding:16px;border-radius:999px;margin:22px 0">
          Pay ${rupees} with UPI
        </a>
        <p style="font-size:14px;color:#505063;line-height:1.6;margin:0 0 6px">
          Or pay to <b>${safeUpi}</b> in any UPI app.
        </p>
        <p style="font-size:14px;color:#505063;line-height:1.6;margin:18px 0 0">
          <a href="${origin}" style="color:#505063">Open teramera</a> to see the full balance and record the payment.
        </p>
        <p style="font-size:12px;color:#6D6D7A;line-height:1.6;margin:22px 0 0">
          This request was sent because ${safeRequester} chose to remind you from teramera.
        </p>
      </div>`;
    const text = `${requesterName} requested ${rupees} on teramera. Pay with UPI: ${upiIntent} (UPI ID: ${requester.upi_id}).`;

    try {
        const status = await sendMail(c.env, recipient.email, subject, html, text);
        return c.json({ status, amountMinor });
    } catch (err) {
        console.error("Settle request email failed:", err);
        return jsonError(c, 502, "Couldn't send the settle request email");
    }
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
    if (!isPositiveInteger(body.amountMinor)) return jsonError(c, 400, "Amount must be a positive whole number of paise");
    if (!body.method || !METHODS.has(body.method.toUpperCase())) {
        return jsonError(c, 400, "Method must be UPI, CASH or BANK");
    }
    const payer = body.payerUserId || userId;
    if (payer === body.paidToUserId) return jsonError(c, 400, "Payer and payee must be different people");
    if (payer !== userId && body.paidToUserId !== userId) {
        return jsonError(c, 403, "You must be part of the settlement");
    }
    const knownUsers = await rows<{ id: string }>(
        c, "SELECT id FROM users WHERE id IN (?, ?)", payer, body.paidToUserId,
    );
    if (knownUsers.length !== 2) return jsonError(c, 400, "Unknown payer or payee");
    if (body.groupId) {
        await requireMember(c, body.groupId, userId);
        const members = await rows<{ user_id: string }>(
            c, "SELECT user_id FROM memberships WHERE group_id = ? AND user_id IN (?, ?)",
            body.groupId, payer, body.paidToUserId,
        );
        if (members.length !== 2) return jsonError(c, 403, "Both people must be group members");
    }

    await run(
        c,
        `INSERT INTO settlements (id, group_id, payer_user_id, paid_to_user_id, amount_minor, method, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        crypto.randomUUID(), body.groupId ?? null, payer, body.paidToUserId,
        body.amountMinor, body.method.toUpperCase(), Date.now(),
    );

    // Notify the *other* side of the payment (receiver when payer paid, payer when
    // receiver recorded a payment they made). Best-effort, never throws.
    const receiver = payer === userId ? body.paidToUserId : payer;
    if (
        receiver &&
        c.env.FCM_PROJECT_ID &&
        c.env.FCM_CLIENT_EMAIL &&
        c.env.FCM_PRIVATE_KEY
    ) {
        c.executionCtx.waitUntil((async () => {
            const [payerUser] = await rows<UserRow>(c, "SELECT name FROM users WHERE id = ?", payer);
            let groupName: string | null = null;
            if (body.groupId) {
                const [g] = await rows<{ name: string }>(c, "SELECT name FROM groups WHERE id = ?", body.groupId);
                groupName = g?.name ?? null;
            }
            const rupees = (body.amountMinor ?? 0) / 100;
            await pushToUsers(
                c,
                [receiver],
                `${payerUser?.name ?? "Someone"} settled up`,
                `₹${rupees.toFixed(0)}${groupName ? ` · ${groupName}` : ""} · ${body.method!.toUpperCase()}`,
            );
        })());
    }
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

    // Two aggregates instead of one query per expense: what others owe me and
    // what I owe others. /balances stays at a constant number of queries.
    const owedToMe = await rows<Row>(
        c,
        `SELECT s.user_id AS uid, SUM(s.share_amount_minor) AS total
         FROM expense_shares s
         JOIN expenses e ON e.id = s.expense_id
         WHERE e.paid_by_user_id = ? AND s.user_id != ?
         GROUP BY s.user_id`,
        selfId, selfId,
    );
    for (const row of owedToMe) {
        net.set(row.uid, (net.get(row.uid) ?? 0) + row.total);
    }

    const iOwe = await rows<Row>(
        c,
        `SELECT e.paid_by_user_id AS uid, SUM(s.share_amount_minor) AS total
         FROM expense_shares s
         JOIN expenses e ON e.id = s.expense_id
         WHERE s.user_id = ? AND e.paid_by_user_id != ?
         GROUP BY e.paid_by_user_id`,
        selfId, selfId,
    );
    for (const row of iOwe) {
        net.set(row.uid, (net.get(row.uid) ?? 0) - row.total);
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
        ? await rowsByIds<UserRow>(
              c,
              (n) => `SELECT id, name, upi_id FROM users WHERE id IN (${placeholders(n)})`,
              uids,
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
    // One event per logical expense. Shares, payer names and group names are
    // batched so a page of activity costs four queries, not four per row.
    const expenseIds = expenses.map((e) => e.id);
    const shares = expenseIds.length > 0
        ? await rowsByIds<Row>(
              c,
              (n) => `SELECT expense_id, user_id, share_amount_minor FROM expense_shares
                      WHERE expense_id IN (${placeholders(n)})`,
              expenseIds,
          )
        : [];
    const sharesByExpense = new Map<string, Row[]>();
    for (const share of shares) {
        const list = sharesByExpense.get(share.expense_id) ?? [];
        list.push(share);
        sharesByExpense.set(share.expense_id, list);
    }

    const payerIds = [...new Set(expenses.map((e) => e.paid_by_user_id as string))];
    const payerRows = payerIds.length > 0
        ? await rowsByIds<UserRow>(
              c,
              (n) => `SELECT id, name FROM users WHERE id IN (${placeholders(n)})`,
              payerIds,
          )
        : [];
    const payerNameById = new Map(payerRows.map((u) => [u.id, u.name]));

    const groupIds = [...new Set(expenses.map((e) => e.group_id).filter(Boolean) as string[])];
    const groupRows = groupIds.length > 0
        ? await rowsByIds<{ id: string; name: string }>(
              c,
              (n) => `SELECT id, name FROM groups WHERE id IN (${placeholders(n)})`,
              groupIds,
          )
        : [];
    const groupNameById = new Map(groupRows.map((g) => [g.id, g.name]));

    const expenseEvents = [...groupExpenseRows(expenses).entries()].map(([, logicalRows]) => {
        const first = logicalRows[0];
        const logicalShares = logicalRows.flatMap((row) => sharesByExpense.get(row.id) ?? []);
        return {
            type: "expense" as const,
            id: first.id,
            title: first.title,
            payerName: payerNameById.get(first.paid_by_user_id) ?? "?",
            paidBySelf: logicalRows.some((row) => row.paid_by_user_id === selfId),
            amountMinor: logicalRows.reduce((acc, row) => acc + row.amount_minor, 0),
            myShareMinor: logicalShares
                .filter((s) => s.user_id === selfId)
                .reduce((acc, s) => acc + s.share_amount_minor, 0),
            groupName: first.group_id ? groupNameById.get(first.group_id) ?? null : null,
            participantCount: new Set(logicalShares.map((s) => s.user_id)).size,
            createdAt: first.created_at,
        };
    });

    const settlements = await rows<Row>(
        c,
        "SELECT * FROM settlements WHERE payer_user_id = ? OR paid_to_user_id = ? ORDER BY created_at DESC LIMIT ?",
        selfId, selfId, limit,
    );
    const settlementIds = [...new Set(settlements.flatMap((s) => [s.payer_user_id, s.paid_to_user_id] as string[]))];
    const settlementUsers = settlementIds.length > 0
        ? await rowsByIds<UserRow>(
              c,
              (n) => `SELECT id, name FROM users WHERE id IN (${placeholders(n)})`,
              settlementIds,
          )
        : [];
    const settlementName = new Map(settlementUsers.map((u) => [u.id, u.name]));
    const settlementEvents = [];
    for (const s of settlements) {
        settlementEvents.push({
            type: "settlement" as const,
            id: s.id,
            payerName: settlementName.get(s.payer_user_id) ?? "?",
            payeeName: settlementName.get(s.paid_to_user_id) ?? "?",
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

function jsonError(c: any, status: any, message: string) {
    return c.json({ message }, status);
}

export default app;

