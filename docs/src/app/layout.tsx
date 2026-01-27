import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "mi. Browser | Minimalist Mobile Web",
  description: "A hyper-lightweight, distraction-free mobile browser designed for the modern web.",
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