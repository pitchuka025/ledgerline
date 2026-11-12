import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowLeftRight, ArrowUpRight, BadgeCheck, CircleHelp, Coins, Shield,
} from "lucide-react";
import { api, idempotencyKey } from "./api.js";
import Dashboard from "./Dashboard.jsx";

function readStoredSession() {
  try {
    const stored = JSON.parse(sessionStorage.getItem("ledgerline-session"));
    if (stored && typeof stored.token === "string" && typeof stored.email === "string"
      && ["CUSTOMER", "ADMIN", "AUDITOR"].includes(stored.role)) {
      return stored;
    }
  } catch {
  }
  sessionStorage.removeItem("ledgerline-session");
  return null;
}

export default function App() {
  const [session, setSession] = useState(readStoredSession);
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [ledgerEntries, setLedgerEntries] = useState([]);
  const [audit, setAudit] = useState([]);
  const [users, setUsers] = useState([]);
  const [schedules, setSchedules] = useState([]);
  const [profile, setProfile] = useState({ email: session?.email || "", displayName: "" });
  const [preferences, setPreferences] = useState({
    loginAlertsEnabled: true,
    emailNotificationsEnabled: true,
    hideBalances: false,
  });
  const [receipt, setReceipt] = useState(null);
  const [activeProof, setActiveProof] = useState(null);
  const [proofLoading, setProofLoading] = useState(false);
  const [activeAccount, setActiveAccount] = useState("");
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [serviceStatus, setServiceStatus] = useState({ checking: true, connected: false, message: "Checking backend…" });
  const [authMode, setAuthMode] = useState("login");
  const [movementKind, setMovementKind] = useState("transfer");
  const [movementOpen, setMovementOpen] = useState(false);
  const [hideBalances, setHideBalances] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");

  const logout = useCallback(() => {
    sessionStorage.removeItem("ledgerline-session");
    setSession(null);
    setAccounts([]);
    setTransactions([]);
    setLedgerEntries([]);
    setAudit([]);
    setUsers([]);
    setSchedules([]);
    setActiveProof(null);
    setActiveAccount("");
    setReceipt(null);
  }, []);

  const refresh = useCallback(async () => {
    if (!session?.token) return;
    setLoading(true);
    setError("");
    try {
      const [nextAccounts, nextTransactions, nextLedgerEntries] = await Promise.all([
        api("/accounts", { token: session.token }),
        api("/transactions", { token: session.token }),
        api("/ledger", { token: session.token }),
      ]);
      const [nextProfile, nextPreferences] = await Promise.all([
        api("/profile", { token: session.token }),
        api("/profile/preferences", { token: session.token }),
      ]);
      setAccounts(nextAccounts);
      setTransactions(nextTransactions);
      setLedgerEntries(nextLedgerEntries);
      setProfile(nextProfile);
      setPreferences(nextPreferences);
      setHideBalances(nextPreferences.hideBalances);
      setActiveAccount((current) => nextAccounts.some((item) => item.id === current)
        ? current : (nextAccounts[0]?.id || ""));
      const [nextAudit, nextUsers, nextSchedules] = await Promise.all([
        session.role !== "CUSTOMER" ? api("/audit", { token: session.token }) : Promise.resolve([]),
        session.role === "ADMIN" ? api("/admin/users", { token: session.token }) : Promise.resolve([]),
        session.role === "CUSTOMER" ? api("/scheduled-transfers", { token: session.token }) : Promise.resolve([]),
      ]);
      setAudit(nextAudit);
      setUsers(nextUsers);
      setSchedules(nextSchedules);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    if (session?.token) refresh();
  }, [session?.token, refresh]);

  useEffect(() => {
    let active = true;
    async function checkServices() {
      try {
        const health = await api("/health");
        if (active) {
          setServiceStatus({
            checking: false,
            connected: health.status === "UP" && health.database === "UP",
            message: health.database === "UP"
              ? "Backend and database connected"
              : health.status === "UP"
                ? "Backend needs restart to load the latest version"
                : "Backend is not ready",
          });
        }
      } catch (requestError) {
        if (active) {
          setServiceStatus({ checking: false, connected: false, message: requestError.message });
        }
      }
    }
    checkServices();
    const timer = window.setInterval(checkServices, 15000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      setError("Your session expired. Please sign in again.");
      logout();
    };
    window.addEventListener("ledgerline:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("ledgerline:unauthorized", handleUnauthorized);
  }, [logout]);

  const selectedAccount = useMemo(
    () => accounts.find((account) => account.id === activeAccount),
    [accounts, activeAccount],
  );

  function openMovement(kind) {
    if (session.role !== "CUSTOMER") {
      setError("Only customer accounts can make deposits, withdrawals, and transfers.");
      return;
    }
    if (!selectedAccount) {
      setError("Select an account before starting a money movement.");
      return;
    }
    setMovementKind(kind);
    setMovementOpen(true);
    setError("");
    setNotice("");
  }

  async function submitAuth(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setError("");
    setLoading(true);
    try {
      const result = await api(`/auth/${authMode === "register" ? "register" : "login"}`, {
        method: "POST",
        body: JSON.stringify({ email: form.get("email"), password: form.get("password") }),
      });
      setSession(result);
      sessionStorage.setItem("ledgerline-session", JSON.stringify(result));
      setNotice(authMode === "register" ? "Your account is ready." : "Welcome back.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function submitMovement(event) {
    event.preventDefault();
    if (session.role !== "CUSTOMER" || !selectedAccount) {
      setError("Select a customer account before starting a money movement.");
      return;
    }
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const kind = form.get("kind");
    const amount = form.get("amount");
    const description = form.get("description");
    const destinationAccountNumber = form.get("destinationAccountNumber");
    const endpoint = kind === "transfer"
      ? `/accounts/${activeAccount}/transfer`
      : `/accounts/${activeAccount}/${kind}`;
    setError("");
    setNotice("");
    setLoading(true);
    try {
      const body = kind === "transfer"
        ? { amount, description, destinationAccountNumber }
        : { amount, description };
      const result = await api(endpoint, {
        token: session.token,
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey() },
        body: JSON.stringify(body),
      });
      setMovementOpen(false);
      setReceipt(result);
      await refresh();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function createAccount(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError("");
    setLoading(true);
    try {
      const path = session.role === "ADMIN" ? "/admin/accounts" : "/accounts";
      const body = session.role === "ADMIN"
        ? { email: form.get("email"), currency: "USD" }
        : { currency: "USD" };
      await api(path, { token: session.token, method: "POST", body: JSON.stringify(body) });
      formElement.reset();
      setNotice("Account created.");
      await refresh();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function openProof(reference) {
    setError("");
    setProofLoading(true);
    setActiveProof(null);
    try {
      setActiveProof(await api(`/transactions/${encodeURIComponent(reference)}/proof`, {
        token: session.token,
      }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setProofLoading(false);
    }
  }

  async function saveProfile(values) {
    setError("");
    setLoading(true);
    try {
      const updatedProfile = await api("/profile", {
        token: session.token,
        method: "PUT",
        body: JSON.stringify(values),
      });
      setProfile(updatedProfile);
      const nextSession = { ...session, email: updatedProfile.email };
      setSession(nextSession);
      sessionStorage.setItem("ledgerline-session", JSON.stringify(nextSession));
      setNotice("Profile updated.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function savePreferences(values) {
    setError("");
    setLoading(true);
    try {
      const updated = await api("/profile/preferences", {
        token: session.token,
        method: "PUT",
        body: JSON.stringify({
          loginAlertsEnabled: Boolean(values.loginAlertsEnabled),
          emailNotificationsEnabled: Boolean(values.emailNotificationsEnabled),
          hideBalances: Boolean(values.hideBalances),
        }),
      });
      setPreferences(updated);
      setHideBalances(updated.hideBalances);
      setNotice("Preferences saved.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function changePassword(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setError("");
    setLoading(true);
    try {
      await api("/profile/password", {
        token: session.token,
        method: "POST",
        body: JSON.stringify({
          currentPassword: form.get("currentPassword"),
          newPassword: form.get("newPassword"),
        }),
      });
      formElement.reset();
      setNotice("Password updated.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function createUser(values, formElement) {
    setError("");
    setLoading(true);
    try {
      await api("/admin/users", {
        token: session.token,
        method: "POST",
        body: JSON.stringify(values),
      });
      formElement.reset();
      setNotice("User created. Share the temporary password securely.");
      setUsers(await api("/admin/users", { token: session.token }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function updateUserRole(userId, role) {
    setError("");
    setLoading(true);
    try {
      const updatedUser = await api(`/admin/users/${userId}/role`, {
        token: session.token,
        method: "PATCH",
        body: JSON.stringify({ role }),
      });
      setUsers((current) => current.map((user) => user.id === userId ? updatedUser : user));
      setNotice("User role updated and recorded in the audit log.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function createSchedule(values, formElement) {
    setError("");
    setLoading(true);
    try {
      await api("/scheduled-transfers", {
        token: session.token,
        method: "POST",
        body: JSON.stringify(values),
      });
      formElement.reset();
      setNotice("Scheduled transfer saved.");
      setSchedules(await api("/scheduled-transfers", { token: session.token }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function cancelSchedule(scheduleId) {
    setError("");
    setLoading(true);
    try {
      await api(`/scheduled-transfers/${scheduleId}/cancel`, {
        token: session.token,
        method: "POST",
      });
      setNotice("Scheduled transfer canceled.");
      setSchedules(await api("/scheduled-transfers", { token: session.token }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  if (!session?.token) {
    return <AuthScreen mode={authMode} setMode={setAuthMode} onSubmit={submitAuth}
      loading={loading} error={error} serviceStatus={serviceStatus} />;
  }

  return <Dashboard
    session={session} accounts={accounts} transactions={transactions} ledgerEntries={ledgerEntries} audit={audit}
    activeAccount={activeAccount} setActiveAccount={setActiveAccount}
    loading={loading} notice={notice} error={error} setNotice={setNotice} setError={setError}
    refresh={refresh} logout={logout} createAccount={createAccount} openMovement={openMovement}
    openProof={openProof} proofLoading={proofLoading} activeProof={activeProof}
    setActiveProof={setActiveProof} movementOpen={movementOpen} setMovementOpen={setMovementOpen}
    movementKind={movementKind} submitMovement={submitMovement}
    hideBalances={hideBalances} setHideBalances={setHideBalances}
    searchTerm={searchTerm} setSearchTerm={setSearchTerm}
    profile={profile} preferences={preferences} users={users} schedules={schedules} receipt={receipt}
    saveProfile={saveProfile} savePreferences={savePreferences} changePassword={changePassword}
    createUser={createUser} updateUserRole={updateUserRole} createSchedule={createSchedule}
    cancelSchedule={cancelSchedule} closeReceipt={() => setReceipt(null)} />;
}

function AuthScreen({ mode, setMode, onSubmit, loading, error, serviceStatus }) {
  const registering = mode === "register";
  return <main className="auth-page">
    <div className="auth-brand"><span className="brand-mark"><Coins size={19} /></span>
      <span>ledgerline<span className="brand-period">.</span></span></div>
    <div className="auth-layout">
      <section className="auth-story">
        <span className="eyebrow"><span className="live-dot" /> MONEY MOVEMENT, MADE ACCOUNTABLE</span>
        <h1>Every balance<br />has a <em>paper trail.</em></h1>
        <p>Move money with confidence. Each transaction is authorised, protected against duplicates, and recorded twice — so the books always balance.</p>
        <div className="story-points">
          <div><span className="point-icon"><BadgeCheck size={17} /></span><span><strong>One request, one payment</strong><small>Idempotency protection built in.</small></span></div>
          <div><span className="point-icon"><ArrowLeftRight size={17} /></span><span><strong>Books that balance</strong><small>Every movement has two ledger entries.</small></span></div>
          <div><span className="point-icon"><Shield size={17} /></span><span><strong>Your money stays yours</strong><small>Ownership and permissions checked server-side.</small></span></div>
        </div>
        <div className="auth-orbit"><span /><span /><span /><div><Coins size={31} /></div></div>
      </section>
      <section className="auth-card">
        <span className="section-kicker">{registering ? "START YOUR LEDGER" : "YOUR MONEY, IN VIEW"}</span>
        <h2>{registering ? "Create your account" : "Welcome back"}</h2>
        <p className="auth-subtitle">{registering ? "Your secure account is one small step away." : "Sign in to pick up right where you left off."}</p>
        <div className={`auth-service-status${serviceStatus.connected ? " is-connected" : ""}`}
          role="status" aria-live="polite">
          <span />{serviceStatus.checking ? "Checking backend…" : serviceStatus.message}
        </div>
        {error && <div className="feedback feedback-error" role="alert">{error}</div>}
        <form className="auth-form" onSubmit={onSubmit}>
          <label htmlFor="email">Email address</label>
          <input id="email" name="email" type="email" autoComplete="email" placeholder="you@example.com"
            maxLength="320" required />
          <label htmlFor="password">Password</label>
          <input id="password" name="password" type="password"
            autoComplete={registering ? "new-password" : "current-password"}
            placeholder={registering ? "At least 12 characters" : "Your password"}
            minLength={registering ? 12 : 1} maxLength="72" required />
          <button className="primary-button auth-submit" disabled={loading}>
            {loading ? "Please wait…" : registering ? "Create secure account" : "Sign in securely"}
            <ArrowUpRight size={16} />
          </button>
        </form>
        <div className="auth-switch">{registering ? "Already have an account?" : "New to LedgerLine?"}
          <button onClick={() => setMode(registering ? "login" : "register")}>
            {registering ? "Sign in" : "Create account"}
          </button>
        </div>
        <div className="auth-assurance"><Shield size={14} /> Passwords are protected with BCrypt. Sessions expire automatically.</div>
      </section>
    </div>
    <div className="auth-footer"><span>© LedgerLine</span><span>Trust, posted in two places.</span>
      <span><CircleHelp size={13} /> Secure by design</span></div>
  </main>;
}
