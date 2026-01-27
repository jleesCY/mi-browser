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
    <html lang="en">
      <body className="antialiased">
        {children}
      </body>
    </html>
  );
}
