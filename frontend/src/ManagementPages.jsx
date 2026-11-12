import { useState } from "react";
import {
  CalendarClock, CheckCircle2, Download, LockKeyhole, Plus, Shield, UserRound, UsersRound,
} from "lucide-react";

const money = (amount, currency = "USD") =>
  new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number(amount || 0));
const dateTime = (value) => new Intl.DateTimeFormat("en", {
  dateStyle: "medium", timeStyle: "short",
}).format(new Date(value));

export function ScheduledTransfersPage({ accounts, schedules, loading, onCreate, onCancel }) {
  const [formOpen, setFormOpen] = useState(false);
  const [sourceAccountId, setSourceAccountId] = useState("");

  function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    onCreate({
      sourceAccountId: form.get("sourceAccountId"),
      destinationAccountId: form.get("destinationAccountId"),
      amount: form.get("amount"),
      scheduledAt: new Date(form.get("scheduledAt")).toISOString(),
    }, event.currentTarget);
  }

  return <div className="page-stack">
    <section className="bank-card transactions-page-card">
      <div className="card-heading">
        <div><h2>Scheduled transfers</h2>
          <p>One-time local ledger transfers. Pending schedules execute automatically when due.</p></div>
        <button className="primary-button" type="button" onClick={() => setFormOpen((open) => !open)}
          disabled={!accounts.length || loading}><Plus size={16} /> Schedule transfer</button>
      </div>
      {formOpen && <form className="management-form" onSubmit={submit}>
        <label>From account
          <select name="sourceAccountId" required value={sourceAccountId}
            onChange={(event) => setSourceAccountId(event.target.value)}>
            <option value="" disabled>Select an account</option>
            {accounts.map((account) => <option key={account.id} value={account.id}>
              {account.accountNumber} · {money(account.balance, account.currency)}
            </option>)}
          </select>
        </label>
        <label>To account
          <select name="destinationAccountId" required defaultValue="">
            <option value="" disabled>Select an account</option>
            {accounts.filter((account) => account.id !== sourceAccountId).map((account) => <option key={account.id} value={account.id}>
              {account.accountNumber} · {money(account.balance, account.currency)}
            </option>)}
          </select>
        </label>
        <label>Amount (USD)<input name="amount" type="number" min="0.01" step="0.01" required /></label>
        <label>Run at<input name="scheduledAt" type="datetime-local" required
          min={localDateTimeMinimum()} /></label>
        <div className="management-form-actions">
          <button type="button" className="secondary-button" onClick={() => setFormOpen(false)}>Cancel</button>
          <button type="submit" className="primary-button" disabled={loading}>
            <CalendarClock size={16} /> Save schedule
          </button>
        </div>
      </form>}
      {!schedules.length ? <div className="empty-panel"><CalendarClock size={24} />
        <strong>No scheduled transfers</strong><p>Create a one-time transfer to run at a future date and time.</p>
      </div> : <div className="transaction-table-wrap"><table className="bank-table">
        <thead><tr><th>Scheduled for</th><th>From account</th><th>To account</th><th>Amount</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>{schedules.map((schedule) => <tr key={schedule.id}>
          <td>{dateTime(schedule.scheduledAt)}</td>
          <td><code>{accounts.find((account) => account.id === schedule.sourceAccountId)?.accountNumber
            || schedule.sourceAccountId}</code></td>
          <td><code>{accounts.find((account) => account.id === schedule.destinationAccountId)?.accountNumber
            || schedule.destinationAccountId}</code></td>
          <td>{money(schedule.amount)}</td>
          <td><span className="status-pill">{schedule.status}</span></td>
          <td>{schedule.status === "PENDING" && <button className="table-proof-button"
            type="button" disabled={loading} onClick={() => onCancel(schedule.id)}>Cancel</button>}
            {schedule.failureReason && <small className="schedule-error">{schedule.failureReason}</small>}
          </td>
        </tr>)}</tbody>
      </table></div>}
    </section>
    <p className="data-limit-note"><Shield size={15} /> This local demo records transfers in LedgerLine only; no bank or payment network is contacted.</p>
  </div>;
}

