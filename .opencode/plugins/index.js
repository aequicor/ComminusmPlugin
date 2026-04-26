// src/index.ts
import { WebSocketServer, WebSocket } from "ws";
var WsDiscoveryServer = class {
  wss = null;
  registeredServers = /* @__PURE__ */ new Map();
  options;
  constructor(options) {
    this.options = { host: "0.0.0.0", ...options };
  }
  start() {
    return new Promise((resolve, reject) => {
      try {
        this.wss = new WebSocketServer({
          host: this.options.host,
          port: this.options.port
        });
        this.wss.on("listening", () => {
          resolve();
        });
        this.wss.on("connection", (ws) => {
          ws.on("message", (data) => {
            try {
              const message = JSON.parse(data.toString());
              this.handleMessage(ws, message);
            } catch (error) {
            }
          });
          ws.on("close", () => {
            this.removeServer(ws);
          });
        });
        this.wss.on("error", (error) => {
          reject(error);
        });
      } catch (error) {
        reject(error);
      }
    });
  }
  stop() {
    return new Promise((resolve) => {
      if (!this.wss) {
        resolve();
        return;
      }
      this.registeredServers.clear();
      this.wss.close(() => {
        this.wss = null;
        resolve();
      });
    });
  }
  handleMessage(ws, message) {
    if (message.type === "handshake") {
      if (message.action === "register") {
        this.registerServer(ws, message.payload);
      } else if (message.action === "discover") {
        this.sendServerList(ws);
      }
    }
  }
  registerServer(ws, payload) {
    if (!payload) return;
    try {
      const url2 = new URL(payload.serverUrl);
      const serverInfo = {
        host: url2.hostname,
        port: parseInt(url2.port, 10) || 4096,
        baseUrl: payload.serverUrl,
        pid: payload.pid,
        username: payload.username,
        password: payload.password
      };
      this.registeredServers.set(ws, serverInfo);
      ws.send(JSON.stringify({ type: "handshake", action: "registered" }));
    } catch {
      ws.send(JSON.stringify({ type: "error", message: "Invalid server URL" }));
    }
  }
  removeServer(ws) {
    this.registeredServers.delete(ws);
  }
  sendServerList(ws) {
    const servers = Array.from(this.registeredServers.values());
    ws.send(JSON.stringify({ type: "handshake", action: "discovered", servers }));
  }
  getServers() {
    return Array.from(this.registeredServers.values());
  }
  getServerCount() {
    return this.registeredServers.size;
  }
};
var serverInstance = null;
async function createWsDiscoveryServer(options) {
  if (serverInstance) {
    await serverInstance.stop();
  }
  serverInstance = new WsDiscoveryServer(options);
  await serverInstance.start();
  return serverInstance;
}
function getWsDiscoveryServer() {
  return serverInstance;
}
async function stopWsDiscoveryServer() {
  if (serverInstance) {
    await serverInstance.stop();
    serverInstance = null;
  }
}
var WsDiscoveryClient = class {
  ws = null;
  options;
  reconnectTimer = null;
  registered = false;
  constructor(options) {
    this.options = {
      autoReconnect: true,
      reconnectInterval: 5e3,
      ...options
    };
  }
  connect() {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.options.wsUrl);
        this.ws.on("open", () => {
          this.register();
          resolve();
        });
        this.ws.on("message", (data) => {
          try {
            const message = JSON.parse(data.toString());
            this.handleMessage(message);
          } catch {
          }
        });
        this.ws.on("close", () => {
          this.registered = false;
          this.scheduleReconnect();
        });
        this.ws.on("error", (error) => {
          reject(error);
        });
      } catch (error) {
        reject(error);
      }
    });
  }
  disconnect() {
    this.options.autoReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
  register() {
    if (!this.ws || this.registered) return;
    const message = {
      type: "handshake",
      action: "register",
      payload: {
        serverUrl: this.options.serverUrl,
        pid: this.options.pid,
        username: this.options.username,
        password: this.options.password
      }
    };
    if (this.options.secret) {
      message.secret = this.options.secret;
    }
    this.ws.send(JSON.stringify(message));
  }
  handleMessage(message) {
    if (message.type === "handshake" && message.action === "registered") {
      this.registered = true;
    }
  }
  scheduleReconnect() {
    if (!this.options.autoReconnect) return;
    this.reconnectTimer = setTimeout(() => {
      this.connect().catch();
    }, this.options.reconnectInterval);
  }
  isConnected() {
    return this.ws?.readyState === WebSocket.OPEN;
  }
  isRegistered() {
    return this.registered;
  }
};
var clientInstance = null;
async function createWsDiscoveryClient(options) {
  if (clientInstance) {
    clientInstance.disconnect();
  }
  clientInstance = new WsDiscoveryClient(options);
  await clientInstance.connect();
  return clientInstance;
}
function getWsDiscoveryClient() {
  return clientInstance;
}
async function stopWsDiscoveryClient() {
  if (clientInstance) {
    clientInstance.disconnect();
    clientInstance = null;
  }
}
function getArgValue(name) {
  for (let i = 0; i < process.argv.length; i++) {
    const arg = process.argv[i];
    const inlined = arg.match(new RegExp(`^--${name}=(.+)$`));
    if (inlined) return inlined[1];
    if (arg === `--${name}` && process.argv[i + 1]) return process.argv[i + 1];
  }
  return void 0;
}
var wsDiscoveryUrl = process.env.OPENCODE_WS_DISCOVERY_URL || "ws://localhost:8765";
var hostname = getArgValue("hostname") ?? "localhost";
var port = getArgValue("port") ?? process.env.OPENCODE_PORT ?? "4096";
var serverUrl = process.env.OPENCODE_SERVER_URL || `http://${hostname}:${port}`;
var username = process.env.OPENCODE_SERVER_USERNAME ?? getArgValue("username") ?? "opencode";
var password = process.env.OPENCODE_SERVER_PASSWORD ?? process.env.OPENCODE_SECRET ?? process.env.OPENCODE_PASSWORD ?? process.env.OPENCODE_AUTH_TOKEN ?? getArgValue("password") ?? getArgValue("secret");
var wsDiscoverySecret = process.env.OPENCODE_WS_DISCOVERY_SECRET ?? process.env.WS_DISCOVERY_SECRET;
createWsDiscoveryClient({
  wsUrl: wsDiscoveryUrl,
  serverUrl,
  pid: process.pid,
  username,
  password,
  secret: wsDiscoverySecret,
  reconnectInterval: 3e3
}).catch(() => {
});
var url = await new Promise((resolve) => {
  const ipcServer = new WebSocketServer({ host: "127.0.0.1", port: 0 });
  ipcServer.on("listening", () => {
    const addr = ipcServer.address();
    resolve(`ws://127.0.0.1:${addr.port}`);
  });
});
export {
  createWsDiscoveryClient,
  createWsDiscoveryServer,
  getWsDiscoveryClient,
  getWsDiscoveryServer,
  stopWsDiscoveryClient,
  stopWsDiscoveryServer,
  url
};
