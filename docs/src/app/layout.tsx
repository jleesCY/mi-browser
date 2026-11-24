import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "mi. Browser | A Simple, Private Browser",
  description: "A simple, private Android browser built around one Pill: swipe to search and switch tabs, with ads and trackers blocked, price following and sites as apps.",
  // the app icon (public/icon.png), under the site's base path on GitHub Pages
  icons: { icon: `${process.env.NODE_ENV === "production" ? `/${process.env.NEXT_PUBLIC_REPO_NAME}` : ""}/icon.png` },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark" style={{ colorScheme: 'dark' }}>
      <body className="antialiased bg-[#050505] text-[#ededed]">
        {children}
      </body>
    </html>
  );
}