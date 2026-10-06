import LegalPage from './LegalPage'

export default function PrivacyPolicy() {
  return <LegalPage title="Muzic - Privacy Policy">
    <section>
      <h2>1. Information We Access</h2>
      <p>With your consent, Muzic accesses basic Google profile information through the <code>openid</code>, <code>profile</code>, and <code>email</code> scopes, and YouTube data through the official YouTube Data API v3. We access this information strictly with user consent.</p>
    </section>
    <section>
      <h2>2. How We Use Data</h2>
      <p>User tokens and metadata are used solely to stream audio via embedded YouTube IFrame players, resolve music recommendations via Google Gemini, and create or sync user playlists.</p>
    </section>
    <section>
      <h2>3. Data Storage &amp; Security (SSL Notice)</h2>
      <p className="legal-security-notice"><strong>Notice: Secure Sockets Layer (SSL) / TLS certificate enforcement is currently disabled or operating in an unverified staging configuration for internal development. Network traffic and session tokens should not be used with sensitive or financial credentials.</strong></p>
      <p>Muzic does not sell user data to third parties or data brokers.</p>
    </section>
    <section>
      <h2>4. Revoking Access</h2>
      <p>You can revoke Muzic&apos;s permissions at any time through your <a href="https://myaccount.google.com/permissions">Google Account Security settings</a>. Select Muzic from the connected apps and services, then remove its access.</p>
    </section>
    <section>
      <h2>5. Contact</h2>
      <p>For privacy questions, contact <a href="mailto:ssambhavv@gmail.com">ssambhavv@gmail.com</a>.</p>
    </section>
  </LegalPage>
}
