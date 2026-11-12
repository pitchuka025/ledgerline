import { useState } from "react";
import {
  Activity, ArrowDownLeft, ArrowLeftRight, ArrowRight, ArrowUpRight, BadgeCheck,
  BookOpen, Building2, CalendarClock, CalendarDays, ChartNoAxesCombined, ChevronDown,
  CircleHelp, Download, FileText, LayoutDashboard, LogOut, Menu, Plus, ReceiptText, RefreshCw, Search, Settings,
  Shield, ShieldCheck, Sparkles, TrendingDown, TrendingUp, UsersRound, Wallet, X,
} from "lucide-react";
import {
  ScheduledTransfersPage, SecurityPage, SettingsPage, TransferSuccessDialog, UsersPage,
} from "./ManagementPages.jsx";

const navigation = [
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard, available: true },
  { id: "accounts", label: "Accounts", icon: Building2, available: true },
  { id: "transfer", label: "Transfer", icon: ArrowLeftRight, available: true },
  { id: "transactions", label: "Transactions", icon: ReceiptText, available: true },
  { id: "ledger", label: "Ledger", icon: BookOpen, available: true },
  { id: "analytics", label: "Analytics", icon: ChartNoAxesCombined, available: true },
  { id: "scheduled", label: "Scheduled Transfers", icon: CalendarClock, available: true, roles: ["CUSTOMER"] },
  { id: "security", label: "Security", icon: ShieldCheck, available: true },
  { id: "audit", label: "Audit Logs", icon: FileText, available: true, roles: ["ADMIN", "AUDITOR"] },
  { id: "users", label: "Users & Roles", icon: UsersRound, available: true, roles: ["ADMIN"] },
  { id: "settings", label: "Settings", icon: Settings, available: true },
];

const money = (amount, currency = "USD") =>
  new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number(amount || 0));
const shortDate = (value) => new Intl.DateTimeFormat("en", {
  month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit",
}).format(new Date(value));

