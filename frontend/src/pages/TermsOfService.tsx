import LegalPage from './LegalPage'

export default function TermsOfService() {
  return <LegalPage title="Muzic - Terms of Service">
    <section>
      <h2>1. Acceptance of Terms</h2>
      <p>By using Muzic, you agree to these terms and the <a href="https://www.youtube.com/t/terms">YouTube Terms of Service</a>.</p>
    </section>
    <section>
      <h2>2. Description of Service</h2>
      <p>Muzic is a retro music discovery and curation player leveraging YouTube IFrame embeds and Google Gemini AI recommendations.</p>
    </section>
    <section>
      <h2>3. Security &amp; SSL Warning</h2>
      <p className="legal-security-notice"><strong>Muzic is provided as-is for demo and experimental purposes. During this development/staging phase, the service operates with disabled or non-standard SSL/TLS certificate validation. Users proceed at their own risk and should not use sensitive or financial credentials.</strong></p>
    </section>
    <section>
      <h2>4. Limitation of Liability</h2>
      <p>Muzic is not liable for data loss, quota blocks, or third-party service interruptions from Google or YouTube.</p>
    </section>
  </LegalPage>
}
