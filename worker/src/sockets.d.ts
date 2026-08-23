// Type declarations for the Cloudflare outbound TCP sockets API
declare module "cloudflare:sockets" {
    interface Socket {
        readable: ReadableStream<Uint8Array>;
        writable: WritableStream<Uint8Array>;
        startTls(): Socket;
        close(): void;
    }
    export function connect(address: string | { hostname: string; port: number }, options?: object): Socket;
}
