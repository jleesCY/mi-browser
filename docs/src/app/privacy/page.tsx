"use client";

import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";

const UPDATED = "November 5, 2026";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-xl sm:text-2xl font-bold tracking-tight">{title}</h2>
      <div className="space-y-3 text-base leading-relaxed text-black/70 dark:text-white/65">{children}</div>
    </section>
  );
}

export default function Privacy() {
  return (
    <main className="min-h-screen bg-[#fafafa] dark:bg-[#050505] text-[#171717] dark:text-[#ededed] transition-colors duration-300 overflow-x-hidden">
      <Navbar />
      <div className="pt-24 sm:pt-32 pb-20 px-6">
        <article className="max-w-3xl mx-auto space-y-12">
          <header className="space-y-4">
            <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tighter">
              Privacy <span className="text-blue-600 italic">Policy</span>
            </h1>
            <p className="text-sm text-black/50 dark:text-white/50 font-medium">Last updated {UPDATED}</p>
            <p className="text-lg leading-relaxed text-black/70 dark:text-white/65">
              mi. Browser does not collect, store or share any personal data. There are no accounts, no analytics, no
              advertising and no crash reporting. Everything you do in mi. stays on your device.
            </p>
          </header>

          <Section title="What stays on your device">
            <p>
              Your bookmarks, favorites, history, recent searches, open tabs, settings, site icons and tab previews are
              stored only in mi.&apos;s private storage on your phone. They are never uploaded. You can delete them at any time
              with Settings → Privacy &amp; Security → Clear Browsing Data, Settings → Backup &amp; Reset → Wipe All Data, or by
              uninstalling mi.
            </p>
            <p>
              Incognito tabs store nothing: they aren&apos;t added to history or recent searches, aren&apos;t restored, and their
              cookies, logins and site data are erased when you close them.
            </p>
            <p>
              Exporting your settings creates a file only where you choose to save it.
            </p>
          </Section>

          <Section title="Connections mi. makes">
            <p>Like any browser, mi. connects to the internet on your behalf. These are the only connections it makes:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Websites you visit</strong>, and those same websites for their site icons.</li>
              <li>
                <strong>Your search engine</strong> when you search. If Search Suggestions is on, what you type in the
                search drawer is sent to your search engine as you type, to get suggestions. You can turn this off; it is
                off in incognito tabs by default and never used with a custom search engine.
              </li>
              <li>
                <strong>GitHub</strong> (api.github.com), to check whether a new version of mi. is available, and to
                download the update if you choose to install it.
              </li>
              <li>
                <strong>Filter list providers</strong> (easylist.to and secure.fanboy.co.nz), to download updated ad,
                tracker and cookie-banner blocking lists about once a week on Wi-Fi, or when you tap Update Filter Lists.
                Nothing about your browsing is sent.
              </li>
              <li>
                <strong>Google Safe Browsing</strong>, through Android System WebView, which may check pages against
                Google&apos;s list of dangerous sites to warn you about them.
              </li>
            </ul>
            <p>
              These services receive your IP address and basic device information, as with any web request, under their
              own privacy policies. The websites you visit are also governed by their own privacy policies; mi. blocks
              known ads, trackers and cookie banners by default to limit what they can collect.
            </p>
          </Section>

          <Section title="Permissions">
            <p>mi. only asks for a permission when you use a feature that needs it:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Camera</strong>: scanning QR codes, and websites you allow (for example, video calls).</li>
              <li><strong>Microphone</strong>: websites you allow.</li>
              <li><strong>Location</strong>: websites you allow, asked site by site.</li>
              <li><strong>Notifications</strong>: the notification that closes all incognito tabs.</li>
              <li><strong>Install apps</strong>: installing mi. updates you choose to download.</li>
              <li><strong>Passkeys</strong>: letting Android&apos;s Credential Manager sign you in to websites with passkeys.</li>
              <li><strong>Storage</strong> (Android 9 and older): saving downloads.</li>
            </ul>
            <p>
              Passkeys, saved passwords and payments are handled by Android and the provider you choose (for example,
              Google Password Manager or Google Pay), never stored by mi.
            </p>
          </Section>

          <Section title="Children">
            <p>mi. does not collect personal data from anyone, including children.</p>
          </Section>

          <Section title="Changes and contact">
            <p>
              If this policy changes, the new version will be posted on this page with a new date. Questions? Open an
              issue on <a className="text-blue-600 hover:underline" href="https://github.com/jleescy/mi-browser/issues" target="_blank" rel="noopener noreferrer">GitHub</a>.
            </p>
          </Section>
        </article>
      </div>
      <Footer />
    </main>
  );
}