export default function Dashboard({
  session, accounts, transactions, ledgerEntries, audit, activeAccount, setActiveAccount,
  loading, notice, error, setNotice, setError, refresh, logout, createAccount,
  openMovement, openProof, proofLoading, activeProof, setActiveProof,
  movementOpen, setMovementOpen, movementKind, submitMovement,
  hideBalances, setHideBalances, searchTerm, setSearchTerm,
  profile, preferences, users, schedules, receipt,
  saveProfile, savePreferences, changePassword, createUser, updateUserRole,
  createSchedule, cancelSchedule, closeReceipt,
}) {
  const [activePage, setActivePage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const selectedAccount = accounts.find((account) => account.id === activeAccount);
  const totalBalance = accounts.reduce((sum, account) => sum + Number(account.balance), 0);
  const visibleNavigation = navigation.filter((item) => !item.roles || item.roles.includes(session.role));
  const filteredAccounts = accounts.filter((account) =>
    `${account.accountNumber} ${account.currency}`.toLowerCase().includes(searchTerm.toLowerCase()));
  const filteredTransactions = transactions.filter((transaction) =>
    `${transaction.reference} ${transaction.description || ""} ${transaction.type} ${transaction.amount}`
      .toLowerCase().includes(searchTerm.toLowerCase()));
  const filteredLedgerEntries = ledgerEntries.filter((entry) =>
    `${entry.reference} ${entry.accountRef} ${entry.entryType} ${entry.amount}`
      .toLowerCase().includes(searchTerm.toLowerCase()));
  const monthStart = new Date();
  monthStart.setDate(1);
  monthStart.setHours(0, 0, 0, 0);
  const monthTransactions = transactions.filter((transaction) =>
    new Date(transaction.createdAt) >= monthStart);
  const accountIds = new Set(accounts.map((account) => account.id));
  const monthlyInflow = monthTransactions
    .filter((transaction) => transaction.type === "DEPOSIT"
      || (transaction.type === "TRANSFER" && accountIds.has(transaction.destinationAccountId)))
    .reduce((sum, transaction) => sum + Number(transaction.amount), 0);
  const monthlyOutflow = monthTransactions
    .filter((transaction) => transaction.type === "WITHDRAWAL"
      || (transaction.type === "TRANSFER" && accountIds.has(transaction.sourceAccountId)))
    .reduce((sum, transaction) => sum + Number(transaction.amount), 0);
  const weeklyActivity = Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - (6 - index));
    const volume = transactions
      .filter((transaction) => new Date(transaction.createdAt).toDateString() === date.toDateString())
      .reduce((sum, transaction) => sum + Number(transaction.amount), 0);
    return { label: new Intl.DateTimeFormat("en", { weekday: "short" }).format(date), volume };
  });
  const title = visibleNavigation.find((item) => item.id === activePage)?.label ?? "Dashboard";
  const displayName = profile?.displayName || session.email.split("@")[0].replace(/[._-]+/g, " ");
  const formattedName = displayName.replace(/\b\w/g, (letter) => letter.toUpperCase());

  function navigateTo(page) {
    setActivePage(page);
    setSidebarOpen(false);
    setError("");
    setNotice("");
  }

  return (
    <div className={`banking-shell ${sidebarOpen ? "sidebar-open" : ""}`}>
      <aside className="bank-sidebar">
        <a className="bank-brand" href="#dashboard" onClick={() => navigateTo("dashboard")}>
          <span className="bank-brand-mark"><Building2 size={23} strokeWidth={2.5} /></span>
          <span><strong>LedgerLine</strong><small>Secure Financial Platform</small></span>
        </a>
        <div className="sidebar-caption">WORKSPACE</div>
        <nav className="sidebar-nav" aria-label="Main navigation">
          {visibleNavigation.map(({ id, label, icon: Icon, available }) => (
            <button key={id} type="button" className={`sidebar-link ${activePage === id ? "active" : ""}`}
              onClick={() => navigateTo(id)}>
              <Icon size={17} />
              <span>{label}</span>
              {!available && <small className="coming-soon-dot" title="Backend support not available yet">Soon</small>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="system-status"><span><ShieldCheck size={18} /></span>
            <div><strong>System status</strong><small><i /> Backend connected</small></div>
          </div>
          <button className="sidebar-user" type="button" onClick={logout}>
            <span className="user-avatar">{formattedName.slice(0, 1) || "U"}</span>
            <span className="sidebar-user-copy"><strong>{formattedName}</strong><small>{session.role.toLowerCase()}</small></span>
            <LogOut size={15} />
          </button>
        </div>
      </aside>

      <div className="bank-workspace">
        <header className="bank-header">
          <button className="mobile-menu-button" type="button" onClick={() => setSidebarOpen((open) => !open)}
            aria-label="Toggle navigation"><Menu size={20} /></button>
          <label className="global-search">
            <Search size={17} />
            <input aria-label="Search accounts and transactions" value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="Search transactions, accounts..." />
            {searchTerm && <button type="button" onClick={() => setSearchTerm("")} aria-label="Clear search">
              <X size={15} /></button>}
          </label>
          <div className="header-actions">
            <button className={`header-icon ${loading ? "spin" : ""}`} onClick={refresh}
              title="Refresh data" aria-label="Refresh data"><RefreshCw size={17} /></button>
            <span className="header-divider" />
            <button className="profile-button" onClick={logout} title="Sign out">
              <span className="user-avatar">{formattedName.slice(0, 1) || "U"}</span>
              <span><strong>{formattedName}</strong><small>{session.role.toLowerCase()}</small></span>
              <ChevronDown size={14} />
            </button>
          </div>
        </header>

        <main className="bank-main">
          {(error || notice) && <div className={`bank-feedback ${error ? "is-error" : "is-success"}`} role="status">
            <span>{error || notice}</span>
            <button type="button" onClick={() => { setError(""); setNotice(""); }} aria-label="Dismiss">×</button>
          </div>}

          <div className="page-heading">
            <div><div className="breadcrumb">LEDGERLINE <span>/</span> {title.toUpperCase()}</div>
              <h1>{activePage === "dashboard" ? `Welcome back, ${formattedName}!` : title}</h1>
              <p>{pageDescription(activePage)}</p></div>
            <div className="header-date"><CalendarDays size={15} />
              {new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" }).format(new Date())}
            </div>
          </div>

          {activePage === "dashboard" && <DashboardHome
            accounts={accounts} transactions={filteredTransactions} selectedAccount={selectedAccount}
            activeAccount={activeAccount} setActiveAccount={setActiveAccount}
            totalBalance={totalBalance} monthlyInflow={monthlyInflow} monthlyOutflow={monthlyOutflow}
            weeklyActivity={weeklyActivity} role={session.role} hideBalances={hideBalances}
            openMovement={openMovement} navigateTo={navigateTo} loading={loading} />}
          {activePage === "accounts" && <AccountsPage
            accounts={filteredAccounts} totalAccounts={accounts.length} activeAccount={activeAccount}
            setActiveAccount={setActiveAccount} hideBalances={hideBalances} role={session.role}
            loading={loading} createAccount={createAccount} />}
          {activePage === "transfer" && <TransferPage
            accounts={accounts} activeAccount={activeAccount} setActiveAccount={setActiveAccount}
            selectedAccount={selectedAccount} role={session.role} loading={loading}
            hideBalances={hideBalances} openMovement={openMovement} />}
          {(activePage === "transactions" || activePage === "ledger") && <TransactionsPage
            transactions={filteredTransactions} activeAccount={activeAccount} activePage={activePage}
            ledgerEntries={filteredLedgerEntries}
            openProof={openProof} proofLoading={proofLoading} activeProof={activeProof}
            setActiveProof={setActiveProof} />}
          {activePage === "analytics" && <AnalyticsPage
            totalBalance={totalBalance} monthlyInflow={monthlyInflow} monthlyOutflow={monthlyOutflow}
            weeklyActivity={weeklyActivity} transactions={monthTransactions} hideBalances={hideBalances} />}
          {activePage === "audit" && <AuditPage audit={audit} role={session.role} />}
          {activePage === "scheduled" && <ScheduledTransfersPage accounts={accounts} schedules={schedules}
            loading={loading} onCreate={createSchedule} onCancel={cancelSchedule} />}
          {activePage === "security" && <SecurityPage onChangePassword={changePassword}
            preferences={preferences} onSavePreferences={savePreferences} loading={loading} />}
          {activePage === "users" && session.role === "ADMIN" && <UsersPage users={users}
            loading={loading} onCreate={createUser} onUpdateRole={updateUserRole} />}
          {activePage === "settings" && <SettingsPage profile={profile} preferences={preferences}
            loading={loading} onSaveProfile={saveProfile} onSavePreferences={savePreferences} />}
          {!visibleNavigation.find((item) => item.id === activePage)?.available
            && <ComingSoonPage page={activePage} />}

          <footer className="bank-footer">
            <span>LedgerLine <i>•</i> Local ledger demo</span>
            <span><Shield size={13} /> No external payment network</span>
          </footer>
        </main>
      </div>

      {sidebarOpen && <button className="sidebar-scrim" onClick={() => setSidebarOpen(false)}
        aria-label="Close navigation" />}

      {movementOpen && <div className="movement-overlay" role="presentation" onMouseDown={(event) => {
        if (event.target === event.currentTarget) setMovementOpen(false);
      }}>
        <section className="movement-dialog" role="dialog" aria-modal="true" aria-labelledby="movement-title">
          <div className="dialog-heading">
            <div><span className="section-kicker">SECURE PAYMENT</span>
              <h2 id="movement-title">{movementKind === "transfer" ? "Transfer money"
                : movementKind === "deposit" ? "Deposit money" : "Withdraw money"}</h2></div>
            <button type="button" className="icon-button" onClick={() => setMovementOpen(false)}
              aria-label="Close movement form"><X size={18} /></button>
          </div>
          <p className="dialog-account">From {selectedAccount?.accountNumber} · {selectedAccount?.currency}</p>
          <form className="movement-form" onSubmit={submitMovement}>
            <input type="hidden" name="kind" value={movementKind} />
            {movementKind === "transfer" && <>
              <label htmlFor="destinationAccountNumber">To account</label>
              <input id="destinationAccountNumber" name="destinationAccountNumber" list="owned-account-options"
                placeholder="LLXXXXXXXXXXXX"
                autoComplete="off" required />
              <datalist id="owned-account-options">{accounts.filter((account) => account.id !== activeAccount)
                .map((account) => <option key={account.id} value={account.accountNumber}>
                  {account.currency} · {money(account.balance, account.currency)}
                </option>)}</datalist>
            </>}
            <label htmlFor="amount">Amount <span className="label-hint">{selectedAccount?.currency}</span></label>
            <div className="amount-input"><span>$</span><input id="amount" name="amount" type="number"
              min="0.01" max="99999999999999999.99" step="0.01" placeholder="0.00" required /></div>
            <label htmlFor="description">Purpose <span className="label-hint">OPTIONAL</span></label>
            <input id="description" name="description" maxLength="140" placeholder="e.g. Rent, shopping" />
            {movementKind !== "transfer" && <p className="demo-notice">
              {movementKind === "deposit" ? "This demo deposit uses a simulated clearing account; no real money is collected."
                : "This demo withdrawal uses a simulated clearing account; no real payment is sent."}
            </p>}
            <button className="primary-button" type="submit" disabled={loading}>
              <ArrowRight size={16} /> {loading ? "Processing securely…" : "Confirm transaction"}
            </button>
            <p className="form-footnote"><Shield size={13} /> Protected by a unique idempotency key.</p>
          </form>
        </section>
      </div>}
      <TransferSuccessDialog receipt={receipt} onClose={closeReceipt} />
    </div>
  );
}

function pageDescription(page) {
  return ({
    dashboard: "Here's your financial overview for today.",
    accounts: "Manage your bank accounts and review available balances.",
    transfer: "Send money securely between LedgerLine accounts.",
    transactions: "Track your recent account activity.",
    ledger: "Review transaction records and inspect double-entry proofs.",
    analytics: "A summary calculated from your recorded transactions.",
    audit: "Review account and transaction events recorded by the backend.",
    scheduled: "Schedule one-time transfers between accounts in the local ledger.",
    security: "Change your password and manage saved security preferences.",
    users: "Manage local LedgerLine users and account roles.",
    settings: "Manage your profile and saved preferences.",
  })[page] || "";
}

function DashboardHome({
  accounts, transactions, selectedAccount, activeAccount, setActiveAccount,
  totalBalance, monthlyInflow, monthlyOutflow, weeklyActivity, role,
  hideBalances, openMovement, navigateTo, loading,
}) {
  const maxActivity = Math.max(...weeklyActivity.map((item) => item.volume), 1);
  return <>
    <section className="summary-grid">
      <SummaryCard icon={Wallet} label="Total balance" value={hideBalances ? "••••••" : money(totalBalance)}
        detail={`${accounts.length} active account${accounts.length === 1 ? "" : "s"}`} color="blue" />
      <SummaryCard icon={TrendingUp} label="This month inflow" value={money(monthlyInflow)}
        detail="Deposits and transfers received" color="green" />
      <SummaryCard icon={TrendingDown} label="This month outflow" value={money(monthlyOutflow)}
        detail="Withdrawals and transfers sent" color="purple" />
      <SummaryCard icon={Activity} label="Transactions" value={transactions.length}
        detail="Latest records available" color="cyan" />
    </section>
    {!loading && role === "CUSTOMER" && accounts.length === 0 && <section className="bank-card empty-dashboard-setup">
      <div><h2>No account is linked to this customer yet</h2>
        <p>Open a USD account to see its balance here. Your account and transaction data will load from the backend.</p></div>
      <button className="primary-button" type="button" onClick={() => navigateTo("accounts")}>
        <Plus size={16} /> Open account
      </button>
    </section>}

    <section className="dashboard-columns">
      <div className="dashboard-primary">
        <section className="bank-card chart-card">
          <div className="card-heading"><div><h2>Transaction activity</h2>
            <p>Daily transaction volume over the last 7 days</p></div>
            <span className="period-pill"><CalendarDays size={14} /> 7 days</span></div>
          <div className="activity-chart" role="img" aria-label="Daily transaction volume for the last seven days">
            <div className="chart-y-labels"><span>{money(maxActivity)}</span><span>{money(maxActivity / 2)}</span><span>$0</span></div>
            <div className="chart-bars">{weeklyActivity.map((item) => (
              <div className="chart-bar-column" key={item.label}>
                <span className="chart-bar-value">{item.volume ? money(item.volume) : ""}</span>
                <div className="chart-bar-track"><span style={{ height: `${Math.max(item.volume / maxActivity * 100, item.volume ? 5 : 0)}%` }} /></div>
                <small>{item.label}</small>
              </div>
            ))}</div>
          </div>
        </section>

        <section className="bank-card recent-transactions-card">
          <div className="card-heading"><div><h2>Recent transactions</h2>
            <p>Your latest recorded account activity</p></div>
            <button className="inline-link" onClick={() => navigateTo("transactions")}>View all <ArrowRight size={14} /></button>
          </div>
          <TransactionRows transactions={transactions.slice(0, 5)} activeAccount={activeAccount} />
        </section>
      </div>

      <div className="dashboard-secondary">
        <section className="bank-card quick-actions-card">
          <div className="card-heading"><div><h2>Quick actions</h2><p>Move money securely</p></div></div>
          {role === "CUSTOMER" ? <div className="quick-action-list">
            <QuickAction icon={ArrowLeftRight} title="Transfer money" subtitle="Send money securely"
              color="blue" disabled={!selectedAccount || loading} onClick={() => openMovement("transfer")} />
            <QuickAction icon={ArrowDownLeft} title="Deposit money" subtitle="Add funds to account"
              color="green" disabled={!selectedAccount || loading} onClick={() => openMovement("deposit")} />
            <QuickAction icon={ArrowUpRight} title="Withdraw money" subtitle="Withdraw from account"
              color="orange" disabled={!selectedAccount || loading} onClick={() => openMovement("withdraw")} />
          </div> : <div className="readonly-note"><Shield size={17} /><span>
            {role === "ADMIN" ? "Admin access can create accounts and review audit logs." : "Auditor access is read-only."}
          </span></div>}
        </section>

        <section className="bank-card account-overview-card">
          <div className="card-heading"><div><h2>Account overview</h2><p>Selected account</p></div>
            <button className="inline-link" onClick={() => navigateTo("accounts")}>Details <ArrowRight size={14} /></button>
          </div>
          {selectedAccount ? <div className="bank-card-visual">
            <div className="card-chip" />
            <span className="bank-card-account">{selectedAccount.accountNumber}</span>
            <strong>{hideBalances ? "••••••" : money(selectedAccount.balance, selectedAccount.currency)}</strong>
            <small>Available balance · {selectedAccount.currency}</small>
            <span className="card-owner">{selectedAccount.currency} account</span>
          </div> : <div className="empty-account-note"><Wallet size={22} />
            <span>No account yet. Open an account to see its overview.</span></div>}
        </section>
      </div>
    </section>
  </>;
}

function SummaryCard({ icon: Icon, label, value, detail, color }) {
  return <article className={`summary-card summary-${color}`}>
    <span className="summary-icon"><Icon size={21} /></span>
    <div><small>{label}</small><strong>{value}</strong><span>{detail}</span></div>
  </article>;
}

function QuickAction({ icon: Icon, title, subtitle, color, onClick, disabled }) {
  return <button className="quick-action" type="button" onClick={onClick} disabled={disabled}>
    <span className={`quick-action-icon ${color}`}><Icon size={19} /></span>
    <span><strong>{title}</strong><small>{subtitle}</small></span>
    <ArrowRight size={16} />
  </button>;
}

function AccountsPage({ accounts, totalAccounts, activeAccount, setActiveAccount, hideBalances, role, loading, createAccount }) {
  return <div className="page-stack">
    <section className="account-cards-grid">
      {accounts.map((account, index) => <button key={account.id}
        className={`bank-account-card ${activeAccount === account.id ? "selected" : ""}`}
        onClick={() => setActiveAccount(account.id)}>
        <span className="account-card-top"><span className="account-card-icon"><Building2 size={19} /></span>
          <span>{activeAccount === account.id ? "Selected" : `Account ${index + 1}`}</span></span>
        <strong>{hideBalances ? "••••••" : money(account.balance, account.currency)}</strong>
        <small>{account.currency} · {account.accountNumber}</small>
        <span className="account-card-link">View account details <ArrowRight size={14} /></span>
      </button>)}
      {!accounts.length && <EmptyPanel icon={Wallet} title="No accounts yet"
        message="Create a customer account to see your balance here." />}
    </section>
    {role === "CUSTOMER" && <form className="bank-card create-account-panel" onSubmit={createAccount}>
      <span className="summary-icon summary-icon-blue"><Plus size={20} /></span>
      <div><h2>Open a new account</h2><p>New accounts are created in USD.</p></div>
      <button className="primary-button" type="submit" disabled={loading}><Plus size={16} /> Open account</button>
    </form>}
    {role === "ADMIN" && <form className="bank-card create-account-panel admin-create-panel" onSubmit={createAccount}>
      <div><h2>Create a customer account</h2><p>Enter the email of an existing LedgerLine user.</p></div>
      <input type="email" name="email" placeholder="customer@example.com" required />
      <button className="primary-button" type="submit" disabled={loading}><Plus size={16} /> Create account</button>
    </form>}
    <section className="bank-card account-details-card">
      <div className="card-heading"><div><h2>Account details</h2><p>Account information from the backend</p></div></div>
      {accounts.find((account) => account.id === activeAccount) ? (() => {
        const account = accounts.find((item) => item.id === activeAccount);
        return <dl className="account-details-grid">
          <div><dt>Account number</dt><dd>{account.accountNumber}</dd></div>
          <div><dt>Account type</dt><dd>{account.currency} account</dd></div>
          <div><dt>Available balance</dt><dd>{hideBalances ? "••••••" : money(account.balance, account.currency)}</dd></div>
          <div><dt>Opened</dt><dd>{shortDate(account.createdAt)}</dd></div>
          <div><dt>Accounts available</dt><dd>{totalAccounts}</dd></div>
        </dl>;
      })() : <p className="muted-copy">Select an account to view its details.</p>}
    </section>
  </div>;
}

function TransferPage({ accounts, activeAccount, setActiveAccount, selectedAccount, role, loading, hideBalances, openMovement }) {
  if (role !== "CUSTOMER") return <ReadOnlyPanel role={role} />;
  if (!selectedAccount) return <EmptyPanel icon={Wallet} title="Select an account"
    message="Open or select an account before creating a transaction." />;
  return <div className="transfer-layout">
    <section className="bank-card transfer-card">
      <div className="card-heading"><div><h2>Money transfer</h2>
        <p>Transfer, deposit, or withdraw from your selected account.</p></div><ShieldCheck size={21} className="secure-icon" /></div>
      <div className="transfer-form-wrap">
        <label className="field-label" htmlFor="transfer-source-account">From account</label>
        <select id="transfer-source-account" className="transfer-source-select" value={activeAccount}
          onChange={(event) => setActiveAccount(event.target.value)}>
          {accounts.map((account) => <option key={account.id} value={account.id}>
            {account.accountNumber} · {hideBalances ? "Balance hidden" : money(account.balance, account.currency)}
          </option>)}
        </select>
        <span className="field-label">Choose an action</span>
        <button className="primary-button" onClick={() => openMovement("transfer")} disabled={loading}>
          <ArrowLeftRight size={16} /> Start transfer
        </button>
        <div className="transfer-alternatives">
          <button type="button" onClick={() => openMovement("deposit")} disabled={loading}>
            <ArrowDownLeft size={17} /> Deposit <span>Demo only</span>
          </button>
          <button type="button" onClick={() => openMovement("withdraw")} disabled={loading}>
            <ArrowUpRight size={17} /> Withdraw
          </button>
        </div>
      </div>
      <div className="transfer-safety"><ShieldCheck size={17} /><span>
        Transfers are validated by the backend, protected against duplicate submissions, and written to the ledger.
      </span></div>
    </section>
    <section className="bank-card transfer-side-card">
      <span className="transfer-art"><ArrowLeftRight size={40} /></span>
      <h2>Secure account-to-account transfers</h2>
      <p>Enter the destination account number and amount. The backend checks ownership and available funds.</p>
      <div className="transfer-account-summary"><span>Sending from</span>
        <strong>{selectedAccount.accountNumber}</strong>
        <small>{hideBalances ? "Balance hidden" : `${money(selectedAccount.balance, selectedAccount.currency)} available`}</small>
      </div>
    </section>
  </div>;
}

function TransactionsPage({ transactions, ledgerEntries, activeAccount, activePage, openProof, proofLoading, activeProof, setActiveProof }) {
  const records = activePage === "ledger" ? ledgerEntries : transactions;
  return <section className="bank-card transactions-page-card">
    <div className="card-heading"><div><h2>{activePage === "ledger" ? "Double-entry ledger" : "Transaction history"}</h2>
      <p>{activePage === "ledger" ? "Open a proof to inspect the paired debit and credit entries." : "All recent activity available for your accounts."}</p>
    </div><div className="transactions-heading-actions">
      <span className="transaction-count">{records.length} records</span>
      <button className="secondary-button" type="button"
        onClick={() => downloadCsv(activePage === "ledger" ? "ledgerline-ledger.csv" : "ledgerline-transactions.csv",
          activePage === "ledger"
            ? ["Reference", "Date", "Account", "Entry Type", "Amount", "Balance After"]
            : ["Reference", "Date", "Type", "Description", "Amount", "Source Account", "Destination Account"],
          activePage === "ledger"
            ? ledgerEntries.map((item) => [item.reference, item.createdAt, item.accountRef, item.entryType,
              item.amount, item.accountBalanceAfter])
            : transactions.map((item) => [item.reference, item.createdAt, item.type, item.description,
              item.amount, item.sourceAccountId, item.destinationAccountId]))}>
        <Download size={14} /> Export CSV
      </button>
    </div></div>
    {records.length ? activePage === "ledger"
      ? <LedgerTable entries={ledgerEntries} openProof={openProof} proofLoading={proofLoading} />
      : <TransactionTable transactions={transactions} activeAccount={activeAccount}
        openProof={openProof} proofLoading={proofLoading} />
      : <EmptyPanel icon={Sparkles}
      title="No transactions found" message="Transactions will appear here after you make a deposit, withdrawal, or transfer." />}
    {activeProof && <ProofPanel proof={activeProof} setActiveProof={setActiveProof} />}
  </section>;
}

function LedgerTable({ entries, openProof, proofLoading }) {
  return <div className="transaction-table-wrap"><table className="bank-table">
    <thead><tr><th>Date</th><th>Transaction ref</th><th>Account</th><th>Entry type</th><th>Amount</th><th>Balance after</th><th>Proof</th></tr></thead>
    <tbody>{entries.map((entry, index) => <tr key={`${entry.reference}-${entry.accountRef}-${index}`}>
      <td>{shortDate(entry.createdAt)}</td>
      <td><small className="table-reference">{entry.reference}</small></td>
      <td><code>{entry.accountRef}</code></td>
      <td><span className={`proof-entry-type ${entry.entryType.toLowerCase()}`}>{entry.entryType}</span></td>
      <td>{money(entry.amount)}</td>
      <td>{entry.accountBalanceAfter == null ? "—" : money(entry.accountBalanceAfter)}</td>
      <td><button className="table-proof-button" onClick={() => openProof(entry.reference)}
        disabled={proofLoading}><BadgeCheck size={14} /> View</button></td>
    </tr>)}</tbody>
  </table></div>;
}

function TransactionRows({ transactions, activeAccount }) {
  if (!transactions.length) {
    return <EmptyPanel icon={ReceiptText} title="No transactions yet"
      message="Your account activity will appear here after a deposit, withdrawal, or transfer." />;
  }

  return <div className="transaction-table-wrap"><table className="bank-table">
    <thead><tr><th>Date</th><th>Description</th><th>Type</th><th>Amount</th><th>Status</th></tr></thead>
    <tbody>{transactions.map((transaction) => {
      const incoming = transaction.destinationAccountId === activeAccount;
      const outgoing = transaction.sourceAccountId === activeAccount;
      const positive = incoming && (transaction.type === "DEPOSIT" || transaction.type === "TRANSFER");
      const negative = outgoing && (transaction.type === "WITHDRAWAL" || transaction.type === "TRANSFER");
      return <tr key={transaction.id}>
        <td>{shortDate(transaction.createdAt)}</td>
        <td><strong>{transaction.description || transaction.type.toLowerCase()}</strong>
          <small className="table-reference">{transaction.reference}</small></td>
        <td><span className={`transaction-type type-${transaction.type.toLowerCase()}`}>{transaction.type.toLowerCase()}</span></td>
        <td className={positive ? "amount-positive" : negative ? "amount-negative" : ""}>
          {positive ? "+" : negative ? "−" : ""}{money(transaction.amount)}</td>
        <td><span className="status-pill">Posted</span></td>
      </tr>;
    })}</tbody>
  </table></div>;
}

function TransactionTable({ transactions, activeAccount, openProof, proofLoading }) {
  return <div className="transaction-table-wrap"><table className="bank-table">
    <thead><tr><th>Date</th><th>Description</th><th>Type</th><th>Amount</th><th>Status</th><th>Proof</th></tr></thead>
    <tbody>{transactions.map((transaction) => {
      const incoming = transaction.destinationAccountId === activeAccount;
      const outgoing = transaction.sourceAccountId === activeAccount;
      const positive = incoming && (transaction.type === "DEPOSIT" || transaction.type === "TRANSFER");
      const negative = outgoing && (transaction.type === "WITHDRAWAL" || transaction.type === "TRANSFER");
      return <tr key={transaction.id}>
        <td>{shortDate(transaction.createdAt)}</td>
        <td><strong>{transaction.description || transaction.type.toLowerCase()}</strong>
          <small className="table-reference">{transaction.reference}</small></td>
        <td><span className={`transaction-type type-${transaction.type.toLowerCase()}`}>{transaction.type.toLowerCase()}</span></td>
        <td className={positive ? "amount-positive" : negative ? "amount-negative" : ""}>
          {positive ? "+" : negative ? "−" : ""}{money(transaction.amount)}</td>
        <td><span className="status-pill">Posted</span></td>
        <td><button className="table-proof-button" onClick={() => openProof(transaction.reference)}
          disabled={proofLoading}><BadgeCheck size={14} /> View</button></td>
      </tr>;
    })}</tbody>
  </table></div>;
}

function ProofPanel({ proof, setActiveProof }) {
  return <div className="proof-slip">
    <div className="proof-slip-heading"><div><span className="section-kicker">LEDGER PROOF</span>
      <strong>{proof.reference}</strong></div><span className={`proof-status ${proof.balanced ? "" : "proof-unbalanced"}`}>
      <BadgeCheck size={14} /> {proof.balanced ? "BALANCED" : "REVIEW REQUIRED"}</span>
      <button className="proof-close" onClick={() => setActiveProof(null)} aria-label="Close proof">×</button>
    </div>
    <div className="proof-totals"><span>Debits <strong>{money(proof.totalDebits)}</strong></span>
      <span>Credits <strong>{money(proof.totalCredits)}</strong></span></div>
    <div className="proof-entries">{proof.entries.map((entry, index) => <div key={`${entry.accountRef}-${index}`}>
      <span className={`proof-entry-type ${entry.entryType.toLowerCase()}`}>{entry.entryType}</span>
      <code>{entry.accountRef}</code><strong>{money(entry.amount)}</strong>
      <small>{entry.accountBalanceAfter == null ? "External clearing" : `Balance after ${money(entry.accountBalanceAfter)}`}</small>
    </div>)}</div>
    <p className="proof-caption"><Shield size={13} /> Debit and credit totals are reported by the backend.</p>
  </div>;
}

function AnalyticsPage({ totalBalance, monthlyInflow, monthlyOutflow, weeklyActivity, transactions, hideBalances }) {
  const maxActivity = Math.max(...weeklyActivity.map((item) => item.volume), 1);
  return <div className="page-stack">
    <section className="summary-grid analytics-summary">
      <SummaryCard icon={Wallet} label="Current balance" value={hideBalances ? "••••••" : money(totalBalance)}
        detail="Across your available accounts" color="blue" />
      <SummaryCard icon={TrendingUp} label="Inflow this month" value={money(monthlyInflow)}
        detail="Deposits and incoming transfers" color="green" />
      <SummaryCard icon={TrendingDown} label="Outflow this month" value={money(monthlyOutflow)}
        detail="Withdrawals and outgoing transfers" color="purple" />
      <SummaryCard icon={Activity} label="This month" value={transactions.length}
        detail="Recorded transactions" color="cyan" />
    </section>
    <section className="bank-card analytics-chart-card">
      <div className="card-heading"><div><h2>Daily transaction volume</h2>
        <p>Calculated from transaction history available to this account.</p></div></div>
      <div className="analytics-bars">{weeklyActivity.map((item) => (
        <div className="analytics-bar-column" key={item.label}><strong>{item.volume ? money(item.volume) : "$0"}</strong>
          <div><span style={{ height: `${Math.max(item.volume / maxActivity * 100, item.volume ? 4 : 0)}%` }} /></div>
          <small>{item.label}</small>
        </div>
      ))}</div>
    </section>
    <p className="data-limit-note"><CircleHelp size={15} /> Analytics are limited to the latest transaction history returned by the backend.</p>
  </div>;
}

function AuditPage({ audit, role }) {
  if (role === "CUSTOMER") return <ReadOnlyPanel role={role} />;
  return <section className="bank-card transactions-page-card">
    <div className="card-heading"><div><h2>Audit logs</h2><p>Recent events returned by the audit API.</p></div>
      <div className="transactions-heading-actions"><span className="transaction-count">{audit.length} events</span>
        <button className="secondary-button" type="button"
          onClick={() => downloadCsv("ledgerline-audit.csv",
            ["Date", "Action", "Resource Type", "Resource ID", "Details"],
            audit.map((item) => [item.createdAt, item.action, item.resourceType, item.resourceId, item.details]))}>
          <Download size={14} /> Export CSV
        </button>
      </div></div>
    {audit.length ? <div className="audit-table-wrap"><table className="bank-table">
      <thead><tr><th>Date & time</th><th>Action</th><th>Details</th><th>Resource ID</th></tr></thead>
      <tbody>{audit.map((entry) => <tr key={entry.id}><td>{shortDate(entry.createdAt)}</td>
        <td><span className="transaction-type type-transfer">{entry.action.toLowerCase()}</span></td>
        <td>{entry.details}</td><td><code>{entry.resourceId}</code></td></tr>)}</tbody>
    </table></div> : <EmptyPanel icon={FileText} title="No audit events"
      message="Events will appear after accounts or transactions are created." />}
  </section>;
}

function ComingSoonPage({ page }) {
  const item = navigation.find((entry) => entry.id === page);
  const Icon = item?.icon || Settings;
  return <section className="bank-card coming-soon-panel">
    <span className="coming-soon-icon"><Icon size={28} /></span>
    <span className="section-kicker">NOT CONNECTED TO THE BACKEND</span>
    <h2>{item?.label || "Feature"} is not available yet</h2>
    <p>This prototype does not have a backend API for this feature, so no account settings or payment data are simulated here.</p>
  </section>;
}

function ReadOnlyPanel({ role }) {
  return <div className="bank-card read-only-panel"><Shield size={23} />
    <div><h2>Read-only access</h2>
      <p>{role.toLowerCase()} accounts cannot initiate money movements. Sign in as a customer to transfer funds.</p></div>
  </div>;
}

function EmptyPanel({ icon: Icon, title, message }) {
  return <div className="empty-panel"><Icon size={24} /><strong>{title}</strong><p>{message}</p></div>;
}

function downloadCsv(filename, headers, rows) {
  const escape = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;
  const content = [headers, ...rows].map((row) => row.map(escape).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([content], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