function localDateTimeMinimum() {
  const minimum = new Date(Date.now() + 60_000);
  minimum.setMinutes(minimum.getMinutes() - minimum.getTimezoneOffset());
  return minimum.toISOString().slice(0, 16);
}

export function SecurityPage({ onChangePassword, preferences, onSavePreferences, loading }) {
  return <div className="page-stack">
    <section className="bank-card settings-card">
      <div className="card-heading"><div><h2>Security center</h2>
        <p>Manage password and local account alert preferences.</p></div><Shield size={22} className="secure-icon" /></div>
      <form className="security-password-form" onSubmit={onChangePassword}>
        <h3><LockKeyhole size={17} /> Change password</h3>
        <label>Current password<input name="currentPassword" type="password" autoComplete="current-password" required /></label>
        <label>New password<input name="newPassword" type="password" minLength={12} maxLength={72}
          autoComplete="new-password" required /><small>Use 12 to 72 characters.</small></label>
        <button className="primary-button" type="submit" disabled={loading}>Update password</button>
      </form>
      <div className="security-status-row">
        <span><LockKeyhole size={18} /></span><div><strong>Two-factor authentication</strong>
          <small>Not enabled. This app does not yet support second-factor verification.</small></div>
        <span className="status-pill">Unavailable</span>
      </div>
      <label className="preference-toggle"><span><strong>Login alerts</strong>
        <small>Store your preference for security notifications.</small></span>
        <input type="checkbox" checked={preferences.loginAlertsEnabled}
          onChange={(event) => onSavePreferences({ ...preferences, loginAlertsEnabled: event.target.checked })}
          disabled={loading} />
      </label>
    </section>
  </div>;
}

export function UsersPage({ users, loading, onCreate, onUpdateRole }) {
  const [formOpen, setFormOpen] = useState(false);

  function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    onCreate({
      email: form.get("email"),
      displayName: form.get("displayName"),
      role: form.get("role"),
      password: form.get("password"),
    }, event.currentTarget);
  }

  return <div className="page-stack">
    <section className="bank-card transactions-page-card">
      <div className="card-heading"><div><h2>Users and roles</h2>
        <p>Administrator-only account directory. Role changes are recorded in the audit log.</p></div>
        <button className="primary-button" type="button" onClick={() => setFormOpen((open) => !open)}>
          <Plus size={16} /> Add user
        </button>
      </div>
      {formOpen && <form className="management-form" onSubmit={submit}>
        <label>Email<input type="email" name="email" required maxLength={320} /></label>
        <label>Display name<input name="displayName" required maxLength={100} /></label>
        <label>Role<select name="role" defaultValue="CUSTOMER">
          <option value="CUSTOMER">Customer</option><option value="AUDITOR">Auditor</option>
        </select></label>
        <label>Temporary password<input name="password" type="password" minLength={12} maxLength={72}
          autoComplete="new-password" required /></label>
        <div className="management-form-actions">
          <button type="button" className="secondary-button" onClick={() => setFormOpen(false)}>Cancel</button>
          <button type="submit" className="primary-button" disabled={loading}>Create user</button>
        </div>
      </form>}
      {users.length ? <div className="transaction-table-wrap"><table className="bank-table">
        <thead><tr><th>User</th><th>Email</th><th>Role</th><th>Created</th><th>Change role</th></tr></thead>
        <tbody>{users.map((user) => <tr key={user.id}>
          <td><span className="management-user"><UserRound size={15} />{user.displayName || user.email.split("@")[0]}</span></td>
          <td>{user.email}</td><td><span className="transaction-type type-transfer">{user.role}</span></td>
          <td>{dateTime(user.createdAt)}</td>
          <td><select aria-label={`Role for ${user.email}`} value={user.role}
            onChange={(event) => onUpdateRole(user.id, event.target.value)}
            disabled={loading || user.role === "ADMIN"}>
            <option value="CUSTOMER">Customer</option><option value="AUDITOR">Auditor</option><option value="ADMIN">Admin</option>
          </select></td>
        </tr>)}</tbody>
      </table></div> : <div className="empty-panel"><UsersRound size={24} />
        <strong>No users found</strong><p>Users created in this LedgerLine database will appear here.</p>
      </div>}
    </section>
  </div>;
}

