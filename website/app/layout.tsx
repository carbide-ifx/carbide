import type { Metadata } from "next";
import { headers } from "next/headers";
import "./globals.css";
import { siteConfig } from "./site-config";

export async function generateMetadata(): Promise<Metadata> {
  const requestHeaders = await headers();
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host") ?? "localhost:3000";
  const protocol = requestHeaders.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");
  const origin = `${protocol}://${host}`;
  const title = siteConfig.productName ?? siteConfig.title;
  const image = `${origin}/og.png`;

  return {
    title,
    description: siteConfig.description,
    openGraph: {
      title,
      description: siteConfig.description,
      images: [{ url: image, width: 1731, height: 909, alt: "You write the business code" }],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description: siteConfig.description,
      images: [image],
    },
  };
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
