import type { Config } from "tailwindcss";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Material 3 colour roles from the generated theme (src/app/theme.css, made by scripts/theme.mjs from the app's seed
// colour): each --md-<role> becomes a Tailwind colour (bg-surface, text-on-surface-variant, bg-primary-container…), with
// opacity modifiers (bg-primary/10). They follow the visitor's light or dark setting by themselves.
const roles = [...readFileSync(join(__dirname, "src/app/theme.css"), "utf8").matchAll(/--md-([\w-]+):/g)].map((m) => m[1]);
const colors = Object.fromEntries([...new Set(roles)].map((r) => [r, `rgb(var(--md-${r}) / <alpha-value>)`]));

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      screens: { xs: "400px" },
      colors,
      fontFamily: { sans: ["var(--font-sans)", "system-ui", "sans-serif"] },
      borderRadius: { "3xl": "24px", "4xl": "28px" },
    },
  },
  plugins: [],
};
export default config;