export function SettingsPage({ profile, preferences, loading, onSaveProfile, onSavePreferences }) {
  function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    onSaveProfile({ displayName: form.get("displayName"), email: form.get("email") });
  }

  return <div className="page-stack">
    <section className="bank-card settings-card">
      <div className="card-heading"><div><h2>Profile settings</h2>
        <p>These details are stored by the LedgerLine backend.</p></div></div>
      <form className="management-form profile-form" key={`${profile.email}:${profile.displayName}`} onSubmit={submit}>
        <label>Full name<input name="displayName" required maxLength={100} defaultValue={profile.displayName} /></label>
        <label>Email address<input name="email" type="email" required maxLength={320} defaultValue={profile.email} /></label>
        <div className="management-form-actions">
          <button className="primary-button" type="submit" disabled={loading}>Save profile</button>
        </div>
      </form>
      <div className="settings-preferences">
        <h3>Preferences</h3>
        <label className="preference-toggle"><span><strong>Hide balances</strong>
          <small>Mask balance values in this account.</small></span>
          <input type="checkbox" checked={preferences.hideBalances}
            onChange={(event) => onSavePreferences({ ...preferences, hideBalances: event.target.checked })}
            disabled={loading} />
        </label>
        <label className="preference-toggle"><span><strong>Login alerts</strong>
          <small>Save your preference for security notifications.</small></span>
          <input type="checkbox" checked={preferences.loginAlertsEnabled}
            onChange={(event) => onSavePreferences({ ...preferences, loginAlertsEnabled: event.target.checked })}
            disabled={loading} />
        </label>
        <label className="preference-toggle"><span><strong>Email notifications</strong>
          <small>Save your preference for account activity updates.</small></span>
          <input type="checkbox" checked={preferences.emailNotificationsEnabled}
            onChange={(event) => onSavePreferences({ ...preferences, emailNotificationsEnabled: event.target.checked })}
            disabled={loading} />
        </label>
      </div>
    </section>
  </div>;
}

export function TransferSuccessDialog({ receipt, onClose }) {
  if (!receipt) return null;
  return <div className="movement-overlay" role="presentation" onMouseDown={(event) => {
    if (event.target === event.currentTarget) onClose();
  }}>
    <section className="movement-dialog receipt-dialog" role="dialog" aria-modal="true" aria-labelledby="receipt-title">
      <span className="receipt-success-icon"><CheckCircle2 size={32} /></span>
      <span className="section-kicker">LEDGERLINE LOCAL LEDGER</span>
      <h2 id="receipt-title">Transaction recorded</h2>
      <p>Reference: <code>{receipt.reference}</code></p>
      <strong className="receipt-amount">{money(receipt.amount)}</strong>
      <small>No external bank or payment network was used.</small>
      <div className="management-form-actions">
        <button type="button" className="secondary-button"
          onClick={() => downloadReceipt(receipt)}><Download size={15} /> Download receipt</button>
        <button type="button" className="primary-button" onClick={onClose}>Done</button>
      </div>
    </section>
  </div>;
}

function downloadReceipt(receipt) {
  const lines = [
    "LedgerLine local ledger receipt",
    `Reference,${receipt.reference}`,
    `Type,${receipt.type}`,
    `Amount,${receipt.amount} USD`,
    `Description,${receipt.description || ""}`,
    `Created at,${receipt.createdAt}`,
    "No external bank/payment network was used.",
  ];
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8" }));
  link.download = `ledgerline-${receipt.reference}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}
