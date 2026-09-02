/**
 * Crypto + token helpers for the teramera Worker.
 * HS256 JWTs and SHA-256 refresh-token digests via WebCrypto.
 */

const enc = new TextEncoder();

function b64url(bytes: ArrayBuffer | Uint8Array): string {
    const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
    let s = btoa(String.fromCharCode(...b));
    return s.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function b64urlJson(obj: unknown): string {
    return b64url(enc.encode(JSON.stringify(obj)));
}

async function hmacKey(secret: string): Promise<CryptoKey> {
    return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
}

// ---------- JWT ----------

export interface JwtDeps {
    secret: string;
    accessTtlMinutes: number;
    refreshTtlDays: number;
}

export async function signAccessToken(deps: JwtDeps, userId: string): Promise<string> {
    const header = b64urlJson({ alg: "HS256", typ: "JWT" });
    const now = Math.floor(Date.now() / 1000);
    const payload = b64urlJson({
        sub: userId,
        typ: "access",
        jti: crypto.randomUUID(),
        iat: now,
        exp: now + deps.accessTtlMinutes * 60,
    });
    const data = `${header}.${payload}`;
    const sig = await crypto.subtle.sign("HMAC", await hmacKey(deps.secret), enc.encode(data));
    return `${data}.${b64url(sig)}`;
}

export async function verifyAccessToken(deps: JwtDeps, token: string): Promise<{ sub: string; typ: string } | null> {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const key = await hmacKey(deps.secret);
    const sig = await crypto.subtle.sign("HMAC", key, enc.encode(`${parts[0]}.${parts[1]}`));
    if (b64url(sig) !== parts[2]) return null;
    try {
        const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
        if (payload.typ !== "access") return null;
        if (typeof payload.exp === "number" && payload.exp < Date.now() / 1000) return null;
        return { sub: payload.sub, typ: payload.typ };
    } catch {
        return null;
    }
}

export async function issueRefreshToken(): Promise<{ token: string; hash: string; expiresAt: number }> {
    const token = `${crypto.randomUUID()}.${crypto.randomUUID()}`;
    return { token, hash: await sha256Hex(token), expiresAt: Date.now() + 30 * 24 * 3600 * 1000 };
}

export async function sha256Hex(value: string): Promise<string> {
    const digest = await crypto.subtle.digest("SHA-256", enc.encode(value));
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
