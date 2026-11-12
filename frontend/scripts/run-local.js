import { spawn } from "node:child_process";
import { access } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, "..", "..");
const frontendDirectory = path.join(projectDirectory, "frontend");
const backendDirectory = path.join(projectDirectory, "backend", "ledgerline");
const viteEntry = path.join(frontendDirectory, "node_modules", "vite", "bin", "vite.js");
const backendHealthUrl = "http://127.0.0.1:8080/api/health";
const frontendUrl = "http://localhost:5173";
const children = [];
let stopping = false;

async function isReady(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(1500) });
    return response.ok;
  } catch {
    return false;
  }
}

async function backendHealth() {
  try {
    const response = await fetch(backendHealthUrl, { signal: AbortSignal.timeout(5000) });
    const body = await response.json();
    return { reachable: true, ready: response.ok && body.status === "UP" && body.database === "UP", body };
  } catch {
    return { reachable: false, ready: false, body: null };
  }
}

function stopChildren() {
  if (stopping) return;
  stopping = true;
  for (const child of children) {
    if (child.exitCode === null) child.kill();
  }
}

function startService(command, args, cwd, label, options = {}) {
  const child = spawn(command, args, {
    cwd,
    stdio: "inherit",
    windowsHide: true,
    ...options,
  });
  children.push(child);
  child.once("error", (error) => {
    console.error(`Could not start ${label}: ${error.message}`);
    process.exitCode = 1;
    stopChildren();
  });
  child.once("exit", (code) => {
    if (stopping) return;
    console.error(`${label} exited${code === null ? "" : ` with code ${code}`}. Stopping the other service.`);
    process.exitCode = code ?? 1;
    stopChildren();
  });
  return child;
}

async function waitForBackend(child) {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    if ((await backendHealth()).ready) return;
    if (child.exitCode !== null) {
      throw new Error("The backend stopped before its health and database checks passed. Check the Maven output above.");
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error("The backend and database did not become ready within 2 minutes. Check the Maven output above.");
}

async function main() {
  const health = await backendHealth();
  const backendAlreadyRunning = health.ready;
  if (health.reachable && !health.ready && health.body?.database === "DOWN") {
    throw new Error("The backend is running, but PostgreSQL is unavailable. Check PostgreSQL and DB_PASSWORD, then restart the backend.");
  }
  if (health.reachable && !health.ready) {
    throw new Error("An older backend is responding on port 8080 without database readiness checks. Stop it, then run this launcher again.");
  }
  if (!backendAlreadyRunning && !process.env.DB_PASSWORD) {
    throw new Error("DB_PASSWORD is not set. Set it in this PowerShell window, then run npm run dev:all again.");
  }

  const frontendAlreadyRunning = await isReady(frontendUrl);

  if (backendAlreadyRunning) {
    console.log("Backend is already running at http://localhost:8080; reusing it.");
  } else {
    console.log("Starting Spring Boot backend...");
    const mavenCommand = process.platform === "win32" ? "mvn.cmd" : "mvn";
    const backendProcess = startService(
      mavenCommand,
      ["spring-boot:run"],
      backendDirectory,
      "Backend",
      process.platform === "win32" ? { shell: true } : {},
    );
    await waitForBackend(backendProcess);
  }

  if (frontendAlreadyRunning) {
    console.log("Frontend is already responding at http://localhost:5173; reusing it.");
  } else {
    await access(viteEntry);
    console.log("Starting Vite frontend...");
    startService(process.execPath, [viteEntry, "--host", "localhost"], frontendDirectory, "Frontend");
  }

  console.log("LedgerLine is available at http://localhost:5173");
  console.log("Backend health through the frontend proxy: http://localhost:5173/api/health");
  console.log("Press Ctrl+C to stop services started by this launcher.");
}

process.on("SIGINT", () => {
  stopChildren();
  process.exitCode = 130;
});
process.on("SIGTERM", () => {
  stopChildren();
  process.exitCode = 143;
});

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
  stopChildren();
});
